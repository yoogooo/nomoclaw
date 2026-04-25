#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use anyhow::{anyhow, Context, Result};
use chrono::Local;
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::env;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::net::TcpListener;
#[cfg(unix)]
use std::os::unix::process::CommandExt;
#[cfg(windows)]
use std::os::windows::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tauri::image::Image;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::webview::PageLoadEvent;
use tauri::{AppHandle, Emitter, Manager, RunEvent, Runtime, State, WindowEvent};
use tauri_plugin_dialog::{DialogExt, MessageDialogButtons, MessageDialogKind};
use tauri_plugin_updater::{Update, UpdaterExt};

// ===== UI / tray constants =====
const WINDOW_LABEL: &str = "main";
const TRAY_ICON_ID: &str = "main-tray";
const TRAY_MENU_SHOW: &str = "show_main";
const TRAY_MENU_RESTART_APP: &str = "restart_app";
const TRAY_MENU_QUIT: &str = "quit_app";
#[cfg(target_os = "macos")]
const MACOS_TRAY_TEMPLATE_ICON_FILE: &str = "tray-macos-template.png";
#[cfg(target_os = "macos")]
const FALLBACK_TRAY_TEMPLATE_ICON: Image<'_> = tauri::include_image!("./icons/tray-macos-template.png");
#[cfg(not(target_os = "macos"))]
const FALLBACK_TRAY_ICON: Image<'_> = tauri::include_image!("./icons/icon.png");
const DESKTOP_I18N_FILE: &str = "i18n/desktop.json";
const DESKTOP_I18N_FALLBACK_JSON: &str = include_str!("../resources/i18n/desktop.json");

// ===== runtime / persistence constants =====
const INSTANCE_LOCK_FILE: &str = "app.lock";
#[cfg(unix)]
const BACKEND_PGID_FILE: &str = "backend.pgid";
const BOOTSTRAP_PREFS_FILE: &str = "bootstrap_prefs.json";
const BOOTSTRAP_THEME_STORAGE_KEY: &str = "ui:theme-mode";
const BOOTSTRAP_LOCALE_STORAGE_KEY: &str = "ui:locale";
const DEFAULT_THEME_MODE: &str = "dark";
const DEFAULT_LOCALE: &str = "zh-CN";
const UPDATER_EVENT_STATE: &str = "updater://state";
const UPDATE_CHECK_INTERVAL_SECONDS: u64 = 6 * 60 * 60;

// Process-level guards used by multiple async/workdog paths.
static EXITING: AtomicBool = AtomicBool::new(false);
static BACKEND_STARTING: AtomicBool = AtomicBool::new(false);
static BACKEND_RESTARTING: AtomicBool = AtomicBool::new(false);
static BACKEND_WATCH_FAIL_STREAK: AtomicU32 = AtomicU32::new(0);
static QUIT_CONFIRMING: AtomicBool = AtomicBool::new(false);
static UPDATER_CHECKING: AtomicBool = AtomicBool::new(false);
static UPDATER_INSTALLING: AtomicBool = AtomicBool::new(false);
static SHOW_MAIN_ON_NEXT_FINISHED_LOAD: AtomicBool = AtomicBool::new(false);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum BackendMode {
    Attach,
    Spawn,
}

#[derive(Debug)]
struct BackendRuntime {
    mode: BackendMode,
    port: u16,
    child: Option<Child>,
    #[cfg(unix)]
    process_group_id: Option<i32>,
}

#[derive(Clone)]
struct DesktopState {
    runtime: Arc<Mutex<Option<BackendRuntime>>>,
}

impl Default for DesktopState {
    fn default() -> Self {
        Self {
            runtime: Arc::new(Mutex::new(None)),
        }
    }
}

#[derive(Clone)]
struct UpdaterState {
    runtime: Arc<Mutex<UpdaterRuntimeState>>,
}

impl Default for UpdaterState {
    fn default() -> Self {
        Self {
            runtime: Arc::new(Mutex::new(UpdaterRuntimeState::default())),
        }
    }
}

struct UpdaterRuntimeState {
    status: UpdaterStatus,
    latest_version: Option<String>,
    download_progress: Option<f64>,
    error_message: Option<String>,
    pending_update: Option<Update>,
    pending_bytes: Option<Vec<u8>>,
}

impl Default for UpdaterRuntimeState {
    fn default() -> Self {
        Self {
            status: UpdaterStatus::Idle,
            latest_version: None,
            download_progress: None,
            error_message: None,
            pending_update: None,
            pending_bytes: None,
        }
    }
}

#[derive(Clone, Copy)]
enum UpdaterStatus {
    Idle,
    Checking,
    Available,
    Downloading,
    Downloaded,
    Installing,
    Error,
}

impl UpdaterStatus {
    fn as_str(self) -> &'static str {
        match self {
            UpdaterStatus::Idle => "idle",
            UpdaterStatus::Checking => "checking",
            UpdaterStatus::Available => "available",
            UpdaterStatus::Downloading => "downloading",
            UpdaterStatus::Downloaded => "downloaded",
            UpdaterStatus::Installing => "installing",
            UpdaterStatus::Error => "error",
        }
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdaterStatePayload {
    status: String,
    latest_version: Option<String>,
    download_progress: Option<f64>,
    error_message: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BootstrapPrefs {
    theme_mode: String,
    locale: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SaveBootstrapPrefsPayload {
    theme_mode: String,
    locale: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopCopy {
    tray_show_main: String,
    tray_restart_app: String,
    tray_quit: String,
    quit_message: String,
    quit_title: String,
    quit_confirm: String,
    quit_cancel: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopI18nBundle {
    fallback_locale: String,
    locales: HashMap<String, DesktopCopy>,
}

impl Default for BootstrapPrefs {
    fn default() -> Self {
        Self {
            theme_mode: DEFAULT_THEME_MODE.to_string(),
            locale: DEFAULT_LOCALE.to_string(),
        }
    }
}

impl BootstrapPrefs {
    fn normalized(self) -> Self {
        let theme_mode = match self.theme_mode.trim().to_ascii_lowercase().as_str() {
            "light" => "light".to_string(),
            _ => "dark".to_string(),
        };
        let locale = match self.locale.trim().to_ascii_lowercase().as_str() {
            value if value.starts_with("en") => "en-US".to_string(),
            _ => "zh-CN".to_string(),
        };
        Self { theme_mode, locale }
    }
}

// ===== desktop i18n (tray + native dialogs) =====
// TODO(refactor): move desktop i18n loading + resolution to a dedicated module.
fn desktop_copy_default_bundle() -> DesktopI18nBundle {
    serde_json::from_str(DESKTOP_I18N_FALLBACK_JSON).unwrap_or_else(|error| {
        eprintln!("failed to parse embedded desktop i18n fallback json: {error}");
        let mut locales = HashMap::new();
        locales.insert(
            "zh-CN".to_string(),
            DesktopCopy {
                tray_show_main: "显示主窗口".to_string(),
                tray_restart_app: "重启应用".to_string(),
                tray_quit: "退出".to_string(),
                quit_message: "确认退出 NomoClaw 应用吗？".to_string(),
                quit_title: "退出确认".to_string(),
                quit_confirm: "退出".to_string(),
                quit_cancel: "取消".to_string(),
            },
        );
        locales.insert(
            "en-US".to_string(),
            DesktopCopy {
                tray_show_main: "Show Main Window".to_string(),
                tray_restart_app: "Restart App".to_string(),
                tray_quit: "Quit".to_string(),
                quit_message: "Quit NomoClaw?".to_string(),
                quit_title: "Confirm Quit".to_string(),
                quit_confirm: "Quit".to_string(),
                quit_cancel: "Cancel".to_string(),
            },
        );
        DesktopI18nBundle {
            fallback_locale: "zh-CN".to_string(),
            locales,
        }
    })
}

fn desktop_copy_hardcoded_zh() -> DesktopCopy {
    DesktopCopy {
        tray_show_main: "显示主窗口".to_string(),
        tray_restart_app: "重启应用".to_string(),
        tray_quit: "退出".to_string(),
        quit_message: "确认退出 NomoClaw 应用吗？".to_string(),
        quit_title: "退出确认".to_string(),
        quit_confirm: "退出".to_string(),
        quit_cancel: "取消".to_string(),
    }
}

fn read_desktop_i18n_bundle<R: Runtime>(app: &AppHandle<R>) -> DesktopI18nBundle {
    let mut candidates = vec![PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("resources")
        .join(DESKTOP_I18N_FILE)];

    if let Ok(resource_dir) = app.path().resource_dir() {
        candidates.push(resource_dir.join(DESKTOP_I18N_FILE));
        candidates.push(resource_dir.join("resources").join(DESKTOP_I18N_FILE));
    }

    for candidate in candidates {
        if !candidate.exists() {
            continue;
        }
        match fs::read_to_string(&candidate) {
            Ok(raw) => match serde_json::from_str::<DesktopI18nBundle>(&raw) {
                Ok(bundle) => return bundle,
                Err(error) => eprintln!("invalid desktop i18n json {}: {error}", candidate.display()),
            },
            Err(error) => eprintln!("failed to read desktop i18n file {}: {error}", candidate.display()),
        }
    }

    desktop_copy_default_bundle()
}

fn resolve_desktop_copy(bundle: &DesktopI18nBundle, locale: &str) -> DesktopCopy {
    let requested = locale.trim();
    if let Some(copy) = bundle.locales.get(requested) {
        return copy.clone();
    }

    let requested_lower = requested.to_ascii_lowercase();
    if let Some((_, copy)) = bundle
        .locales
        .iter()
        .find(|(key, _)| key.to_ascii_lowercase() == requested_lower)
    {
        return copy.clone();
    }

    let language = requested_lower.split('-').next().unwrap_or("");
    if !language.is_empty() {
        if let Some((_, copy)) = bundle.locales.iter().find(|(key, _)| {
            key.to_ascii_lowercase()
                .split('-')
                .next()
                .map(|part| part == language)
                .unwrap_or(false)
        }) {
            return copy.clone();
        }
    }

    if let Some(copy) = bundle.locales.get(bundle.fallback_locale.as_str()) {
        return copy.clone();
    }

    bundle
        .locales
        .values()
        .next()
        .cloned()
        .unwrap_or_else(desktop_copy_hardcoded_zh)
}

fn desktop_copy<R: Runtime>(app: &AppHandle<R>) -> DesktopCopy {
    let locale = read_bootstrap_prefs(app)
        .map(|prefs| prefs.locale)
        .unwrap_or_else(|_| DEFAULT_LOCALE.to_string());
    let bundle = read_desktop_i18n_bundle(app);
    resolve_desktop_copy(&bundle, &locale)
}

// ===== application lifecycle =====
// TODO(refactor): extract setup/run/exit orchestration into app_lifecycle.rs.
fn main() {
    let builder = tauri::Builder::default()
        .on_page_load(|webview, payload| {
            if webview.label() != WINDOW_LABEL {
                return;
            }
            if payload.event() != PageLoadEvent::Finished {
                return;
            }
            if !SHOW_MAIN_ON_NEXT_FINISHED_LOAD.swap(false, Ordering::SeqCst) {
                return;
            }
            if EXITING.load(Ordering::SeqCst) {
                return;
            }

            let app = webview.app_handle();
            let _ = show_main_window(&app);
        })
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .invoke_handler(tauri::generate_handler![
            save_bootstrap_prefs,
            updater_get_state,
            updater_install_downloaded,
            updater_check_now
        ])
        .setup(|app| {
            app.manage(DesktopState::default());
            app.manage(UpdaterState::default());

            acquire_single_instance_lock(app.handle())?;
            cleanup_known_orphan_java_processes();
            cleanup_stale_backend_group(app.handle());

            register_tray(app)?;
            install_window_behavior(app)?;
            show_loading_window(app)?;
            bootstrap_backend_async(app.handle().clone());
            install_backend_watchdog(app)?;
            install_updater_watchdog(app)?;
            request_updater_check(app.handle().clone(), "startup");
            Ok(())
        });

    let app = match builder.build(tauri::generate_context!()) {
        Ok(app) => app,
        Err(error) => {
            eprintln!("failed to build tauri app: {error}");
            return;
        }
    };

    app.run(|app_handle, event| {
        match event {
            RunEvent::ExitRequested { code, api, .. } => {
                if EXITING.load(Ordering::SeqCst) {
                    return;
                }
                if matches!(code, Some(exit_code) if exit_code == tauri::RESTART_EXIT_CODE) {
                    return;
                }
                api.prevent_exit();
                request_quit_with_confirmation(app_handle);
            }
            #[cfg(target_os = "macos")]
            RunEvent::Reopen { .. } => {
                if !EXITING.load(Ordering::SeqCst) {
                    let _ = show_main_window(app_handle);
                }
            }
            _ => {}
        }
    });
}

fn shutdown_and_exit<R: Runtime>(app: &AppHandle<R>) {
    if EXITING.swap(true, Ordering::SeqCst) {
        return;
    }
    flush_bootstrap_prefs_from_window(app);
    let _ = stop_backend(app);
    release_single_instance_lock(app);
    app.exit(0);
}

// Restart path intentionally performs the same cleanup as normal exit so
// lock/runtime state does not leak across relaunch.
fn request_app_restart<R: Runtime>(app: &AppHandle<R>) {
    if EXITING.swap(true, Ordering::SeqCst) {
        return;
    }
    flush_bootstrap_prefs_from_window(app);
    let _ = stop_backend(app);
    release_single_instance_lock(app);
    app.request_restart();
}

// Single confirmation entrypoint for all exit paths (tray/menu/Cmd+Q/Dock).
fn request_quit_with_confirmation<R: Runtime>(app: &AppHandle<R>) {
    if EXITING.load(Ordering::SeqCst) {
        return;
    }
    if QUIT_CONFIRMING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return;
    }

    let app_handle = app.clone();
    let copy = desktop_copy(&app_handle);
    tauri::async_runtime::spawn(async move {
        let should_quit = app_handle
            .dialog()
            .message(copy.quit_message)
            .title(copy.quit_title)
            .kind(MessageDialogKind::Warning)
            .buttons(MessageDialogButtons::OkCancelCustom(
                copy.quit_confirm.to_string(),
                copy.quit_cancel.to_string(),
            ))
            .blocking_show();

        QUIT_CONFIRMING.store(false, Ordering::SeqCst);

        if should_quit && !EXITING.load(Ordering::SeqCst) {
            shutdown_and_exit(&app_handle);
        }
    });
}

// ===== backend bootstrap / tray / window behavior =====
fn bootstrap_backend<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    let _startup_guard = BackendStartupGuard::enter();
    let dev_attach = is_dev_attach_mode();
    let preferred_port = env_u16("NOMOCLAW_BACKEND_PORT", 18080);

    let runtime = if dev_attach {
        BackendRuntime {
            mode: BackendMode::Attach,
            port: preferred_port,
            child: None,
            #[cfg(unix)]
            process_group_id: None,
        }
    } else {
        start_backend_process(&app, preferred_port)?
    };
    let runtime_port = runtime.port;

    let state = app.state::<DesktopState>();
    {
        let mut guard = state
            .runtime
            .lock()
            .map_err(|_| anyhow!("failed to lock runtime state"))?;
        *guard = Some(runtime);
    }

    if let Err(error) = wait_backend_ready(runtime_port) {
        let _ = stop_backend(&app);
        return Err(error);
    }

    if EXITING.load(Ordering::SeqCst) {
        return Ok(());
    }

    if let Err(error) = update_window_url(&app, &backend_url(runtime_port)) {
        if !EXITING.load(Ordering::SeqCst) {
            let _ = stop_backend(&app);
        }
        return Err(error);
    }

    Ok(())
}

fn bootstrap_backend_async<R: Runtime>(app: AppHandle<R>) {
    tauri::async_runtime::spawn(async move {
        if EXITING.load(Ordering::SeqCst) {
            return;
        }

        if let Err(error) = bootstrap_backend(app.clone()) {
            eprintln!("backend bootstrap failed: {error:#}");
            let _ = stop_backend(&app);
            if !EXITING.load(Ordering::SeqCst) {
                let _ = show_bootstrap_error_page(&app, &format!("{error:#}"));
            }
        }
    });
}

fn register_tray<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    let copy = desktop_copy(app.handle());
    let menu = build_tray_menu(app, &copy)?;
    let tray_builder = TrayIconBuilder::with_id(TRAY_ICON_ID).menu(&menu).show_menu_on_left_click(false);

    #[cfg(target_os = "macos")]
    let tray_builder = {
        // macOS status bar icons should be template images so the system
        // can auto-adapt contrast in light/dark menu bars.
        if !has_packaged_macos_tray_template_icon(app) {
            eprintln!(
                "failed to load macOS tray template icon from resources; fallback to embedded icon: {}",
                MACOS_TRAY_TEMPLATE_ICON_FILE
            );
        }
        let icon = FALLBACK_TRAY_TEMPLATE_ICON.to_owned();
        tray_builder.icon(icon).icon_as_template(true)
    };

    #[cfg(not(target_os = "macos"))]
    let tray_builder = tray_builder.icon(FALLBACK_TRAY_ICON.to_owned());

    tray_builder
        .on_menu_event(|app, event| match event.id().as_ref() {
            TRAY_MENU_SHOW => {
                let _ = show_main_window(app);
            }
            TRAY_MENU_RESTART_APP => request_app_restart(app),
            TRAY_MENU_QUIT => request_quit_with_confirmation(app),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                let _ = show_main_window(tray.app_handle());
            }
        })
        .build(app)?;

    Ok(())
}

fn build_tray_menu<R: Runtime, M: Manager<R>>(manager: &M, copy: &DesktopCopy) -> Result<Menu<R>> {
    let show_item = MenuItem::with_id(manager, TRAY_MENU_SHOW, &copy.tray_show_main, true, None::<&str>)?;
    let restart_item = MenuItem::with_id(manager, TRAY_MENU_RESTART_APP, &copy.tray_restart_app, true, None::<&str>)?;
    let quit_item = MenuItem::with_id(manager, TRAY_MENU_QUIT, &copy.tray_quit, true, None::<&str>)?;
    Menu::with_items(manager, &[&show_item, &restart_item, &quit_item]).map_err(Into::into)
}

fn refresh_tray_menu_for_locale<R: Runtime>(app: &AppHandle<R>, locale: &str) {
    let Some(tray) = app.tray_by_id(TRAY_ICON_ID) else {
        return;
    };
    let bundle = read_desktop_i18n_bundle(app);
    let copy = resolve_desktop_copy(&bundle, locale);
    match build_tray_menu(app, &copy) {
        Ok(menu) => {
            if let Err(error) = tray.set_menu(Some(menu)) {
                eprintln!("failed to refresh tray menu locale={locale}: {error}");
            }
        }
        Err(error) => {
            eprintln!("failed to build tray menu locale={locale}: {error}");
        }
    }
}

#[cfg(target_os = "macos")]
fn has_packaged_macos_tray_template_icon<R: Runtime>(app: &tauri::App<R>) -> bool {
    let mut candidates = vec![PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("icons")
        .join(MACOS_TRAY_TEMPLATE_ICON_FILE)];
    if let Ok(resource_dir) = app.path().resource_dir() {
        candidates.push(resource_dir.join(MACOS_TRAY_TEMPLATE_ICON_FILE));
        candidates.push(
            resource_dir
                .join("resources")
                .join(MACOS_TRAY_TEMPLATE_ICON_FILE),
        );
    }

    for candidate in candidates {
        if candidate.exists() {
            return true;
        }
    }
    false
}

fn install_window_behavior<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    let window = app
        .get_webview_window(WINDOW_LABEL)
        .context("missing main window")?;
    let window_handle = window.clone();

    window.on_window_event(move |event| {
        if let WindowEvent::CloseRequested { api, .. } = event {
            api.prevent_close();
            let _ = window_handle.hide();
        }
    });

    Ok(())
}

fn restart_backend<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
    let _startup_guard = BackendStartupGuard::enter();
    let state = app.state::<DesktopState>();
    let existing_mode = {
        let guard = state
            .runtime
            .lock()
            .map_err(|_| anyhow!("failed to lock runtime state"))?;
        guard.as_ref().map(|runtime| runtime.mode)
    };

    if matches!(existing_mode, Some(BackendMode::Attach)) {
        let port = env_u16("NOMOCLAW_BACKEND_PORT", 18080);
        wait_backend_ready(port)?;
        update_window_url(app, &backend_url(port))?;
        return Ok(());
    }

    stop_backend(app)?;
    let preferred_port = env_u16("NOMOCLAW_BACKEND_PORT", 18080);
    let runtime = start_backend_process(app, preferred_port)?;
    let runtime_port = runtime.port;

    {
        let mut guard = state
            .runtime
            .lock()
            .map_err(|_| anyhow!("failed to lock runtime state"))?;
        *guard = Some(runtime);
    }

    if let Err(error) = wait_backend_ready(runtime_port) {
        let _ = stop_backend(app);
        return Err(error);
    }
    update_window_url(app, &backend_url(runtime_port))?;

    Ok(())
}

// ===== backend watchdog =====
fn request_backend_restart<R: Runtime>(app: AppHandle<R>, reason: &'static str) {
    if EXITING.load(Ordering::SeqCst) {
        return;
    }
    if BACKEND_RESTARTING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return;
    }
    BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);

    tauri::async_runtime::spawn(async move {
        if let Err(error) = restart_backend(&app) {
            eprintln!("backend restart failed ({reason}): {error:#}");
        }
        BACKEND_RESTARTING.store(false, Ordering::SeqCst);
    });
}

fn install_backend_watchdog<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    let app_handle = app.handle().clone();
    let interval = Duration::from_millis(env_u64("NOMOCLAW_BACKEND_WATCH_INTERVAL_MS", 10_000).max(500));
    let fail_threshold = env_u32("NOMOCLAW_BACKEND_WATCH_FAIL_THRESHOLD", 3).max(1);
    let health_timeout = Duration::from_millis(env_u64("NOMOCLAW_BACKEND_WATCH_HEALTH_TIMEOUT_MS", 800).max(100));
    thread::Builder::new()
        .name("backend-watchdog".to_string())
        .spawn(move || loop {
            if EXITING.load(Ordering::SeqCst) {
                return;
            }
            thread::sleep(interval);
            if EXITING.load(Ordering::SeqCst) {
                return;
            }
            if backend_needs_restart(&app_handle, health_timeout, fail_threshold) {
                request_backend_restart(app_handle.clone(), "watchdog");
            }
        })
        .context("failed to spawn backend watchdog thread")?;
    Ok(())
}

fn backend_needs_restart<R: Runtime>(app: &AppHandle<R>, health_timeout: Duration, fail_threshold: u32) -> bool {
    let is_starting = BACKEND_STARTING.load(Ordering::SeqCst);
    let is_restarting = BACKEND_RESTARTING.load(Ordering::SeqCst);
    if is_starting || is_restarting {
        BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
        return false;
    }

    let port = {
        let state = app.state::<DesktopState>();
        let mut guard = match state.runtime.lock() {
            Ok(guard) => guard,
            Err(_) => {
                BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
                return false;
            }
        };

        let Some(runtime) = guard.as_mut() else {
            BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
            return false;
        };
        if runtime.mode != BackendMode::Spawn {
            BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
            return false;
        }

        if let Some(child) = runtime.child.as_mut() {
            match child.try_wait() {
                Ok(Some(status)) => {
                    eprintln!(
                        "backend watchdog restart trigger reason=process_exited status={status} starting={} restarting={} fail_streak={}",
                        is_starting,
                        is_restarting,
                        BACKEND_WATCH_FAIL_STREAK.load(Ordering::SeqCst)
                    );
                    BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
                    return true;
                }
                Ok(None) => {}
                Err(error) => {
                    eprintln!(
                        "backend watchdog restart trigger reason=process_status_error error={error:#} starting={} restarting={} fail_streak={}",
                        is_starting,
                        is_restarting,
                        BACKEND_WATCH_FAIL_STREAK.load(Ordering::SeqCst)
                    );
                    BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
                    return true;
                }
            }
        }

        runtime.port
    };

    let is_healthy = check_backend_ready_once(port, health_timeout);
    let previous_failures = BACKEND_WATCH_FAIL_STREAK.load(Ordering::SeqCst);
    let (should_restart, next_failures) =
        evaluate_watchdog_health_debounce(is_starting, is_restarting, is_healthy, previous_failures, fail_threshold);

    if next_failures != previous_failures {
        BACKEND_WATCH_FAIL_STREAK.store(next_failures, Ordering::SeqCst);
    }

    if !is_healthy && !should_restart {
        eprintln!(
            "backend watchdog health check failed but debounced fail_streak={} threshold={} timeout_ms={}",
            next_failures,
            fail_threshold,
            health_timeout.as_millis()
        );
    }

    if should_restart {
        eprintln!(
            "backend watchdog restart trigger reason=health_check failures={} threshold={} starting={} restarting={} timeout_ms={}",
            next_failures,
            fail_threshold,
            is_starting,
            is_restarting,
            health_timeout.as_millis()
        );
    }

    should_restart
}

fn check_backend_ready_once(port: u16, timeout: Duration) -> bool {
    let health_url = format!("http://127.0.0.1:{port}/api/system/config");
    let client = match Client::builder().timeout(timeout).build() {
        Ok(client) => client,
        Err(_) => return false,
    };
    match client.get(health_url).send() {
        Ok(response) => response.status().is_success(),
        Err(_) => false,
    }
}

fn evaluate_watchdog_health_debounce(
    is_starting: bool,
    is_restarting: bool,
    is_healthy: bool,
    previous_failures: u32,
    fail_threshold: u32,
) -> (bool, u32) {
    if is_starting || is_restarting || is_healthy {
        return (false, 0);
    }

    let threshold = fail_threshold.max(1);
    let next_failures = previous_failures.saturating_add(1);
    (next_failures >= threshold, next_failures)
}

// ===== updater =====
fn updater_supported<R: Runtime>(_app: &AppHandle<R>) -> bool {
    cfg!(target_os = "macos") || cfg!(target_os = "windows")
}

fn snapshot_updater_state(state: &UpdaterRuntimeState) -> UpdaterStatePayload {
    UpdaterStatePayload {
        status: state.status.as_str().to_string(),
        latest_version: state.latest_version.clone(),
        download_progress: state.download_progress,
        error_message: state.error_message.clone(),
    }
}

fn emit_updater_state<R: Runtime>(app: &AppHandle<R>, payload: &UpdaterStatePayload) {
    let _ = app.emit(UPDATER_EVENT_STATE, payload.clone());
}

fn set_updater_state<R: Runtime, F>(app: &AppHandle<R>, updater_state: &UpdaterState, mutator: F)
where
    F: FnOnce(&mut UpdaterRuntimeState),
{
    let payload = {
        let mut guard = match updater_state.runtime.lock() {
            Ok(guard) => guard,
            Err(_) => return,
        };
        mutator(&mut guard);
        snapshot_updater_state(&guard)
    };
    emit_updater_state(app, &payload);
}

fn request_updater_check<R: Runtime>(app: AppHandle<R>, reason: &'static str) {
    if !updater_supported(&app) || EXITING.load(Ordering::SeqCst) || UPDATER_INSTALLING.load(Ordering::SeqCst) {
        return;
    }
    {
        let updater_state = app.state::<UpdaterState>().inner().clone();
        let skip_check = updater_state
            .runtime
            .lock()
            .map(|guard| {
                matches!(
                    guard.status,
                    UpdaterStatus::Checking | UpdaterStatus::Downloading | UpdaterStatus::Downloaded | UpdaterStatus::Installing
                )
            })
            .unwrap_or(false);
        if skip_check {
            return;
        }
    }
    if UPDATER_CHECKING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return;
    }

    tauri::async_runtime::spawn(async move {
        let updater_state = app.state::<UpdaterState>().inner().clone();
        if let Err(error) = run_updater_check(app.clone(), updater_state).await {
            eprintln!("updater check failed ({reason}): {error:#}");
            let updater_state = app.state::<UpdaterState>();
            set_updater_state(&app, updater_state.inner(), |state| {
                state.status = UpdaterStatus::Error;
                state.error_message = Some(error.to_string());
                state.pending_update = None;
                state.pending_bytes = None;
            });
        }
        UPDATER_CHECKING.store(false, Ordering::SeqCst);
    });
}

async fn run_updater_check<R: Runtime>(app: AppHandle<R>, updater_state: UpdaterState) -> Result<()> {
    set_updater_state(&app, &updater_state, |state| {
        state.status = UpdaterStatus::Checking;
        state.download_progress = None;
        state.error_message = None;
        state.pending_update = None;
        state.pending_bytes = None;
    });

    let update = app.updater()?.check().await?;
    let Some(update) = update else {
        set_updater_state(&app, &updater_state, |state| {
            state.status = UpdaterStatus::Idle;
            state.latest_version = None;
            state.download_progress = None;
            state.error_message = None;
            state.pending_update = None;
            state.pending_bytes = None;
        });
        return Ok(());
    };

    let latest_version = update.version.clone();
    set_updater_state(&app, &updater_state, |state| {
        state.status = UpdaterStatus::Available;
        state.latest_version = Some(latest_version.clone());
        state.download_progress = Some(0.0);
        state.error_message = None;
    });

    let mut downloaded: usize = 0;
    let mut total_size: Option<u64> = None;
    let app_for_progress = app.clone();
    let updater_state_for_progress = updater_state.clone();
    let app_for_finish = app.clone();
    let updater_state_for_finish = updater_state.clone();

    let bytes = update
        .download(
            move |chunk_length, content_length| {
                downloaded += chunk_length;
                if total_size.is_none() {
                    total_size = content_length;
                }
                let progress = total_size.and_then(|content_len| {
                    if content_len == 0 {
                        return None;
                    }
                    Some((downloaded as f64 / content_len as f64).min(1.0))
                });
                set_updater_state(&app_for_progress, &updater_state_for_progress, |state| {
                    state.status = UpdaterStatus::Downloading;
                    state.download_progress = progress;
                    state.error_message = None;
                });
            },
            move || {
                set_updater_state(&app_for_finish, &updater_state_for_finish, |state| {
                    state.status = UpdaterStatus::Downloading;
                    state.download_progress = Some(1.0);
                });
            },
        )
        .await?;

    set_updater_state(&app, &updater_state, move |state| {
        state.status = UpdaterStatus::Downloaded;
        state.latest_version = Some(latest_version);
        state.download_progress = Some(1.0);
        state.error_message = None;
        state.pending_update = Some(update);
        state.pending_bytes = Some(bytes);
    });

    Ok(())
}

fn install_updater_watchdog<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    if !updater_supported(&app.handle()) {
        return Ok(());
    }
    let app_handle = app.handle().clone();
    thread::Builder::new()
        .name("updater-watchdog".to_string())
        .spawn(move || loop {
            if EXITING.load(Ordering::SeqCst) {
                return;
            }
            thread::sleep(Duration::from_secs(UPDATE_CHECK_INTERVAL_SECONDS));
            if EXITING.load(Ordering::SeqCst) {
                return;
            }
            request_updater_check(app_handle.clone(), "interval");
        })
        .context("failed to spawn updater watchdog thread")?;
    Ok(())
}

// ===== tauri command handlers =====
#[tauri::command]
fn save_bootstrap_prefs(app: AppHandle, payload: SaveBootstrapPrefsPayload) -> std::result::Result<(), String> {
    let normalized = BootstrapPrefs {
        theme_mode: payload.theme_mode,
        locale: payload.locale,
    }
    .normalized();
    write_bootstrap_prefs(&app, normalized.clone()).map_err(|error| error.to_string())?;
    refresh_tray_menu_for_locale(&app, &normalized.locale);
    Ok(())
}

#[tauri::command]
fn updater_get_state(updater_state: State<'_, UpdaterState>) -> std::result::Result<UpdaterStatePayload, String> {
    let guard = updater_state
        .runtime
        .lock()
        .map_err(|_| "failed to lock updater state".to_string())?;
    Ok(snapshot_updater_state(&guard))
}

#[tauri::command]
fn updater_check_now(app: AppHandle) -> std::result::Result<(), String> {
    if !updater_supported(&app) {
        return Err("updater is only enabled on macOS and Windows".to_string());
    }
    request_updater_check(app, "manual");
    Ok(())
}

#[tauri::command]
async fn updater_install_downloaded(
    app: AppHandle,
    updater_state: State<'_, UpdaterState>,
) -> std::result::Result<(), String> {
    if !updater_supported(&app) {
        return Err("updater is only enabled on macOS and Windows".to_string());
    }
    if UPDATER_INSTALLING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return Err("update installation is already running".to_string());
    }

    let (update, bytes) = {
        let mut guard = updater_state
            .runtime
            .lock()
            .map_err(|_| "failed to lock updater state".to_string())?;
        let Some(update) = guard.pending_update.take() else {
            UPDATER_INSTALLING.store(false, Ordering::SeqCst);
            return Err("no downloaded update available".to_string());
        };
        let Some(bytes) = guard.pending_bytes.take() else {
            UPDATER_INSTALLING.store(false, Ordering::SeqCst);
            return Err("no downloaded update payload available".to_string());
        };
        guard.status = UpdaterStatus::Installing;
        guard.error_message = None;
        guard.download_progress = Some(1.0);
        let payload = snapshot_updater_state(&guard);
        drop(guard);
        emit_updater_state(&app, &payload);
        (update, bytes)
    };

    let install_result = update.install(bytes);
    match install_result {
        Ok(()) => {
            UPDATER_INSTALLING.store(false, Ordering::SeqCst);
            app.restart();
        }
        Err(error) => {
            UPDATER_INSTALLING.store(false, Ordering::SeqCst);
            set_updater_state(&app, updater_state.inner(), |state| {
                state.status = UpdaterStatus::Error;
                state.error_message = Some(error.to_string());
                state.download_progress = None;
                state.pending_update = None;
                state.pending_bytes = None;
            });
            Err(error.to_string())
        }
    }
}

// ===== window/content bootstrap =====
fn stop_backend<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
    BACKEND_STARTING.store(false, Ordering::SeqCst);
    BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
    let state = app.state::<DesktopState>();
    let mut guard = state
        .runtime
        .lock()
        .map_err(|_| anyhow!("failed to lock runtime state"))?;

    let Some(runtime) = guard.as_mut() else {
        cleanup_stale_backend_group(app);
        return Ok(());
    };

    if runtime.mode == BackendMode::Attach {
        *guard = None;
        return Ok(());
    }

    if let Some(mut child) = runtime.child.take() {
        terminate_child_gracefully(&mut child)?;
    }

    #[cfg(unix)]
    if let Some(pgid) = runtime.process_group_id {
        terminate_process_group(pgid);
        wait_process_group_exit(pgid, Duration::from_secs(5));
    }

    cleanup_stale_backend_group(app);
    *guard = None;

    Ok(())
}

fn show_main_window<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
    let window = app
        .get_webview_window(WINDOW_LABEL)
        .context("missing main window")?;
    window.show()?;
    window.unminimize()?;
    window.set_focus()?;
    Ok(())
}

fn show_loading_window<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    let window = app
        .get_webview_window(WINDOW_LABEL)
        .context("missing main window")?;
    let prefs = read_bootstrap_prefs(app.handle()).unwrap_or_default();
    let _ = inject_bootstrap_prefs(&window, &prefs);
    SHOW_MAIN_ON_NEXT_FINISHED_LOAD.store(true, Ordering::SeqCst);
    Ok(())
}

fn update_window_url<R: Runtime>(app: &AppHandle<R>, target_url: &str) -> Result<()> {
  let window = app
    .get_webview_window(WINDOW_LABEL)
    .context("missing main window")?;
  SHOW_MAIN_ON_NEXT_FINISHED_LOAD.store(true, Ordering::SeqCst);
  let safe_url = target_url.replace('\\', "\\\\").replace('"', "\\\"");
  window.eval(&format!("window.location.replace(\"{safe_url}\");"))?;
  Ok(())
}

fn show_bootstrap_error_page<R: Runtime>(app: &AppHandle<R>, raw_error: &str) -> Result<()> {
  let window = app
    .get_webview_window(WINDOW_LABEL)
    .context("missing main window")?;
  SHOW_MAIN_ON_NEXT_FINISHED_LOAD.store(false, Ordering::SeqCst);
  let escaped = js_escape(raw_error);
    let script = format!(
        "document.body.innerHTML = '<main style=\"font-family:-apple-system,BlinkMacSystemFont,\\'SF Pro Text\\',\\'PingFang SC\\',sans-serif;max-width:760px;margin:48px auto;padding:24px;border:1px solid #fecaca;border-radius:12px;background:#fff5f5;color:#7f1d1d;line-height:1.6\">\
        <h1 style=\"margin:0 0 8px;font-size:24px\">NomoClaw 启动失败</h1>\
        <p style=\"margin:0 0 12px\">后端进程未成功就绪，请检查日志后重试。</p>\
        <pre style=\"white-space:pre-wrap;word-break:break-word;background:#fff;border:1px solid #fecaca;padding:12px;border-radius:8px\">'+\"{escaped}\"+'</pre>\
        </main>';"
    );
    window.eval(&script)?;
    window.show()?;
    Ok(())
}

fn js_escape(input: &str) -> String {
    input
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "")
}

// ===== persisted bootstrap prefs =====
// These prefs are used before web app hydration to style/localize the loading page.
fn bootstrap_prefs_file_path<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    Ok(nomoclaw_root_dir(app)?.join(BOOTSTRAP_PREFS_FILE))
}

fn read_bootstrap_prefs<R: Runtime>(app: &AppHandle<R>) -> Result<BootstrapPrefs> {
    let path = bootstrap_prefs_file_path(app)?;
    if !path.exists() {
        return Ok(BootstrapPrefs::default());
    }

    let raw = fs::read_to_string(&path)
        .with_context(|| format!("failed to read bootstrap prefs: {}", path.display()))?;
    let prefs: BootstrapPrefs = serde_json::from_str(&raw)
        .with_context(|| format!("invalid bootstrap prefs json: {}", path.display()))?;
    Ok(prefs.normalized())
}

fn write_bootstrap_prefs<R: Runtime>(app: &AppHandle<R>, prefs: BootstrapPrefs) -> Result<()> {
    let path = bootstrap_prefs_file_path(app)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create bootstrap prefs dir: {}", parent.display()))?;
    }
    let normalized = prefs.normalized();
    let payload = serde_json::to_string_pretty(&normalized)?;
    fs::write(&path, format!("{payload}\n"))
        .with_context(|| format!("failed to write bootstrap prefs: {}", path.display()))?;
    Ok(())
}

fn inject_bootstrap_prefs<R: Runtime>(window: &tauri::WebviewWindow<R>, prefs: &BootstrapPrefs) -> Result<()> {
    let theme_mode = js_escape(&prefs.theme_mode);
    let locale = js_escape(&prefs.locale);
    let script = format!(
        "const prefs = {{ themeMode: \"{theme_mode}\", locale: \"{locale}\" }};\n\
         window.__NOMOCLAW_BOOTSTRAP_PREFS__ = prefs;\n\
         if (typeof window.__NOMOCLAW_APPLY_BOOTSTRAP_PREFS__ === \"function\") {{\n\
           window.__NOMOCLAW_APPLY_BOOTSTRAP_PREFS__(prefs, {{ persistToStorage: true }});\n\
         }} else {{\n\
           try {{ localStorage.setItem(\"{BOOTSTRAP_THEME_STORAGE_KEY}\", \"{theme_mode}\"); localStorage.setItem(\"{BOOTSTRAP_LOCALE_STORAGE_KEY}\", \"{locale}\"); }} catch (e) {{}}\n\
           document.documentElement.setAttribute(\"lang\", \"{locale}\");\n\
           document.documentElement.setAttribute(\"data-theme\", \"{theme_mode}\");\n\
         }}"
    );
    window.eval(&script)?;
    Ok(())
}

fn flush_bootstrap_prefs_from_window<R: Runtime>(app: &AppHandle<R>) {
    let Some(window) = app.get_webview_window(WINDOW_LABEL) else {
        return;
    };

    let script = r#"
        (function () {
          try {
            const storedTheme = localStorage.getItem("__THEME_STORAGE_KEY__");
            const storedLocale = localStorage.getItem("__LOCALE_STORAGE_KEY__");
            if (storedTheme === null && storedLocale === null) {
              return;
            }
            const themeMode = String(storedTheme || "__DEFAULT_THEME_MODE__").toLowerCase() === "light" ? "light" : "dark";
            const locale = String(storedLocale || "__DEFAULT_LOCALE__").toLowerCase().startsWith("en") ? "en-US" : "zh-CN";
            if (window.__TAURI_INTERNALS__?.invoke) {
              window.__TAURI_INTERNALS__.invoke("save_bootstrap_prefs", {
                payload: { themeMode, locale }
              });
            }
          } catch (_) {}
        })();
    "#
    .replace("__THEME_STORAGE_KEY__", BOOTSTRAP_THEME_STORAGE_KEY)
    .replace("__LOCALE_STORAGE_KEY__", BOOTSTRAP_LOCALE_STORAGE_KEY)
    .replace("__DEFAULT_THEME_MODE__", DEFAULT_THEME_MODE)
    .replace("__DEFAULT_LOCALE__", DEFAULT_LOCALE);

    if window.eval(script).is_ok() {
        thread::sleep(Duration::from_millis(120));
    }
}

// ===== backend process + runtime discovery =====
// TODO(refactor): split process-launch and filesystem-path resolution.
fn start_backend_process<R: Runtime>(app: &AppHandle<R>, preferred_port: u16) -> Result<BackendRuntime> {
    let port = resolve_port(preferred_port)?;
    let (java_bin, jar_path) = resolve_runtime_paths(app)?;
    #[cfg(windows)]
    let java_bin = windows_compatible_path(&java_bin);
    #[cfg(windows)]
    let jar_path = windows_compatible_path(&jar_path);
    let log_path = ensure_log_file_path(app)?;

    let mut log_file = File::options()
        .create(true)
        .append(true)
        .open(&log_path)
        .with_context(|| format!("failed to open log file: {}", log_path.display()))?;
    let jar_size = fs::metadata(&jar_path).map(|meta| meta.len()).unwrap_or(0);
    let _ = writeln!(
        &mut log_file,
        "[desktop] launch backend java={} jar={} jar_size={} port={}",
        java_bin.display(),
        jar_path.display(),
        jar_size,
        port
    );
    let log_file_err = log_file
        .try_clone()
        .with_context(|| format!("failed to clone log file: {}", log_path.display()))?;

    #[cfg(unix)]
    let parent_pid = std::process::id();

    #[cfg(unix)]
    {
        let java_bin_quoted = shell_quote(&java_bin.to_string_lossy());
        let jar_path_quoted = shell_quote(&jar_path.to_string_lossy());
        let script = format!(
            "{java} \
            -Dspring.profiles.active=h2 \
            -Dnomoclaw.desktop.open-browser-on-startup=false \
            -Dfile.encoding=UTF-8 \
            -Dsun.stdout.encoding=UTF-8 \
            -Dsun.stderr.encoding=UTF-8 \
            -Dserver.shutdown=immediate \
            -Dspring.lifecycle.timeout-per-shutdown-phase=2s \
            -Dserver.port={port} \
            -jar {jar} & \
            JAVA_PID=$!; \
            while /bin/kill -0 {parent_pid} 2>/dev/null; do sleep 0.5; done; \
            /bin/kill -TERM \"$JAVA_PID\" 2>/dev/null || true; \
            sleep 1; \
            /bin/kill -KILL \"$JAVA_PID\" 2>/dev/null || true; \
            wait \"$JAVA_PID\" 2>/dev/null || true",
            java = java_bin_quoted,
            jar = jar_path_quoted,
            port = port,
            parent_pid = parent_pid
        );

        let mut command = Command::new("/bin/sh");
        command
            .arg("-c")
            .arg(script)
            .stdout(Stdio::from(log_file))
            .stderr(Stdio::from(log_file_err));

        #[cfg(unix)]
        {
            command.process_group(0);
        }

        let child = command
            .spawn()
            .with_context(|| format!("failed to spawn backend with java={}", java_bin.display()))?;

        #[cfg(unix)]
        let process_group_id = Some(child.id() as i32);

        #[cfg(unix)]
        persist_backend_pgid(app, child.id() as i32);

        Ok(BackendRuntime {
            mode: BackendMode::Spawn,
            port,
            child: Some(child),
            #[cfg(unix)]
            process_group_id,
        })
    }

    #[cfg(windows)]
    {
        let child = Command::new(&java_bin)
            .arg("-Dspring.profiles.active=h2")
            .arg("-Dnomoclaw.desktop.open-browser-on-startup=false")
            .arg("-Dnomoclaw.h2.auto-server=false")
            .arg("-Dspring.quartz.startup-delay-seconds=10")
            .arg("-Dnomoclaw.quartz.restore-delay-seconds=10")
            .arg("-Dfile.encoding=UTF-8")
            .arg("-Dsun.stdout.encoding=UTF-8")
            .arg("-Dsun.stderr.encoding=UTF-8")
            .arg("-Dserver.shutdown=immediate")
            .arg("-Dspring.lifecycle.timeout-per-shutdown-phase=2s")
            .arg(format!("-Dserver.port={port}"))
            .arg("-jar")
            .arg(&jar_path)
            .stdout(Stdio::from(log_file))
            .stderr(Stdio::from(log_file_err))
            .creation_flags(0x08000000)
            .spawn()
            .with_context(|| format!("failed to spawn backend with java={}", java_bin.display()))?;

        Ok(BackendRuntime {
            mode: BackendMode::Spawn,
            port,
            child: Some(child),
        })
    }
}

// ===== environment / utility helpers =====
#[cfg(unix)]
fn shell_quote(raw: &str) -> String {
    format!("'{}'", raw.replace('\'', "'\"'\"'"))
}

fn resolve_runtime_paths<R: Runtime>(app: &AppHandle<R>) -> Result<(PathBuf, PathBuf)> {
    if cfg!(debug_assertions) {
        if let (Ok(java_bin), Ok(jar_path)) = (env::var("NOMOCLAW_JAVA_BIN"), env::var("NOMOCLAW_DEV_JAR")) {
            return Ok((PathBuf::from(java_bin), PathBuf::from(jar_path)));
        }
    }

    let resource_dir = app
        .path()
        .resource_dir()
        .context("failed to resolve tauri resource directory")?;

    let java_candidates = [
        resource_dir.join("backend/runtime/bin/java"),
        resource_dir.join("backend/runtime/bin/java.exe"),
        resource_dir.join("resources/backend/runtime/bin/java"),
        resource_dir.join("resources/backend/runtime/bin/java.exe"),
        resource_dir.join("runtime/bin/java"),
        resource_dir.join("runtime/bin/java.exe"),
    ];
    let jar_candidates = [
        resource_dir.join("backend/nomoclaw.jar"),
        resource_dir.join("resources/backend/nomoclaw.jar"),
        resource_dir.join("nomoclaw.jar"),
    ];

    let java_bin = java_candidates
        .iter()
        .find(|path| path.exists())
        .cloned()
        .ok_or_else(|| anyhow!("embedded runtime not found"))?;

    let mut seen_jars: Vec<PathBuf> = Vec::new();
    let mut jar_path: Option<PathBuf> = None;
    for candidate in jar_candidates {
        if !candidate.exists() {
            continue;
        }
        seen_jars.push(candidate.clone());
        if is_boot_executable_jar(&candidate) {
            jar_path = Some(candidate);
            break;
        }
    }
    let jar_path = jar_path.ok_or_else(|| {
        if seen_jars.is_empty() {
            anyhow!("embedded backend jar not found")
        } else {
            let candidates = seen_jars
                .iter()
                .map(|p| p.display().to_string())
                .collect::<Vec<_>>()
                .join("; ");
            anyhow!(
                "embedded backend jar found but invalid (missing BOOT-INF or JarLauncher): {candidates}"
            )
        }
    })?;

    Ok((java_bin, jar_path))
}

fn is_boot_executable_jar(path: &Path) -> bool {
    let Ok(content) = fs::read(path) else {
        return false;
    };
    let has_boot_inf = content
        .windows("BOOT-INF/".len())
        .any(|window| window == b"BOOT-INF/");
    let has_launcher = content
        .windows("org/springframework/boot/loader/launch/JarLauncher.class".len())
        .any(|window| window == b"org/springframework/boot/loader/launch/JarLauncher.class");
    has_boot_inf && has_launcher
}

#[cfg(windows)]
fn windows_compatible_path(path: &Path) -> PathBuf {
    let raw = path.as_os_str().to_string_lossy();
    if let Some(stripped) = raw.strip_prefix(r"\\?\UNC\") {
        return PathBuf::from(format!(r"\\{}", stripped));
    }
    if let Some(stripped) = raw.strip_prefix(r"\\?\") {
        return PathBuf::from(stripped);
    }
    path.to_path_buf()
}

fn wait_backend_ready(port: u16) -> Result<()> {
    let timeout_seconds = env_u64("NOMOCLAW_BACKEND_START_TIMEOUT_SECONDS", 30);
    let interval_ms = env_u64("NOMOCLAW_BACKEND_HEALTH_INTERVAL_MS", 500);

    let timeout = Duration::from_secs(timeout_seconds);
    let interval = Duration::from_millis(interval_ms.max(100));
    let started = Instant::now();
    let health_url = format!("http://127.0.0.1:{port}/api/system/config");
    let client = Client::builder().timeout(Duration::from_secs(3)).build()?;

    loop {
        if let Ok(response) = client.get(&health_url).send() {
            if response.status().is_success() {
                return Ok(());
            }
        }

        if started.elapsed() >= timeout {
            return Err(anyhow!(
                "backend readiness timeout after {}s, endpoint={health_url}",
                timeout_seconds
            ));
        }

        thread::sleep(interval);
    }
}

fn resolve_port(preferred_port: u16) -> Result<u16> {
    if is_port_available(preferred_port) {
        return Ok(preferred_port);
    }
    let listener = TcpListener::bind(("127.0.0.1", 0)).context("failed to allocate fallback backend port")?;
    let port = listener
        .local_addr()
        .context("failed to read fallback backend port")?
        .port();
    drop(listener);
    Ok(port)
}

fn is_port_available(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

fn ensure_log_file_path<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    let logs_dir = runtime_state_dir(app)?.join("logs");
    fs::create_dir_all(&logs_dir)
        .with_context(|| format!("failed to create logs directory: {}", logs_dir.display()))?;
    Ok(logs_dir.join(current_backend_log_file_name()))
}

fn current_backend_log_file_name() -> String {
    format!("backend-{}.log", Local::now().format("%Y%m%d"))
}

fn nomoclaw_root_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    if let Ok(configured) = env::var("NOMOCLAW_ROOT_DIR") {
        let trimmed = configured.trim();
        if !trimmed.is_empty() {
            return Ok(PathBuf::from(trimmed));
        }
    }
    let home_dir = resolve_home_dir(app)?;
    Ok(home_dir.join(".nomoclaw"))
}

fn resolve_home_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    if let Some(home) = env::var_os("HOME") {
        let path = PathBuf::from(home);
        if !path.as_os_str().is_empty() {
            return Ok(path);
        }
    }
    #[cfg(windows)]
    {
        if let Some(user_profile) = env::var_os("USERPROFILE") {
            let path = PathBuf::from(user_profile);
            if !path.as_os_str().is_empty() {
                return Ok(path);
            }
        }
        let home_drive = env::var_os("HOMEDRIVE");
        let home_path = env::var_os("HOMEPATH");
        if let (Some(drive), Some(path)) = (home_drive, home_path) {
            let mut combined = PathBuf::from(drive);
            combined.push(path);
            if !combined.as_os_str().is_empty() {
                return Ok(combined);
            }
        }
    }
    app.path()
        .home_dir()
        .context("failed to resolve home directory")
}

fn runtime_state_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    let dir = nomoclaw_root_dir(app)?.join("runtime");
    fs::create_dir_all(&dir)
        .with_context(|| format!("failed to create runtime state dir: {}", dir.display()))?;
    Ok(dir)
}

fn backend_url(port: u16) -> String {
    let cache_buster = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0);
    format!("http://127.0.0.1:{port}/nomoclaw/?v={cache_buster}#/")
}

fn env_u16(key: &str, default_value: u16) -> u16 {
    env::var(key)
        .ok()
        .and_then(|raw| raw.parse::<u16>().ok())
        .unwrap_or(default_value)
}

fn env_u32(key: &str, default_value: u32) -> u32 {
    env::var(key)
        .ok()
        .and_then(|value| value.trim().parse::<u32>().ok())
        .unwrap_or(default_value)
}

fn env_u64(key: &str, default_value: u64) -> u64 {
    env::var(key)
        .ok()
        .and_then(|raw| raw.parse::<u64>().ok())
        .unwrap_or(default_value)
}

struct BackendStartupGuard;

impl BackendStartupGuard {
    fn enter() -> Self {
        BACKEND_STARTING.store(true, Ordering::SeqCst);
        BACKEND_WATCH_FAIL_STREAK.store(0, Ordering::SeqCst);
        Self
    }
}

impl Drop for BackendStartupGuard {
    fn drop(&mut self) {
        BACKEND_STARTING.store(false, Ordering::SeqCst);
    }
}

#[cfg(test)]
mod tests {
    use super::evaluate_watchdog_health_debounce;

    #[test]
    fn watchdog_never_restarts_while_starting() {
        let (should_restart, next_failures) = evaluate_watchdog_health_debounce(true, false, false, 2, 3);
        assert!(!should_restart);
        assert_eq!(next_failures, 0);
    }

    #[test]
    fn watchdog_single_failure_does_not_restart() {
        let (should_restart, next_failures) = evaluate_watchdog_health_debounce(false, false, false, 0, 3);
        assert!(!should_restart);
        assert_eq!(next_failures, 1);
    }

    #[test]
    fn watchdog_restarts_after_threshold_failures() {
        let (should_restart, next_failures) = evaluate_watchdog_health_debounce(false, false, false, 2, 3);
        assert!(should_restart);
        assert_eq!(next_failures, 3);
    }

    #[test]
    fn watchdog_resets_failures_after_recovery() {
        let (should_restart, next_failures) = evaluate_watchdog_health_debounce(false, false, true, 2, 3);
        assert!(!should_restart);
        assert_eq!(next_failures, 0);
    }
}

fn is_dev_attach_mode() -> bool {
    if !cfg!(debug_assertions) {
        return false;
    }
    matches!(
        env::var("NOMOCLAW_DEV_ATTACH").ok().as_deref(),
        None | Some("1") | Some("true") | Some("TRUE") | Some("yes") | Some("YES")
    )
}

// ===== single-instance lock + process-group cleanup =====
// TODO(refactor): isolate into lock_and_cleanup.rs with unit tests.
fn acquire_single_instance_lock<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
    let lock_path = runtime_state_dir(app)?.join(INSTANCE_LOCK_FILE);

    let try_create = || -> Result<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&lock_path)
            .with_context(|| format!("failed to create lock file: {}", lock_path.display()))?;
        writeln!(file, "{}", std::process::id())
            .with_context(|| format!("failed to write lock file: {}", lock_path.display()))?;
        Ok(())
    };

    match try_create() {
        Ok(_) => Ok(()),
        Err(_) => {
            #[cfg(unix)]
            if let Ok(raw) = fs::read_to_string(&lock_path) {
                if let Ok(pid) = raw.trim().parse::<i32>() {
                    if process_exists(pid) {
                        return Err(anyhow!("another NomoClaw instance is already running (pid={pid})"));
                    }
                }
            }
            let _ = fs::remove_file(&lock_path);
            try_create()
        }
    }
}

fn release_single_instance_lock<R: Runtime>(app: &AppHandle<R>) {
    if let Ok(lock_path) = runtime_state_dir(app).map(|dir| dir.join(INSTANCE_LOCK_FILE)) {
        let _ = fs::remove_file(lock_path);
    }
}

#[cfg(unix)]
fn backend_pgid_file_path<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    Ok(runtime_state_dir(app)?.join(BACKEND_PGID_FILE))
}

#[cfg(unix)]
fn persist_backend_pgid<R: Runtime>(app: &AppHandle<R>, pgid: i32) {
    if pgid <= 0 {
        return;
    }
    if let Ok(path) = backend_pgid_file_path(app) {
        let _ = fs::write(path, pgid.to_string());
    }
}

#[cfg(unix)]
fn read_backend_pgid<R: Runtime>(app: &AppHandle<R>) -> Option<i32> {
    let path = backend_pgid_file_path(app).ok()?;
    let raw = fs::read_to_string(path).ok()?;
    raw.trim().parse::<i32>().ok().filter(|pgid| *pgid > 0)
}

#[cfg(unix)]
fn clear_backend_pgid_file<R: Runtime>(app: &AppHandle<R>) {
    if let Ok(path) = backend_pgid_file_path(app) {
        let _ = fs::remove_file(path);
    }
}

#[cfg(unix)]
fn cleanup_stale_backend_group<R: Runtime>(app: &AppHandle<R>) {
    if let Some(pgid) = read_backend_pgid(app) {
        terminate_process_group(pgid);
        let _ = wait_process_group_exit(pgid, Duration::from_secs(5));
    }
    clear_backend_pgid_file(app);
}

#[cfg(not(unix))]
fn cleanup_stale_backend_group<R: Runtime>(_app: &AppHandle<R>) {}

#[cfg(unix)]
fn cleanup_known_orphan_java_processes() {
    for pattern in [
        "/Applications/NomoClaw.app/Contents/Resources/resources/backend/nomoclaw.jar",
        "resources/backend/nomoclaw.jar",
        "backend/nomoclaw.jar",
    ] {
        if let Ok(output) = Command::new("/usr/bin/pgrep").arg("-f").arg(pattern).output() {
            if output.status.success() {
                let raw = String::from_utf8_lossy(&output.stdout);
                for line in raw.lines() {
                    if let Ok(pid) = line.trim().parse::<i32>() {
                        if pid > 0 {
                            terminate_pid_force(pid);
                        }
                    }
                }
            }
        }
    }
}

#[cfg(not(unix))]
fn cleanup_known_orphan_java_processes() {}

fn terminate_child_gracefully(child: &mut Child) -> Result<()> {
    #[cfg(unix)]
    {
        use nix::sys::signal::{kill, Signal};
        use nix::unistd::Pid;
        let pid = Pid::from_raw(child.id() as i32);
        let _ = kill(pid, Signal::SIGTERM);

        let deadline = Instant::now() + Duration::from_secs(2);
        loop {
            if child.try_wait()?.is_some() {
                return Ok(());
            }
            if Instant::now() >= deadline {
                break;
            }
            thread::sleep(Duration::from_millis(120));
        }
    }

    let _ = child.kill();
    let _ = child.wait();
    Ok(())
}

#[cfg(unix)]
fn terminate_process_group(process_group_id: i32) {
    use nix::sys::signal::{killpg, Signal};
    use nix::unistd::Pid;

    if process_group_id <= 0 {
        return;
    }
    let pgid = Pid::from_raw(process_group_id);
    let _ = killpg(pgid, Signal::SIGTERM);
    thread::sleep(Duration::from_millis(250));
    if process_group_has_members(process_group_id) {
        let _ = killpg(pgid, Signal::SIGKILL);
    }
}

#[cfg(unix)]
fn terminate_pid_force(pid_raw: i32) {
    use nix::sys::signal::{kill, Signal};
    use nix::unistd::Pid;
    if pid_raw <= 0 {
        return;
    }
    let pid = Pid::from_raw(pid_raw);
    let _ = kill(pid, Signal::SIGTERM);
    thread::sleep(Duration::from_millis(200));
    let _ = kill(pid, Signal::SIGKILL);
}

#[cfg(unix)]
fn wait_process_group_exit(process_group_id: i32, timeout: Duration) -> bool {
    let started = Instant::now();
    while started.elapsed() < timeout {
        if !process_group_has_members(process_group_id) {
            return true;
        }
        thread::sleep(Duration::from_millis(120));
    }
    !process_group_has_members(process_group_id)
}

#[cfg(unix)]
fn process_group_has_members(process_group_id: i32) -> bool {
    if process_group_id <= 0 {
        return false;
    }
    let output = Command::new("/usr/bin/pgrep")
        .arg("-g")
        .arg(process_group_id.to_string())
        .output();

    match output {
        Ok(result) => result.status.success(),
        Err(_) => false,
    }
}

#[cfg(unix)]
fn process_exists(pid_raw: i32) -> bool {
    use nix::sys::signal::kill;
    use nix::unistd::Pid;
    if pid_raw <= 0 {
        return false;
    }
    kill(Pid::from_raw(pid_raw), None).is_ok()
}
