<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { NButton, NCard, NDrawer, NDrawerContent, NForm, NFormItem, NInput, NSwitch, NTag } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { channelApi } from "@/api/channelApi";
import { message } from "@/discrete";
import type { ChannelConfig } from "@/types/api";

type ChannelKey = "feishu" | "dingtalk";

const loading = ref(false);
const saving = ref(false);
const showEditor = ref(false);
const editingKey = ref<ChannelKey>("feishu");
const config = reactive<ChannelConfig>({
  channels: {
    feishu: {
      enabled: false,
      requireMention: true,
      allowList: [],
      appId: "",
      appSecret: "",
      processingAckReactionEnabled: true,
      processingAckReactionType: "OK"
    },
    dingtalk: {
      enabled: false,
      requireMention: true,
      allowList: [],
      clientId: "",
      clientSecret: "",
      robotCode: ""
    }
  }
});
const draft = reactive<ChannelConfig>({
  channels: {
    feishu: {
      enabled: false,
      requireMention: true,
      allowList: [],
      appId: "",
      appSecret: "",
      processingAckReactionEnabled: true,
      processingAckReactionType: "OK"
    },
    dingtalk: {
      enabled: false,
      requireMention: true,
      allowList: [],
      clientId: "",
      clientSecret: "",
      robotCode: ""
    }
  }
});
const allowListText = reactive({ feishu: "", dingtalk: "" });

const channelCards = computed(() => [
  {
    key: "feishu" as ChannelKey,
    title: "飞书 Channel",
    subtitle: "长连接接收消息，支持 Reaction ACK",
    enabled: config.channels.feishu.enabled,
    mention: config.channels.feishu.requireMention,
    summary: config.channels.feishu.appId ? `App ID: ${config.channels.feishu.appId}` : "未配置 App ID"
  },
  {
    key: "dingtalk" as ChannelKey,
    title: "钉钉 Channel",
    subtitle: "Stream SDK 接收消息，支持会话 webhook 回复",
    enabled: config.channels.dingtalk.enabled,
    mention: config.channels.dingtalk.requireMention,
    summary: config.channels.dingtalk.robotCode ? `Robot Code: ${config.channels.dingtalk.robotCode}` : "未配置 Robot Code"
  }
]);

const editorTitle = computed(() => (editingKey.value === "feishu" ? "编辑飞书通道" : "编辑钉钉通道"));

function normalizeAllowList(raw: string): string[] {
  if (!raw.trim()) {
    return [];
  }
  return Array.from(new Set(raw.split(",").map((item) => item.trim()).filter(Boolean)));
}

function syncAllowListText() {
  allowListText.feishu = config.channels.feishu.allowList.join(", ");
  allowListText.dingtalk = config.channels.dingtalk.allowList.join(", ");
}

function cloneConfig(source: ChannelConfig): ChannelConfig {
  return JSON.parse(JSON.stringify(source)) as ChannelConfig;
}

async function loadConfig() {
  loading.value = true;
  try {
    const data = await channelApi.getChannelConfig();
    config.channels.feishu = { ...data.channels.feishu, allowList: data.channels.feishu.allowList ?? [] };
    config.channels.dingtalk = { ...data.channels.dingtalk, allowList: data.channels.dingtalk.allowList ?? [] };
    syncAllowListText();
  } finally {
    loading.value = false;
  }
}

async function refreshConfig() {
  try {
    await loadConfig();
    message.success("配置已刷新");
  } catch (error) {
    const text = error instanceof Error ? error.message : "刷新失败";
    message.error(text);
  }
}

function openEditor(key: ChannelKey) {
  editingKey.value = key;
  const copied = cloneConfig(config);
  draft.channels.feishu = copied.channels.feishu;
  draft.channels.dingtalk = copied.channels.dingtalk;
  // Feishu defaults: keep mention + reaction ack enabled and hidden from UI.
  draft.channels.feishu.requireMention = true;
  draft.channels.feishu.processingAckReactionEnabled = true;
  draft.channels.feishu.processingAckReactionType = "OK";
  allowListText.feishu = draft.channels.feishu.allowList.join(", ");
  allowListText.dingtalk = draft.channels.dingtalk.allowList.join(", ");
  showEditor.value = true;
}

function validateChannelDraft() {
  if (editingKey.value === "feishu") {
    const feishu = draft.channels.feishu;
    if (feishu.enabled && (!feishu.appId.trim() || !feishu.appSecret.trim())) {
      throw new Error("飞书启用时必须填写 appId 和 appSecret");
    }
    return;
  }
  const dingtalk = draft.channels.dingtalk;
  if (dingtalk.enabled && (!dingtalk.clientId.trim() || !dingtalk.clientSecret.trim() || !dingtalk.robotCode.trim())) {
    throw new Error("钉钉启用时必须填写 clientId、clientSecret、robotCode");
  }
}

async function saveEditor() {
  saving.value = true;
  try {
    // Feishu defaults: keep mention + reaction ack enabled and hidden from UI.
    draft.channels.feishu.requireMention = true;
    draft.channels.feishu.processingAckReactionEnabled = true;
    draft.channels.feishu.processingAckReactionType = "OK";
    draft.channels.feishu.allowList = normalizeAllowList(allowListText.feishu);
    draft.channels.dingtalk.allowList = normalizeAllowList(allowListText.dingtalk);
    validateChannelDraft();
    const saved = await channelApi.updateChannelConfig(cloneConfig(draft));
    config.channels.feishu = { ...saved.channels.feishu };
    config.channels.dingtalk = { ...saved.channels.dingtalk };
    syncAllowListText();
    showEditor.value = false;
    message.success("Channel 配置已保存");
  } catch (error) {
    const text = error instanceof Error ? error.message : "保存失败";
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
          <AppPageHeader title="Channel 管理">
            <template #subtitle>
              点击卡片编辑通道信息，保存后写入 <code>~/.nomoclaw/nomoclaw.json</code>。
            </template>
            <template #actions>
              <n-button :loading="loading" @click="refreshConfig">刷新配置</n-button>
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
                  <n-tag :type="item.enabled ? 'success' : 'warning'" round>{{ item.enabled ? "已启用" : "已停用" }}</n-tag>
                </div>
              </template>
              <div class="card-subtitle">{{ item.subtitle }}</div>
              <div class="card-row"><span class="meta-label">@响应策略</span><span>{{ item.mention ? "仅 @ 触发" : "所有消息" }}</span></div>
              <div class="card-row"><span class="meta-label">配置摘要</span><span>{{ item.summary }}</span></div>
              <div class="card-foot">
                <n-button size="small" tertiary type="primary">编辑</n-button>
              </div>
            </n-card>
          </section>

        </div>
      </main>
    </div>
  </div>

  <n-drawer v-model:show="showEditor" :width="520" placement="right">
    <n-drawer-content :title="editorTitle" closable>
    <n-form v-if="editingKey === 'feishu'" label-placement="top" class="channel-edit-form">
      <div class="channel-switch-stack">
        <span class="channel-enable-label">启用</span>
        <n-switch v-model:value="draft.channels.feishu.enabled" />
      </div>
      <n-form-item label="App ID（启用时必填）">
        <n-input v-model:value="draft.channels.feishu.appId" placeholder="cli_xxx" />
      </n-form-item>
      <n-form-item label="App Secret（启用时必填）">
        <n-input v-model:value="draft.channels.feishu.appSecret" type="password" show-password-on="click" />
      </n-form-item>
      <n-form-item label="Allow List（可选，逗号分隔）">
        <n-input v-model:value="allowListText.feishu" placeholder="ou_xxx, ou_yyy" />
      </n-form-item>
    </n-form>

    <n-form v-else label-placement="top" class="channel-edit-form">
      <div class="channel-switch-row">
        <span class="meta-label">启用钉钉通道</span>
        <n-switch v-model:value="draft.channels.dingtalk.enabled" />
      </div>
      <div class="channel-switch-row">
        <span class="meta-label">仅响应 @ 机器人</span>
        <n-switch v-model:value="draft.channels.dingtalk.requireMention" />
      </div>
      <n-form-item label="Client ID（启用时必填）">
        <n-input v-model:value="draft.channels.dingtalk.clientId" />
      </n-form-item>
      <n-form-item label="Client Secret（启用时必填）">
        <n-input v-model:value="draft.channels.dingtalk.clientSecret" type="password" show-password-on="click" />
      </n-form-item>
      <n-form-item label="Robot Code（启用时必填）">
        <n-input v-model:value="draft.channels.dingtalk.robotCode" placeholder="dingxxxx" />
      </n-form-item>
      <n-form-item label="Allow List（可选，逗号分隔）">
        <n-input v-model:value="allowListText.dingtalk" placeholder="manager001, manager002" />
      </n-form-item>
    </n-form>

    <template #footer>
      <div class="ui-actions-end">
        <n-button @click="showEditor = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="saveEditor">保存</n-button>
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
  font-size: var(--font-size-sm);
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
  margin-bottom: var(--space-5);
}

.channel-enable-label {
  font-size: var(--font-size-lg);
  font-weight: 400;
  color: var(--color-text-primary);
}

.channel-switch-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.channel-edit-form :deep(.n-form-item) {
  margin-bottom: var(--space-5);
}

@media (min-width: 1440px) {
  .channel-grid {
    grid-template-columns: repeat(4, minmax(240px, 1fr));
  }
}

@media (max-width: 1200px) {
  .channel-grid {
    grid-template-columns: repeat(3, minmax(220px, 1fr));
  }
}

@media (max-width: 900px) {
  .channel-grid {
    grid-template-columns: repeat(2, minmax(210px, 1fr));
  }
}

@media (max-width: 640px) {
  .channel-grid {
    grid-template-columns: 1fr;
  }

}
</style>
