import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// SPA is served by Spring Boot from /; during dev, proxy the API to the backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    // Keep the bundle lean for free-tier hosting; split the charting lib out.
    rollupOptions: {
      output: {
        manualChunks: {
          charts: ["recharts"],
        },
      },
    },
  },
});
