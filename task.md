# GitHub 公開タスクリスト (VirtualKeysMod)

GitHubにプロジェクトを公開し、JARファイルをリリース配布するまでに必要な手順の管理リストです。

## 1. Gitのセットアップと最初の記録
- [x] リポジトリに日本語の [README.md](README.md) を作成する
- [/] ローカルフォルダでGitリポジトリを初期化する (`git init`)
- [/] 変更されたすべてのファイルをGitに登録する (`git add .`)
- [ ] 最初のコミット（記録）を実行する (`git commit -m "Initial commit"`)

## 2. GitHubへのアップロード (Push)
- [ ] ブラウザで GitHub 上に「`VirtualKeysMod`」という名前の空のリポジトリを作成する
- [ ] 作成したリポジトリの URL を登録する (`git remote add origin ...`)
- [ ] `main` ブランチにソースコードをアップロードする (`git push -u origin main`)

## 3. リリース (Releases) 作成とJARファイルの配布
- [x] `gradlew clean build` により最新の Mod JAR ファイルをビルドする
- [ ] GitHub リポジトリページで新規リリース（バージョン `v1.0.1`）を作成する
- [ ] ビルドされた [virtualkeys-1.0.1.jar](build/libs/virtualkeys-1.0.1.jar) をリリースに添付して公開する
