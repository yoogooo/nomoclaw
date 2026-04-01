<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { NButton } from "naive-ui";
import { useConversationStore } from "@/stores/conversation";

const props = withDefaults(defineProps<{
  forceVisible?: boolean;
}>(), {
  forceVisible: false
});

const conversationStore = useConversationStore();
const visible = computed(() => props.forceVisible || Boolean(conversationStore.approval.stepUid));
const bannerRef = ref<HTMLElement | null>(null);
const isSubmitting = computed(() => conversationStore.approval.submitting);
const approveLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "approve");
const rejectLoading = computed(() => isSubmitting.value && conversationStore.approval.submittingAction === "reject");
const missingStepUid = computed(() => !conversationStore.approval.stepUid);

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
    aria-label="高风险操作待确认"
  >
    <div class="ui-approval-content">
      <div class="ui-approval-head">
        <div class="ui-approval-icon" aria-hidden="true">!</div>
        <div>
          <div class="ui-approval-headline">高风险操作待确认</div>
          <div class="ui-approval-title">{{ conversationStore.approval.title }}</div>
        </div>
      </div>
      <pre class="ui-approval-body mono">{{ conversationStore.approval.body }}</pre>
      <div class="ui-approval-actions">
        <n-button
          class="ui-approval-approve-btn"
          secondary
          :loading="approveLoading"
          :disabled="isSubmitting || missingStepUid"
          @click="conversationStore.approveStep()"
        >
          {{ approveLoading ? "继续执行中" : "继续执行" }}
        </n-button>
        <n-button
          class="ui-approval-reject-btn"
          type="error"
          strong
          :loading="rejectLoading"
          :disabled="isSubmitting || missingStepUid"
          @click="conversationStore.rejectStep()"
        >
          {{ rejectLoading ? "拒绝中" : "拒绝本次操作" }}
        </n-button>
      </div>
    </div>
  </section>
</template>
