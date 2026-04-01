# 前端开发说明

## 1. 前端结构
- `web/`
  Vue 前端工程，技术栈为 `Vue 3 + Vite + TypeScript + Pinia + Vue Router + Naive UI`。

当前建议的开发方式：

- Vue 前端使用独立端口运行，例如 `http://localhost:5174`
- Spring Boot 后端单独运行，例如 `http://localhost:8080`
- 前端通过 Vite 代理访问 `/api` 和 SSE

## 2. 图标库

前端导航图标统一使用 **Lucide**：

- 官方站点：<https://lucide.dev>
- GitHub 仓库：<https://github.com/lucide-icons/lucide>

使用方式分为两套：

- Vue 前端：使用 `lucide-vue-next`
- 静态页面：使用 Lucide CDN，通过 `window.lucide.createIcons()` 渲染

当前接入位置：

- Vue 导航栏：
  [DirectoryRail.vue](../../web/src/components/chat/DirectoryRail.vue)
- 静态导航栏：
  [nav.js](../../src/main/resources/static/assets/js/nav.js)

## 3. 许可说明

根据 Lucide 官方站点与 GitHub 仓库，Lucide 是开源图标库，并以 **ISC License** 发布。

这意味着在当前项目中：

- 可以免费使用
- 可以商用
- 可以修改和分发

但仍应保留其许可证与来源信息，避免在文档中把图标误写成自研资源。

## 4. 管理页统一布局规范

`/cron`、`/agents`、`/channels`、`/models`、`/settings` 统一按以下模板实现，后续新增管理页也应复用：

- 页头统一使用 [AppPageHeader.vue](../../web/src/components/layout/AppPageHeader.vue)
  - `kicker`（可选）
  - `title`（必填）
  - `subtitle`（可选，支持 slot）
  - `actions`（可选，右侧操作插槽）
- 页面主内容统一使用 `app-page-content` 容器类，保持相同的纵向节奏和响应式行为
- 页面不再定义私有标题字号（如 `xxx-page-title`），统一使用 `page-title / page-subtitle / ui-kicker`
- 页面结构遵循“无大外卡”原则：不套整页 `page-card`，正文使用分区卡片承载业务内容

这样可以确保五个管理页在标题位置、字号、上下间距、内容起始线和移动端表现上保持一致。
