<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NCard, NDrawer, NDrawerContent, NForm, NFormItem, NInput, NSwitch, NTag } from "naive-ui";
import { CircleHelp } from "lucide-vue-next";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import UiInstantTooltip from "@/components/UiInstantTooltip.vue";
import { channelApi } from "@/api/channelApi";
import { message } from "@/discrete";
import type { ChannelConfig, ChannelDingTalkBotConfig, ChannelDiscordBotConfig, ChannelFeishuBotConfig, ChannelTelegramBotConfig } from "@/types/api";

type ChannelKey = "feishu" | "dingtalk" | "discord" | "telegram";
const { t } = useI18n();

const loading = ref(false);
const saving = ref(false);
const showEditor = ref(false);
const editingKey = ref<ChannelKey>("feishu");
const config = reactive<ChannelConfig>(createEmptyConfig());
const draft = reactive<ChannelConfig>(createEmptyConfig());
const allowListText = reactive<Record<string, string>>({});

const channelCards = computed(() => [
  {
    key: "feishu" as ChannelKey,
    title: t("channels.cards.feishu.title"),
    subtitle: t("channels.cards.feishu.subtitle"),
    enabled: config.channels.feishu.enabled,
    botCount: config.channels.feishu.bots.length,
    defaultBot: config.channels.feishu.bots.find((item) => item.isDefault)?.displayName || "-"
  },
  {
    key: "dingtalk" as ChannelKey,
    title: t("channels.cards.dingtalk.title"),
    subtitle: t("channels.cards.dingtalk.subtitle"),
    enabled: config.channels.dingtalk.enabled,
    botCount: config.channels.dingtalk.bots.length,
    defaultBot: config.channels.dingtalk.bots.find((item) => item.isDefault)?.displayName || "-"
  },
  {
    key: "discord" as ChannelKey,
    title: t("channels.cards.discord.title"),
    subtitle: t("channels.cards.discord.subtitle"),
    enabled: config.channels.discord.enabled,
    botCount: config.channels.discord.bots.length,
    defaultBot: config.channels.discord.bots.find((item) => item.isDefault)?.displayName || "-"
  },
  {
    key: "telegram" as ChannelKey,
    title: t("channels.cards.telegram.title"),
    subtitle: t("channels.cards.telegram.subtitle"),
    enabled: config.channels.telegram.enabled,
    botCount: config.channels.telegram.bots.length,
    defaultBot: config.channels.telegram.bots.find((item) => item.isDefault)?.displayName || "-"
  }
]);

const editorTitle = computed(() => t(`channels.editor.${editingKey.value}Title`));

const editingFeishuBots = computed(() => draft.channels.feishu.bots);
const editingDingTalkBots = computed(() => draft.channels.dingtalk.bots);
const editingDiscordBots = computed(() => draft.channels.discord.bots);
const editingTelegramBots = computed(() => draft.channels.telegram.bots);

function createDefaultFeishuBot(): ChannelFeishuBotConfig {
  return {
    botId: `feishu_${Date.now()}`,
    displayName: "Feishu Bot",
    enabled: true,
    isDefault: false,
    requireMention: true,
    allowList: [],
    appId: "",
    appSecret: "",
    processingAckReactionEnabled: true,
    processingAckReactionType: "OK",
    defaultTarget: "",
    defaultTargetDisplayName: "",
    targetResolvedAt: ""
  };
}

function createDefaultDingTalkBot(): ChannelDingTalkBotConfig {
  return {
    botId: `dingtalk_${Date.now()}`,
    displayName: "DingTalk Bot",
    enabled: true,
    isDefault: false,
    requireMention: true,
    allowList: [],
    clientId: "",
    clientSecret: "",
    robotCode: ""
  };
}

function createDefaultDiscordBot(): ChannelDiscordBotConfig {
  return {
    botId: `discord_${Date.now()}`,
    displayName: "Discord Bot",
    enabled: true,
    isDefault: false,
    requireMention: true,
    allowList: [],
    token: "",
    botUserId: "",
    acceptBotMessages: false
  };
}

function createDefaultTelegramBot(): ChannelTelegramBotConfig {
  return {
    botId: `telegram_${Date.now()}`,
    displayName: "Telegram Bot",
    enabled: true,
    isDefault: false,
    requireMention: true,
    allowList: [],
    token: "",
    botUsername: ""
  };
}

function createEmptyConfig(): ChannelConfig {
  return {
    channels: {
      feishu: { enabled: false, bots: [createDefaultFeishuBot()] },
      dingtalk: { enabled: false, bots: [createDefaultDingTalkBot()] },
      discord: { enabled: false, bots: [createDefaultDiscordBot()] },
      telegram: { enabled: false, bots: [createDefaultTelegramBot()] }
    }
  };
}

function cloneConfig(source: ChannelConfig): ChannelConfig {
  return JSON.parse(JSON.stringify(source)) as ChannelConfig;
}

function syncAllowListText() {
  for (const bot of config.channels.feishu.bots) {
    allowListText[`feishu:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of config.channels.dingtalk.bots) {
    allowListText[`dingtalk:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of config.channels.discord.bots) {
    allowListText[`discord:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of config.channels.telegram.bots) {
    allowListText[`telegram:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
}

function normalizeAllowList(raw: string): string[] {
  if (!raw.trim()) {
    return [];
  }
  return Array.from(new Set(raw.split(",").map((item) => item.trim()).filter(Boolean)));
}

function extractOpenId(target: string): string {
  const raw = target.trim();
  if (!raw) {
    return "";
  }
  const prefix = "feishu:open_id:";
  if (raw.startsWith(prefix)) {
    return raw.slice(prefix.length);
  }
  const index = raw.lastIndexOf(":");
  return index >= 0 ? raw.slice(index + 1) : raw;
}

function ensureSingleDefault(channel: ChannelKey) {
  const bots =
    channel === "feishu"
      ? draft.channels.feishu.bots
      : channel === "dingtalk"
        ? draft.channels.dingtalk.bots
        : channel === "discord"
          ? draft.channels.discord.bots
          : draft.channels.telegram.bots;
  if (!bots.length) {
    return;
  }
  const firstDefaultIndex = bots.findIndex((item) => item.isDefault);
  const winnerIndex = firstDefaultIndex >= 0 ? firstDefaultIndex : 0;
  bots.forEach((item, index) => {
    item.isDefault = index === winnerIndex;
  });
}

async function loadConfig() {
  loading.value = true;
  try {
    const data = await channelApi.getChannelConfig();
    config.channels.feishu = { enabled: data.channels.feishu.enabled, bots: data.channels.feishu.bots || [] };
    config.channels.dingtalk = { enabled: data.channels.dingtalk.enabled, bots: data.channels.dingtalk.bots || [] };
    config.channels.discord = { enabled: data.channels.discord.enabled, bots: data.channels.discord.bots || [] };
    config.channels.telegram = { enabled: data.channels.telegram.enabled, bots: data.channels.telegram.bots || [] };
    if (!config.channels.feishu.bots.length) {
      config.channels.feishu.bots = [createDefaultFeishuBot()];
    }
    if (!config.channels.dingtalk.bots.length) {
      config.channels.dingtalk.bots = [createDefaultDingTalkBot()];
    }
    if (!config.channels.discord.bots.length) {
      config.channels.discord.bots = [createDefaultDiscordBot()];
    }
    if (!config.channels.telegram.bots.length) {
      config.channels.telegram.bots = [createDefaultTelegramBot()];
    }
    syncAllowListText();
  } finally {
    loading.value = false;
  }
}

async function refreshConfig() {
  try {
    await loadConfig();
    message.success(t("toast.configRefreshed"));
  } catch (error) {
    const text = error instanceof Error ? error.message : t("toast.refreshFailed");
    message.error(text);
  }
}

function openEditor(key: ChannelKey) {
  editingKey.value = key;
  const copied = cloneConfig(config);
  draft.channels.feishu = copied.channels.feishu;
  draft.channels.dingtalk = copied.channels.dingtalk;
  draft.channels.discord = copied.channels.discord;
  draft.channels.telegram = copied.channels.telegram;
  ensureSingleDefault("feishu");
  ensureSingleDefault("dingtalk");
  ensureSingleDefault("discord");
  ensureSingleDefault("telegram");
  for (const bot of draft.channels.feishu.bots) {
    allowListText[`feishu:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of draft.channels.dingtalk.bots) {
    allowListText[`dingtalk:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of draft.channels.discord.bots) {
    allowListText[`discord:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  for (const bot of draft.channels.telegram.bots) {
    allowListText[`telegram:${bot.botId}`] = (bot.allowList || []).join(", ");
  }
  showEditor.value = true;
}

function addBot(channel: ChannelKey) {
  if (channel === "feishu") {
    draft.channels.feishu.bots.push(createDefaultFeishuBot());
  } else if (channel === "dingtalk") {
    draft.channels.dingtalk.bots.push(createDefaultDingTalkBot());
  } else if (channel === "discord") {
    draft.channels.discord.bots.push(createDefaultDiscordBot());
  } else {
    draft.channels.telegram.bots.push(createDefaultTelegramBot());
  }
  ensureSingleDefault(channel);
}

function removeBot(channel: ChannelKey, index: number) {
  const bots =
    channel === "feishu"
      ? draft.channels.feishu.bots
      : channel === "dingtalk"
        ? draft.channels.dingtalk.bots
        : channel === "discord"
          ? draft.channels.discord.bots
          : draft.channels.telegram.bots;
  if (bots.length <= 1) {
    return;
  }
  bots.splice(index, 1);
  ensureSingleDefault(channel);
}

function setDefaultBot(channel: ChannelKey, index: number) {
  const bots =
    channel === "feishu"
      ? draft.channels.feishu.bots
      : channel === "dingtalk"
        ? draft.channels.dingtalk.bots
        : channel === "discord"
          ? draft.channels.discord.bots
          : draft.channels.telegram.bots;
  bots.forEach((item, idx) => {
    item.isDefault = idx === index;
  });
}

function validateChannelDraft() {
  if (editingKey.value === "feishu") {
    const section = draft.channels.feishu;
    if (section.enabled && !section.bots.some((item) => item.enabled)) {
      throw new Error(t("channels.errors.feishuRequired"));
    }
    section.bots.forEach((bot) => {
      if (section.enabled && bot.enabled && (!bot.appId.trim() || !bot.appSecret.trim())) {
        throw new Error(t("channels.errors.feishuRequired"));
      }
    });
    return;
  }
  if (editingKey.value === "dingtalk") {
    const section = draft.channels.dingtalk;
    if (section.enabled && !section.bots.some((item) => item.enabled)) {
      throw new Error(t("channels.errors.dingtalkRequired"));
    }
    section.bots.forEach((bot) => {
      if (section.enabled && bot.enabled && (!bot.clientId.trim() || !bot.clientSecret.trim() || !bot.robotCode.trim())) {
        throw new Error(t("channels.errors.dingtalkRequired"));
      }
    });
    return;
  }
  if (editingKey.value === "discord") {
    const section = draft.channels.discord;
    if (section.enabled && !section.bots.some((item) => item.enabled)) {
      throw new Error(t("channels.errors.discordRequired"));
    }
    section.bots.forEach((bot) => {
      if (section.enabled && bot.enabled && !bot.token.trim()) {
        throw new Error(t("channels.errors.discordRequired"));
      }
    });
    return;
  }
  const section = draft.channels.telegram;
  if (section.enabled && !section.bots.some((item) => item.enabled)) {
    throw new Error(t("channels.errors.telegramRequired"));
  }
  section.bots.forEach((bot) => {
    if (section.enabled && bot.enabled && !bot.token.trim()) {
      throw new Error(t("channels.errors.telegramRequired"));
    }
  });
}

function normalizeDraftBeforeSave() {
  draft.channels.feishu.bots.forEach((bot) => {
    bot.allowList = normalizeAllowList(allowListText[`feishu:${bot.botId}`] || "");
    bot.processingAckReactionEnabled = true;
    bot.processingAckReactionType = "OK";
  });
  draft.channels.dingtalk.bots.forEach((bot) => {
    bot.allowList = normalizeAllowList(allowListText[`dingtalk:${bot.botId}`] || "");
  });
  draft.channels.discord.bots.forEach((bot) => {
    bot.allowList = normalizeAllowList(allowListText[`discord:${bot.botId}`] || "");
  });
  draft.channels.telegram.bots.forEach((bot) => {
    bot.allowList = normalizeAllowList(allowListText[`telegram:${bot.botId}`] || "");
    bot.botUsername = bot.botUsername.replace(/^@/, "").trim();
  });
  ensureSingleDefault("feishu");
  ensureSingleDefault("dingtalk");
  ensureSingleDefault("discord");
  ensureSingleDefault("telegram");
}

async function saveEditor() {
  saving.value = true;
  try {
    normalizeDraftBeforeSave();
    validateChannelDraft();
    const saved = await channelApi.updateChannelConfig(cloneConfig(draft));
    config.channels.feishu = { ...saved.channels.feishu };
    config.channels.dingtalk = { ...saved.channels.dingtalk };
    config.channels.discord = { ...saved.channels.discord };
    config.channels.telegram = { ...saved.channels.telegram };
    syncAllowListText();
    showEditor.value = false;
    message.success(t("channels.toast.saved"));
  } catch (error) {
    const text = error instanceof Error ? error.message : t("toast.saveFailed");
    message.error(text);
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void loadConfig();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content channels-page">
          <AppPageHeader :title="t('pages.channels.title')">
            <template #subtitle>
              {{ t("pages.channels.subtitle") }}
            </template>
            <template #actions>
              <n-button :loading="loading" @click="refreshConfig">{{ t("common.refresh") }}</n-button>
            </template>
          </AppPageHeader>

          <section class="channel-grid">
            <n-card
              v-for="item in channelCards"
              :key="item.key"
              class="channel-card"
              hoverable
              @click="openEditor(item.key)"
            >
              <template #header>
                <div class="ui-card-head-between">
                  <span>{{ item.title }}</span>
                  <n-tag :type="item.enabled ? 'success' : 'warning'" round>{{ item.enabled ? t("common.enabled") : t("common.disabled") }}</n-tag>
                </div>
              </template>
              <div class="card-subtitle">{{ item.subtitle }}</div>
              <div class="card-row"><span class="meta-label">{{ t("channels.cards.summary") }}</span><span>{{ t("channels.editor.botCount", { count: item.botCount }) }}</span></div>
              <div class="card-row"><span class="meta-label">{{ t("channels.editor.defaultBot") }}</span><span>{{ item.defaultBot }}</span></div>
              <div class="card-foot">
                <n-button size="small" tertiary type="primary">{{ t("common.edit") }}</n-button>
              </div>
            </n-card>
          </section>

        </div>
      </main>
    </div>
  </div>

  <n-drawer v-model:show="showEditor" :width="700" placement="right">
    <n-drawer-content :title="editorTitle" closable>
      <n-form v-if="editingKey === 'feishu'" label-placement="top" class="channel-edit-form">
        <div class="editor-toolbar">
          <div class="channel-switch-row">
            <span class="meta-label">{{ t("channels.editor.enable") }}</span>
            <n-switch v-model:value="draft.channels.feishu.enabled" />
          </div>
          <n-button tertiary type="primary" @click="addBot('feishu')">{{ t("channels.editor.addBot") }}</n-button>
        </div>
        <div v-for="(bot, idx) in editingFeishuBots" :key="`feishu-${bot.botId}-${idx}`" class="bot-card">
          <div class="bot-head">
            <div class="bot-title">
              <div class="bot-name">{{ bot.displayName || bot.botId }}</div>
              <div class="bot-id-sub">{{ bot.botId }}</div>
            </div>
            <div class="bot-head-actions">
              <n-button size="small" tertiary :type="bot.isDefault ? 'success' : 'default'" @click="setDefaultBot('feishu', idx)">
                {{ bot.isDefault ? t("channels.editor.defaultBot") : t("channels.editor.setDefault") }}
              </n-button>
              <n-button size="small" tertiary type="error" :disabled="editingFeishuBots.length <= 1" @click="removeBot('feishu', idx)">
                {{ t("common.delete") }}
              </n-button>
            </div>
          </div>
          <div class="bot-switch-row">
            <span class="meta-label">{{ t("common.enabled") }}</span>
            <n-switch v-model:value="bot.enabled" />
          </div>
          <div class="bot-field-grid">
            <n-form-item :label="t('channels.editor.botName')">
              <n-input v-model:value="bot.displayName" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.feishuAppId')">
              <n-input v-model:value="bot.appId" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.feishuAppSecret')">
              <n-input v-model:value="bot.appSecret" type="password" show-password-on="click" />
            </n-form-item>
          </div>
          <n-form-item class="bot-full-row">
            <template #label>
              <span class="field-label-with-tip">
                <span>{{ t("channels.editor.allowList") }}</span>
                <UiInstantTooltip :content="t('channels.editor.allowListTip')" placement="top">
                  <CircleHelp class="field-tip-icon" :size="15" />
                </UiInstantTooltip>
              </span>
            </template>
            <n-input v-model:value="allowListText[`feishu:${bot.botId}`]" />
          </n-form-item>
          <div class="bot-target-status">
            <span class="meta-label">{{ t("channels.editor.openIdLabel") }}</span>
            <n-tag v-if="bot.defaultTarget" size="small" type="success" round>
              {{ extractOpenId(bot.defaultTarget) }}
            </n-tag>
            <n-tag v-else size="small" type="warning" round>
              {{ t("channels.editor.openIdMissing") }}
            </n-tag>
          </div>
        </div>
      </n-form>

      <n-form v-else-if="editingKey === 'dingtalk'" label-placement="top" class="channel-edit-form">
        <div class="editor-toolbar">
          <div class="channel-switch-row">
            <span class="meta-label">{{ t("channels.editor.enableDingtalk") }}</span>
            <n-switch v-model:value="draft.channels.dingtalk.enabled" />
          </div>
          <n-button tertiary type="primary" @click="addBot('dingtalk')">{{ t("channels.editor.addBot") }}</n-button>
        </div>
        <div v-for="(bot, idx) in editingDingTalkBots" :key="`dingtalk-${bot.botId}-${idx}`" class="bot-card">
          <div class="bot-head">
            <div class="bot-title">
              <div class="bot-name">{{ bot.displayName || bot.botId }}</div>
              <div class="bot-id-sub">{{ bot.botId }}</div>
            </div>
            <div class="bot-head-actions">
              <n-button size="small" tertiary :type="bot.isDefault ? 'success' : 'default'" @click="setDefaultBot('dingtalk', idx)">
                {{ bot.isDefault ? t("channels.editor.defaultBot") : t("channels.editor.setDefault") }}
              </n-button>
              <n-button size="small" tertiary type="error" :disabled="editingDingTalkBots.length <= 1" @click="removeBot('dingtalk', idx)">
                {{ t("common.delete") }}
              </n-button>
            </div>
          </div>
          <div class="bot-switch-row">
            <span class="meta-label">{{ t("common.enabled") }}</span>
            <n-switch v-model:value="bot.enabled" />
          </div>
          <div class="bot-field-grid">
            <n-form-item :label="t('channels.editor.botName')">
              <n-input v-model:value="bot.displayName" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.dingtalkClientId')">
              <n-input v-model:value="bot.clientId" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.dingtalkClientSecret')">
              <n-input v-model:value="bot.clientSecret" type="password" show-password-on="click" />
            </n-form-item>
          </div>
          <n-form-item :label="t('channels.editor.dingtalkRobotCode')">
            <n-input v-model:value="bot.robotCode" />
          </n-form-item>
          <n-form-item class="bot-full-row">
            <template #label>
              <span class="field-label-with-tip">
                <span>{{ t("channels.editor.allowList") }}</span>
                <UiInstantTooltip :content="t('channels.editor.allowListTip')" placement="top">
                  <CircleHelp class="field-tip-icon" :size="15" />
                </UiInstantTooltip>
              </span>
            </template>
            <n-input v-model:value="allowListText[`dingtalk:${bot.botId}`]" />
          </n-form-item>
        </div>
      </n-form>

      <n-form v-else-if="editingKey === 'discord'" label-placement="top" class="channel-edit-form">
        <div class="editor-toolbar">
          <div class="channel-switch-row">
            <span class="meta-label">{{ t("channels.editor.enableDiscord") }}</span>
            <n-switch v-model:value="draft.channels.discord.enabled" />
          </div>
          <n-button tertiary type="primary" @click="addBot('discord')">{{ t("channels.editor.addBot") }}</n-button>
        </div>
        <div v-for="(bot, idx) in editingDiscordBots" :key="`discord-${bot.botId}-${idx}`" class="bot-card">
          <div class="bot-head">
            <div class="bot-title">
              <div class="bot-name">{{ bot.displayName || bot.botId }}</div>
              <div class="bot-id-sub">{{ bot.botId }}</div>
            </div>
            <div class="bot-head-actions">
              <n-button size="small" tertiary :type="bot.isDefault ? 'success' : 'default'" @click="setDefaultBot('discord', idx)">
                {{ bot.isDefault ? t("channels.editor.defaultBot") : t("channels.editor.setDefault") }}
              </n-button>
              <n-button size="small" tertiary type="error" :disabled="editingDiscordBots.length <= 1" @click="removeBot('discord', idx)">
                {{ t("common.delete") }}
              </n-button>
            </div>
          </div>
          <div class="bot-switch-row">
            <span class="meta-label">{{ t("common.enabled") }}</span>
            <n-switch v-model:value="bot.enabled" />
          </div>
          <div class="bot-field-grid">
            <n-form-item :label="t('channels.editor.botName')">
              <n-input v-model:value="bot.displayName" />
            </n-form-item>
            <n-form-item>
              <template #label>
                <span class="field-label-with-tip">
                  <span>{{ t("channels.editor.discordBotUserId") }}</span>
                  <UiInstantTooltip :content="t('channels.editor.discordBotUserIdTip')" placement="top">
                    <CircleHelp class="field-tip-icon" :size="15" />
                  </UiInstantTooltip>
                </span>
              </template>
              <n-input v-model:value="bot.botUserId" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.discordToken')">
              <n-input v-model:value="bot.token" type="password" show-password-on="click" />
            </n-form-item>
          </div>
          <n-form-item class="bot-full-row">
            <template #label>
              <span class="field-label-with-tip">
                <span>{{ t("channels.editor.allowList") }}</span>
                <UiInstantTooltip :content="t('channels.editor.allowListTip')" placement="top">
                  <CircleHelp class="field-tip-icon" :size="15" />
                </UiInstantTooltip>
              </span>
            </template>
            <n-input v-model:value="allowListText[`discord:${bot.botId}`]" />
          </n-form-item>
          <div class="bot-switch-row">
            <span class="meta-label">{{ t("channels.editor.acceptBotMessages") }}</span>
            <n-switch v-model:value="bot.acceptBotMessages" />
          </div>
        </div>
      </n-form>

      <n-form v-else label-placement="top" class="channel-edit-form">
        <div class="editor-toolbar">
          <div class="channel-switch-row">
            <span class="meta-label">{{ t("channels.editor.enableTelegram") }}</span>
            <n-switch v-model:value="draft.channels.telegram.enabled" />
          </div>
          <n-button tertiary type="primary" @click="addBot('telegram')">{{ t("channels.editor.addBot") }}</n-button>
        </div>
        <div v-for="(bot, idx) in editingTelegramBots" :key="`telegram-${bot.botId}-${idx}`" class="bot-card">
          <div class="bot-head">
            <div class="bot-title">
              <div class="bot-name">{{ bot.displayName || bot.botId }}</div>
              <div class="bot-id-sub">{{ bot.botId }}</div>
            </div>
            <div class="bot-head-actions">
              <n-button size="small" tertiary :type="bot.isDefault ? 'success' : 'default'" @click="setDefaultBot('telegram', idx)">
                {{ bot.isDefault ? t("channels.editor.defaultBot") : t("channels.editor.setDefault") }}
              </n-button>
              <n-button size="small" tertiary type="error" :disabled="editingTelegramBots.length <= 1" @click="removeBot('telegram', idx)">
                {{ t("common.delete") }}
              </n-button>
            </div>
          </div>
          <div class="bot-switch-row">
            <span class="meta-label">{{ t("common.enabled") }}</span>
            <n-switch v-model:value="bot.enabled" />
          </div>
          <div class="bot-field-grid">
            <n-form-item :label="t('channels.editor.botName')">
              <n-input v-model:value="bot.displayName" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.telegramUsername')">
              <n-input v-model:value="bot.botUsername" />
            </n-form-item>
            <n-form-item :label="t('channels.editor.telegramToken')">
              <n-input v-model:value="bot.token" type="password" show-password-on="click" />
            </n-form-item>
          </div>
          <n-form-item class="bot-full-row">
            <template #label>
              <span class="field-label-with-tip">
                <span>{{ t("channels.editor.allowList") }}</span>
                <UiInstantTooltip :content="t('channels.editor.allowListTip')" placement="top">
                  <CircleHelp class="field-tip-icon" :size="15" />
                </UiInstantTooltip>
              </span>
            </template>
            <n-input v-model:value="allowListText[`telegram:${bot.botId}`]" />
          </n-form-item>
        </div>
      </n-form>

      <template #footer>
        <div class="ui-actions-end">
          <n-button @click="showEditor = false">{{ t("common.cancel") }}</n-button>
          <n-button type="primary" :loading="saving" @click="saveEditor">{{ t("common.save") }}</n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.channels-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: var(--space-4);
  align-items: stretch;
}

.channel-card {
  cursor: pointer;
  min-height: 220px;
  border-radius: var(--radius-xl);
}

.card-subtitle {
  color: var(--color-text-tertiary);
  margin-bottom: var(--space-3);
  min-height: 44px;
}

.card-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
}

.card-row span:last-child {
  text-align: right;
  max-width: 62%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-foot {
  margin-top: var(--space-3);
  display: flex;
  justify-content: flex-end;
}

.channel-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.bot-card {
  padding: var(--space-4);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-4);
  background: color-mix(in srgb, var(--color-bg-surface) 90%, var(--color-bg-surface-soft));
}

.bot-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3_5);
}

.bot-head-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.bot-name {
  font-size: var(--text-body-size);
  font-weight: 600;
}

.bot-id-sub {
  margin-top: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--text-caption-size);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.bot-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3_5);
  padding: var(--space-2_5) 0;
  border-top: var(--size-1) solid var(--color-border-soft);
  border-bottom: var(--size-1) solid var(--color-border-soft);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.bot-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.bot-full-row {
  margin-top: var(--space-1);
}

.bot-target-status {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.field-label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
}

.field-tip-icon {
  color: var(--color-text-tertiary);
  cursor: help;
}

.field-tip-icon:hover {
  color: var(--color-text-secondary);
}

.channel-edit-form :deep(.n-form-item) {
  margin-bottom: var(--space-2_5);
}

.bot-field-grid :deep(.n-form-item) {
  margin-bottom: 0;
}

@media (max-width: 640px) {
  .channel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 920px) {
  .editor-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .bot-head {
    flex-direction: column;
    align-items: stretch;
  }

  .bot-head-actions {
    justify-content: flex-start;
  }

  .bot-field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
