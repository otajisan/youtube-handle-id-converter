# CLAUDE.md

YouTube のハンドル(`@handle`)と Channel ID(`UC...`)を相互変換する Web ツール。

## Git / GitHub の操作ユーザー

- このリポジトリでは Git / gh の操作を必ず **otajisan** (`otajisan <taji.taji.otajisan@gmail.com>`) で行い、それ以外のアカウントは使わない。
- `gh pr create` などの gh コマンドを実行する前に `gh auth status` でアクティブなアカウントが otajisan か確認し、違えば `gh auth switch -u otajisan` してから実行する。
- `.githooks/pre-commit` と `.githooks/pre-push` が otajisan 以外のコミット・push を止める。フックを `--no-verify` で回避しない。
- `core.hooksPath=.githooks` で追跡中のフックがそのまま実行されるため、他の人の PR(Dependabot を含む)が `.githooks/` を変更していたら、checkout やコミットの前に必ず差分を確認する。

### clone し直したときの再設定

```sh
git config user.name  "otajisan"
git config user.email "taji.taji.otajisan@gmail.com"
git remote set-url origin https://otajisan@github.com/otajisan/youtube-handle-id-converter
git config core.hooksPath .githooks
```

remote URL に `otajisan@` を入れているので、gh の credential helper は gh のアクティブアカウントに関係なく otajisan のトークンを使う。
