import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig, loadEnv } from "vite";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    const localEnv = loadEnv(mode, process.cwd(), "");

    const { VITE_BASE_URL } = localEnv;
    return {
        plugins: [
            react()
        ],
        resolve: {
            alias: [{find: "@", replacement: path.resolve(__dirname, "src")}]
        },
        base: VITE_BASE_URL,
    };
});