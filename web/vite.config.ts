import { resolve } from "node:path";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function normalizeBasePath(rawBase: string | undefined): string {
  const raw = (rawBase || "/").trim();
  if (!raw) {
    return "/";
  }

  let value = raw.replace(/\\/g, "/");

  // Defensive fallback for Git Bash/MSYS path conversion on Windows, e.g.
  // "/Program Files/Git/nomoclaw/" or "C:/Program Files/Git/nomoclaw/".
  if (/^[a-zA-Z]:\//.test(value) || value.startsWith("/Program Files/")) {
    const cleaned = value.replace(/\/+$/, "");
    const lastSegment = cleaned.slice(cleaned.lastIndexOf("/") + 1);
    if (lastSegment) {
      value = `/${lastSegment}/`;
    } else {
      value = "/";
    }
  }

  if (!value.startsWith("/")) {
    value = `/${value}`;
  }
  if (!value.endsWith("/")) {
    value = `${value}/`;
  }
  return value;
}

export default defineConfig({
  base: normalizeBasePath(process.env.VITE_BASE),
  plugins: [vue()],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src")
    }
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: false
      }
    }
  },
  build: {
    outDir: resolve(__dirname, "dist"),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ["vue", "vue-router", "pinia"],
          ui: ["naive-ui"],
          markdown: ["markdown-it", "dompurify"]
        }
      }
    }
  }
});
