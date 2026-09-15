import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "YouTube Handle ⇄ Channel ID Converter",
  description: "YouTube のハンドル(@handle)と Channel ID(UC...)を相互変換するツール",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ja">
      <body>{children}</body>
    </html>
  );
}
