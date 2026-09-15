import type { NextConfig } from "next";

// GitHub Pages(project site)向けの静的エクスポート設定。
// - output: "export"  → `out/` に静的ファイルを生成(SSR / API Route は使えない)
// - basePath          → https://otajisan.github.io/youtube-handle-id-converter/ 配下で配信される
// - trailingSlash     → ネストしたルートを `dir/index.html` にして Pages の 404 を避ける
// - images.unoptimized→ 画像最適化サーバーが無いため無効化
const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "/youtube-handle-id-converter";

const nextConfig: NextConfig = {
  output: "export",
  basePath,
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
};

export default nextConfig;
