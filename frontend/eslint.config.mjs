import { defineConfig, globalIgnores } from "eslint/config";
import nextPlugin from "@next/eslint-plugin-next";
import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier/flat";
import globals from "globals";

// eslint-config-next は依存する eslint-plugin-react が ESLint 10 未対応のため使わず、
// 同等の構成を直接組む(#46)。eslint-plugin-react の対応版が出たら eslint-config-next に戻す。
const eslintConfig = defineConfig([
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{js,mjs,ts,tsx}"],
    plugins: { "@next/next": nextPlugin },
    rules: {
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs["core-web-vitals"].rules,
    },
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
  },
  reactHooks.configs.flat.recommended,
  // Prettier と競合するフォーマット系ルールを無効化する(最後に置く)
  prettier,
  globalIgnores([".next/**", "out/**", "build/**", "coverage/**", ".preview/**", "next-env.d.ts"]),
]);

export default eslintConfig;
