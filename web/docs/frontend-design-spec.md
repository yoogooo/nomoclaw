# 前端开发规范（Vue + Naive UI + Tokenized Design System）

## 1. 目标
1. 统一视觉语言，避免页面风格漂移。
2. 支持 Light/Dark 双主题一处配置、全局生效。
3. 降低维护成本，提升协作与评审效率。

## 2. 适用范围
1. 业务页面与组件（`.vue`）。
2. 全局样式层（`src/styles.css`）。
3. 设计变量层（`src/styles/tokens.css`、`src/themeTokens.ts`）。

## 3. 三层分离规则（必须）
1. `tokens.css`：只放设计值（颜色/字号/间距/圆角/阴影/尺寸等）。
2. `styles.css`：只放全局语义工具类与可复用组件类（如 `.ui-title-md`、`.ui-card`、`.ui-chip`）。
3. 业务 `.vue`：只写布局和少量页面特有样式，优先组合全局类，不重复定义视觉属性。

## 4. Token 分层模型（必须）
1. Primitive Token：原子值（例如基础色阶、基础间距）。
2. Semantic Token：语义值（例如 `--color-text-primary`、`--space-section-gap`）。
3. Component Token：组件级语义（例如 `--input-border-focus`、`--button-primary-bg`）。
4. 依赖方向固定为 `Primitive -> Semantic -> Component`，禁止反向依赖。
5. 业务层默认只使用 Semantic/Component Token。

## 5. Token 覆盖范围（必须）
以下维度必须 token 化，禁止业务硬编码：
1. 颜色（含透明色）。
2. 字号与字重。
3. 间距与尺寸。
4. 圆角与边框宽度。
5. 阴影。
6. 断点（breakpoint）。
7. 层级（z-index）。
8. 动效（duration/easing）与透明度（opacity）。
9. 焦点环（focus-ring）。

## 6. 主题规范（Light/Dark）
1. Light 与 Dark 都必须有完整 token 映射。
2. 禁止组件内部写“临时暗色覆盖”。
3. 主题切换仅允许通过统一入口（如根节点 `data-theme` 或根 class）。
4. 交互状态在双主题下必须同时验证：`default`、`hover`、`active`、`focus`、`disabled`、`error`。

## 7. 可访问性（A11y）红线
1. 正文文本对比度满足 WCAG AA（至少 4.5:1）。
2. 所有可交互元素必须键盘可达。
3. 必须实现统一且可见的 `:focus-visible`。
4. 动画需支持 `prefers-reduced-motion`。

## 8. 全局样式约束（`styles.css`）
1. 仅保留可复用语义类，不写业务耦合选择器。
2. 全局类内部必须优先使用 token。
3. 跨页面一致行为放全局层（例如 Drawer 方角、统一 focus 样式）。

## 9. 业务组件约束（`.vue`）
1. 优先使用 `ui-*` 全局类进行组合。
2. 页面样式只保留布局与极少量页面特有差异。
3. 禁止在模板中写视觉内联样式（若必须，需仅引用 CSS 变量）。
4. Naive UI 的状态色与边框全部走 token。

## 10. 命名规范
1. Token 命名采用前缀分域：`--color-*`、`--font-size-*`、`--space-*`、`--radius-*`、`--shadow-*`、`--size-*`。
2. 全局可复用类统一 `ui-*` 前缀。
3. 禁止语义不清的命名（例如 `--blue-1` 在业务层直接使用）。

## 11. 禁止项
1. 禁止在业务 `.vue` 中硬编码颜色（`#hex`、`rgba`）。
2. 禁止在业务 `.vue` 中硬编码字号、间距、圆角、阴影、断点。
3. 禁止只改 Light 不改 Dark。
4. 禁止复制粘贴重复视觉样式到多个页面。

## 12. 工程门禁（CI 必过）
1. `pnpm -s lint:design-tokens` 必过。
2. `pnpm -s build` 必过。
3. 建议启用 `stylelint` 规则：
- 禁止业务样式出现 `#hex`、`rgba(...)`。
- 禁止业务样式出现硬编码 `px`（白名单除外）。
4. 建议 PR 阶段执行 Light/Dark 视觉回归截图比对。

## 13. Token 治理流程
1. Token 变更必须经过设计与前端评审。
2. Token 需版本化并维护 changelog。
3. 废弃 token 先标记 `deprecated`，保留迁移窗口后再移除。

## 14. 代码评审清单（CR Checklist）
1. 是否存在硬编码视觉值。
2. 是否优先复用 `ui-*` 类。
3. Light/Dark 是否同时正确。
4. 状态模型是否完整（含 error/focus/disabled）。
5. 是否新增了重复语义 token 或重复全局类。

## 15. 页面开发模板（Vue + Naive UI）
```vue
<template>
  <section class="page-frame app-page">
    <div class="page-card">
      <header>
        <h1 class="ui-title-lg">页面标题</h1>
        <p class="ui-subtitle">页面描述</p>
      </header>

      <div class="page-section-grid">
        <article class="ui-card-base">
          <div class="ui-pane-title">模块标题</div>
          <div class="ui-form-help">说明文字</div>

          <n-input class="ui-focus-highlight" placeholder="请输入" />
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-section-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-3);
}
</style>
```

