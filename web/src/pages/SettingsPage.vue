<script setup lang="ts">
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content">
          <AppPageHeader
            title="设置"
            subtitle="管理当前工作区的账户信息和界面偏好。"
          />
          <div class="page-section-grid">
          <section class="surface-card">
            <div class="surface-card-title">账户信息</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">当前身份</div>
                <div class="ui-value-strong">NomoClaw Operator</div>
              </div>
              <div>
                <div class="meta-label">默认时区</div>
                <div class="ui-value-strong">Asia/Shanghai</div>
              </div>
            </div>
          </section>

          <section class="surface-card">
            <div class="surface-card-title">界面偏好</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">布局模式</div>
                <div class="ui-value-strong">聊天工作台</div>
              </div>
              <div>
                <div class="meta-label">消息渲染</div>
                <div class="ui-value-strong">Markdown + 文件链接</div>
              </div>
            </div>
          </section>

          <section class="surface-card">
            <div class="surface-card-title">工作区说明</div>
            <div class="copy-muted ui-detail-list">模型选择、默认 Agent、快捷键或个人资料，可在这个页面扩展。</div>
          </section>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
