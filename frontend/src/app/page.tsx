import { Converter } from "@/components/Converter";
import styles from "./page.module.css";

export default function Home() {
  return (
    <main className={styles.main}>
      <h1 className={styles.title}>YouTube Handle ⇄ Channel ID Converter</h1>
      <p className={styles.description}>
        YouTube のハンドル(<code>@handle</code>)と Channel ID(<code>UC...</code>)を相互変換します。
        YouTube の URL も入力できます。
      </p>
      <Converter />
      <p className={styles.notice}>
        誰でも利用できる代わりに YouTube Data API の 1 日の割り当てを全員で共有しています。
        割り当てを使い切ると翌日(太平洋時間 0 時)まで利用できません。
      </p>
    </main>
  );
}
