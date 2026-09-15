/**
 * ビルド時に埋め込まれる公開設定。NEXT_PUBLIC_ 接頭辞の環境変数のみクライアントから参照できる。
 */
export const config = {
  /** backend の URL(末尾スラッシュなし)。未設定ならローカルの docker compose を指す */
  apiBaseUrl: (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8180").replace(/\/+$/, ""),
} as const;
