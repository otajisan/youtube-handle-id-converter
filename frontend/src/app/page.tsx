import styles from "./page.module.css";

export default function Home() {
  return (
    <main className={styles.main}>
      <h1 className={styles.title}>YouTube Handle ⇄ Channel ID Converter</h1>
      <p className={styles.description}>
        YouTube のハンドル(<code>@handle</code>)と Channel ID(<code>UC...</code>)を相互変換します。
        1 回に最大 10 件まで入力できます。
      </p>
      <p className={styles.notice} role="status">
        変換フォームは準備中です。
      </p>
    </main>
  );
}
