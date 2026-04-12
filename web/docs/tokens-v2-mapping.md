# Tokens V2 Mapping

## Direct Mapping
- `--size-620` -> `--container-sm`
- `--size-720` -> `--container-md`
- `--size-860` -> `--container-lg`
- `--size-980` -> `--container-xl`

## Semantic Renames (Typography)
- `--font-size-2xs` -> `--text-caption-size`
- `--font-size-xs` -> `--text-caption-size`
- `--font-size-sm` -> `--text-body-size`
- `--font-size-md` -> `--text-body-size`
- `--font-size-lg` -> `--text-title-sm-size`
- `--font-size-title-lg` -> `--text-title-md-size`
- `--font-size-title-xl` -> `--text-title-lg-size`

## Removed/Rewritten Usage Patterns
- `padding|margin|gap|scroll-margin*` with `--size-*` -> `--space-*` or semantic spacing tokens
- legacy radius aliases (`--radius-m`, `--radius-s-md`, `--radius-xl-2`, `--radius-round`, `--radius-l`) -> standardized radius tokens (`--radius-sm/md/lg/xl/2xl/pill`) or control radius tokens

## New Foundation Tokens
- Font scale: `--text-xs/sm/base/lg/xl/2xl`
- Line-height scale: `--text-*-line-height`
- Spacing scale: `--space-0/0_5/1/1_5/2/2_5/3/3_5/4/4_5/5/5_5/6/7/8`

## New Semantic Tokens
- Text: `--text-caption-*`, `--text-body-*`, `--text-title-sm/md/lg-*`
- Container: `--container-xs/sm/md/lg/xl`
- Control: `--control-height-*`, `--control-radius-*`, `--control-padding-*`
- Spacing helpers: `--space-anchor-offset`, `--space-jinnang-preview-right`, `--space-chat-mobile-input-offset`

## Notes
- Foundation text tokens are reserved for token composition; component/page styles should use semantic text tokens.
- Spacing properties should only consume `--space-*` tokens.
