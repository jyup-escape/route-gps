# Route GPS

正式版：1.1.8。APIキーなしでGoogle側のルート候補の全座標を取得し、選んだ道筋をGPX保存・再生。OSRMへの自動置換を撤廃。地点数・距離・GPXサイズ・信号件数の固定上限を撤廃。大きなルートは端末のメモリの範囲で扱います。巡航速度・加速度・減速度・速度のゆらぎ・信号待ち時間の上限を解除しました。電車・時刻表対応は別ブランチで準備中です。

JavaとAndroid標準APIで実装した徒歩ルート再生用の疑似GPSアプリ。[使い方](docs/usage.md)、[バージョン管理](docs/versioning.md)、[変更履歴](CHANGELOG.md)を参照。

## ビルド

Windows、Python 3、ネットワーク接続が必要です。

```powershell
python setup_tools.py
python build.py
```

Google公式のAndroid API 33プラットフォームとBuild Tools 33.0.2、Eclipse Temurin JDK 17をプロジェクトの`tools`にダウンロードし、配信元のチェックサムを検証します。システムのJava設定は変更しません。`build.py`は純Javaのルートテスト、リソース処理、コンパイル、DEX化、zipalign、APK署名と署名検証を行います。署名鍵は`tools/routegps.keystore`に生成します。更新用にはこの鍵を保持してください。ローカル配布用の鍵です。

## 設計の参照と決定

- 主方向：Android標準のMaterial Lightテーマ。白い入力面、標準sans、ラベル付きフォーム、48dp以上の操作領域。
- Refero Design同梱の`craft-details.md`：入力形式・単位の明示、貼り付け可能な入力、フォームのエラー表示。
- Refero Design同梱の`typography.md`：作業用ツールには装飾を抑えた中立的な文字階層を使用。
- Android公式Dialogs文書：読込・設定・入力エラーには明示的な確認ボタン付きダイアログ。
- ユーザー要件：指定ルート・指定速度が主操作。先に形状と距離・所要時間を確認し、次に再生する構成。

| 決定 | 参照 | 目的 |
| --- | --- | --- |
| ルート → 速度 → 再生の順 | ユーザーのルート・速度要件 | 再生前に条件を確認 |
| 14/18/20/28spのsans | typography.mdとAndroid標準UI | 数値と状態の読み取り |
| ラベル付き数値・座標入力 | craft-details.md | 単位と入力形式を明確化 |
| 緑を再生・ルート表示に限定、白背景・濃い文字 | 明示的な操作役割、Material標準方向 | 操作と背景の役割を固定 |
| ルート形状を図で表示 | 指定ルート要件 | 入力間違いの発見 |
| 誤った入力はダイアログで説明 | Android Dialogs | 修正内容を明確化 |

## API参照

- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/develop/ui/views/components/dialogs
- https://project-osrm.org/docs/v5.24.0/api/
- https://routing.openstreetmap.de/about.html
- https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL
- https://wiki.openstreetmap.org/wiki/Key:crossing:signals

## v1.1の構造・検証

`WalkSimulation`はAndroidに依存しない徒歩の状態機械。20ms以下の内部ステップと制動距離の包絡で加減速を制限し、停止点への到達・待ち・発進・一時停止・周回を管理します。`PlaybackService`はこの現在距離と速度をGPSプロバイダに公開します。長い遅延は最大5秒まで取り込みます。

`RoadClient`は明示操作でのみOSRM徒歩ルートとOverpass信号を取得し、徒歩ルート形状と信号の距離を端末内に保持します。形状を編集すると作成済みの印と信号を破棄し、再生成が必要な状態に戻します。周回の帰路も徒歩検索に含め、開いたルートを勝手に直線で閉じません。

`WalkSimulationTest`は速度変化の上限、赤での停止、青での通過、停止距離、待ち、再開、周回、ランダム設定、GPS更新間隔の影響を検証します。`RoadClientTest`は信号の射影と日付変更線を検証し、`--live`で公共サービスとの実通信も確認できます。ホスト上のRoadClientテストにはorg.jsonのJVM用jarを`tools/test-json.jar`に用意します。APKはAndroid標準のorg.jsonを使うためjarの同梱は不要です。

UIはv1.0の参照方向を継続。道路作成・信号取得の明示ボタン、通信先の表示、加減速度・確率・時間の単位付き入力、現在速度と巡航速度の併記を追加しました。

Android上の実機操作検証状況は配布用説明書に明記しています。

配布物は署名済みAPKとビルド元ソースZIPの2点です。`python package.py`で`releases/バージョン/`に作成します。BlueStacks用ツール・設定スクリプトは配布しません。
