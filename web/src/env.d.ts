/// <reference types="vite/client" />

declare module "markdown-it-texmath" {
  const texmath: (markdown: unknown, options?: Record<string, unknown>) => void;
  export default texmath;
}
