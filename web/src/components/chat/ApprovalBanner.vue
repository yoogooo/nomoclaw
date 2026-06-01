<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton } from "naive-ui";
import { useConversationStore } from "@/stores/conversation";
import { renderMarkdown } from "@/utils/markdown";

const props = withDefaults(defineProps<{
  forceVisible?: boolean;
}>(), {
  forceVisible: false
});

const conversationStore = useConversationStore();
const { t } = useI18n();
const visible = computed(() => props.forceVisible || Boolean(conversationStore.approval.stepUid));
const bannerRef = ref<HTMLElement | null>(null);
const isSubmitting = computed(() => conversationStore.approval.submitting);
const allowOnceLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "allow_once");
const allowAgentLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "allow_agent");
const allowSessionLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "allow_session");
const rejectLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "deny_once");
const missingStepUid = computed(() => !conversationStore.approval.stepUid);
const isHardGuardApproval = computed(() => String(conversationStore.approval.policyReasonCode || "").startsWith("HARD_GUARD_"));
const approvalPrompt = computed(() => conversationStore.approval.title || t("chat.approval.riskPrompt"));
const approvalBodyLabel = computed(() => {
  return t(conversationStore.approval.labelKey || "chat.approval.payloadLabel");
});
const approvalBodyHtml = computed(() => {
  const command = String(conversationStore.approval.command || "").trim();
  if (command) {
    return renderMarkdown(`\`\`\`bash\n${command}\n\`\`\``);
  }
  const body = String(conversationStore.approval.body || "").trim();
  if (body) {
    return renderMarkdown(body);
  }
  return renderMarkdown(`\`\`\`text\n${t("chat.approval.commandUnavailable")}\n\`\`\``);
});

async function focusAndReveal() {
  await nextTick();
  const element = bannerRef.value;
  if (!element) return;
  element.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
  window.setTimeout(() => {
    element.focus({ preventScroll: true });
  }, 220);
}

watch(visible, (isVisible, wasVisible) => {
  if (isVisible && !wasVisible) {
    void focusAndReveal();
  }
});

onMounted(() => {
  if (visible.value) {
    void focusAndReveal();
  }
});
</script>

<template>
  <section
    v-if="visible"
    ref="bannerRef"
    class="ui-approval-card"
    role="alertdialog"
    tabindex="-1"
    aria-live="assertive"
    :aria-label="t('chat.approval.waiting')"
  >
    <div class="ui-approval-content">
      <div class="ui-approval-info">
        <div class="ui-approval-head">
          <div class="ui-approval-icon" aria-hidden="true">
            <svg class="ui-approval-icon-svg" viewBox="0 0 24 24" fill="none">
              <path d="M12 3L19 7V12C19 16.4 16.2 20.2 12 21C7.8 20.2 5 16.4 5 12V7L12 3Z" />
              <path d="M12 8V13" />
              <path d="M12 16H12.01" />
            </svg>
          </div>
          <div class="ui-approval-headline">{{ t("chat.approval.waiting") }}</div>
        </div>
        <div class="ui-approval-title">{{ approvalPrompt }}</div>
        <div class="ui-approval-command">
          <div class="ui-approval-command-label">{{ approvalBodyLabel }}</div>
          <div class="ui-approval-body message-html mono" v-html="approvalBodyHtml" />
        </div>
      </div>
      <div class="ui-approval-actions">
        <n-button
          class="ui-approval-approve-btn"
          secondary
          :loading="allowOnceLoading"
          :disabled="isSubmitting || missingStepUid"
          @click="conversationStore.approveStep('once')"
        >
          {{ allowOnceLoading ? t("chat.approval.approving") : t("chat.approval.approve") }}
        </n-button>
        <n-button
          class="ui-approval-approve-btn"
          secondary
          :loading="isHardGuardApproval ? allowSessionLoading : allowAgentLoading"
          :disabled="isSubmitting || missingStepUid"
          @click="conversationStore.approveStep(isHardGuardApproval ? 'session' : 'agent')"
        >
          {{
            (isHardGuardApproval ? allowSessionLoading : allowAgentLoading)
              ? t("chat.approval.approving")
              : (isHardGuardApproval ? t("chat.approval.approveSession") : t("chat.approval.approveAgent"))
          }}
        </n-button>
        <n-button
          class="ui-approval-reject-btn"
          type="error"
          strong
          :loading="rejectLoading"
          :disabled="isSubmitting || missingStepUid"
          @click="conversationStore.rejectStep()"
        >
          {{ rejectLoading ? t("chat.approval.rejecting") : t("chat.approval.reject") }}
        </n-button>
      </div>
    </div>
  </section>
</template>
