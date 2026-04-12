#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use anyhow::{anyhow, Context, Result};
use reqwest::blocking::Client;
use std::env;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::net::TcpListener;
#[cfg(unix)]
use std::os::unix::process::CommandExt;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, RunEvent, Runtime, WindowEvent};

const WINDOW_LABEL: &str = "main";
const TRAY_MENU_SHOW: &str = "show_main";
const TRAY_MENU_RESTART: &str = "restart_backend";
const TRAY_MENU_QUIT: &str = "quit_app";

const INSTANCE_LOCK_FILE: &str = "app.lock";
const BACKEND_PGID_FILE: &str = "backend.pgid";

static EXITING: AtomicBool = AtomicBool::new(false);
static BACKEND_RESTARTING: AtomicBool = AtomicBool::new(false);

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

fn main() {
    let builder = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            app.manage(DesktopState::default());

            acquire_single_instance_lock(app.handle())?;
            cleanup_known_orphan_java_processes();
            cleanup_stale_backend_group(app.handle());

            register_tray(app)?;
            install_window_behavior(app)?;
            show_loading_window(app)?;
            if let Err(error) = bootstrap_backend(app.handle().clone()) {
                eprintln!("backend bootstrap failed: {error:#}");
                show_bootstrap_error_page(app, &format!("{error:#}"))?;
            }
            install_backend_watchdog(app)?;
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
            RunEvent::ExitRequested { api, .. } => {
                api.prevent_exit();
                shutdown_and_exit(app_handle);
            }
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
    let _ = stop_backend(app);
    release_single_instance_lock(app);
    app.exit(0);
}

fn bootstrap_backend<R: Runtime>(app: AppHandle<R>) -> Result<()> {
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

    wait_backend_ready(runtime.port)?;
    update_window_url(&app, &backend_url(runtime.port))?;

    let state = app.state::<DesktopState>();
    let mut guard = state
        .runtime
        .lock()
        .map_err(|_| anyhow!("failed to lock runtime state"))?;
    *guard = Some(runtime);

    Ok(())
}

fn register_tray<R: Runtime>(app: &tauri::App<R>) -> Result<()> {
    let show_item = MenuItem::with_id(app, TRAY_MENU_SHOW, "显示主窗口", true, None::<&str>)?;
    let restart_item = MenuItem::with_id(app, TRAY_MENU_RESTART, "重启后端", true, None::<&str>)?;
    let quit_item = MenuItem::with_id(app, TRAY_MENU_QUIT, "退出", true, None::<&str>)?;

    let menu = Menu::with_items(app, &[&show_item, &restart_item, &quit_item])?;

    TrayIconBuilder::new()
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            TRAY_MENU_SHOW => {
                let _ = show_main_window(app);
            }
            TRAY_MENU_RESTART => {
                let app_handle = app.clone();
                request_backend_restart(app_handle, "tray menu");
            }
            TRAY_MENU_QUIT => shutdown_and_exit(app),
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
    wait_backend_ready(runtime.port)?;
    update_window_url(app, &backend_url(runtime.port))?;

    let mut guard = state
        .runtime
        .lock()
        .map_err(|_| anyhow!("failed to lock runtime state"))?;
    *guard = Some(runtime);

    Ok(())
}

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
            if backend_needs_restart(&app_handle) {
                request_backend_restart(app_handle.clone(), "watchdog");
            }
        })
        .context("failed to spawn backend watchdog thread")?;
    Ok(())
}

fn backend_needs_restart<R: Runtime>(app: &AppHandle<R>) -> bool {
    let port = {
        let state = app.state::<DesktopState>();
        let mut guard = match state.runtime.lock() {
            Ok(guard) => guard,
            Err(_) => return false,
        };

        let Some(runtime) = guard.as_mut() else {
            return false;
        };
        if runtime.mode != BackendMode::Spawn {
            return false;
        }

        if let Some(child) = runtime.child.as_mut() {
            match child.try_wait() {
                Ok(Some(status)) => {
                    eprintln!("backend process exited: {status}");
                    return true;
                }
                Ok(None) => {}
                Err(error) => {
                    eprintln!("failed to query backend process status: {error:#}");
                    return true;
                }
            }
        }

        runtime.port
    };

    !check_backend_ready_once(port, Duration::from_millis(800))
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

fn stop_backend<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
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
    window.show()?;
    window.unminimize()?;
    window.set_focus()?;
    Ok(())
}

fn update_window_url<R: Runtime>(app: &AppHandle<R>, target_url: &str) -> Result<()> {
    let window = app
        .get_webview_window(WINDOW_LABEL)
        .context("missing main window")?;
    let safe_url = target_url.replace('\\', "\\\\").replace('"', "\\\"");
    window.eval(&format!("window.location.replace(\"{safe_url}\");"))?;
    window.show()?;
    Ok(())
}

fn show_bootstrap_error_page<R: Runtime>(app: &tauri::App<R>, raw_error: &str) -> Result<()> {
    let window = app
        .get_webview_window(WINDOW_LABEL)
        .context("missing main window")?;
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

fn start_backend_process<R: Runtime>(app: &AppHandle<R>, preferred_port: u16) -> Result<BackendRuntime> {
    let port = resolve_port(preferred_port)?;
    let (java_bin, jar_path) = resolve_runtime_paths(app)?;
    let log_path = ensure_log_file_path(app)?;
    let parent_pid = std::process::id();

    let log_file = File::options()
        .create(true)
        .append(true)
        .open(&log_path)
        .with_context(|| format!("failed to open log file: {}", log_path.display()))?;
    let log_file_err = log_file
        .try_clone()
        .with_context(|| format!("failed to clone log file: {}", log_path.display()))?;

    let java_bin_quoted = shell_quote(&java_bin.to_string_lossy());
    let jar_path_quoted = shell_quote(&jar_path.to_string_lossy());
    let script = format!(
        "{java} \
        -Dspring.profiles.active=h2 \
        -Dnomoclaw.desktop.open-browser-on-startup=false \
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
        resource_dir.join("resources/backend/runtime/bin/java"),
        resource_dir.join("runtime/bin/java"),
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
    let jar_path = jar_candidates
        .iter()
        .find(|path| path.exists())
        .cloned()
        .ok_or_else(|| anyhow!("embedded backend jar not found"))?;

    Ok((java_bin, jar_path))
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
    let app_data_dir = app
        .path()
        .app_data_dir()
        .context("failed to resolve app data directory")?;
    let logs_dir = app_data_dir.join("logs");
    fs::create_dir_all(&logs_dir)
        .with_context(|| format!("failed to create logs directory: {}", logs_dir.display()))?;
    Ok(logs_dir.join("backend.log"))
}

fn app_data_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .context("failed to resolve app data directory")?;
    fs::create_dir_all(&dir).with_context(|| format!("failed to create app data dir: {}", dir.display()))?;
    Ok(dir)
}

fn backend_url(port: u16) -> String {
    format!("http://127.0.0.1:{port}/nomoclaw/#/")
}

fn env_u16(key: &str, default_value: u16) -> u16 {
    env::var(key)
        .ok()
        .and_then(|raw| raw.parse::<u16>().ok())
        .unwrap_or(default_value)
}

fn env_u64(key: &str, default_value: u64) -> u64 {
    env::var(key)
        .ok()
        .and_then(|raw| raw.parse::<u64>().ok())
        .unwrap_or(default_value)
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

fn acquire_single_instance_lock<R: Runtime>(app: &AppHandle<R>) -> Result<()> {
    let lock_path = app_data_dir(app)?.join(INSTANCE_LOCK_FILE);

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
            if let Ok(raw) = fs::read_to_string(&lock_path) {
                if let Ok(pid) = raw.trim().parse::<i32>() {
                    #[cfg(unix)]
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
    if let Ok(lock_path) = app_data_dir(app).map(|dir| dir.join(INSTANCE_LOCK_FILE)) {
        let _ = fs::remove_file(lock_path);
    }
}

#[cfg(unix)]
fn backend_pgid_file_path<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    Ok(app_data_dir(app)?.join(BACKEND_PGID_FILE))
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
