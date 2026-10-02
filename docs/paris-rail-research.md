# パリ鉄道対応の調査保存

このブランチは調査段階です。正式APKには鉄道再生を実装していません。

Île-de-France MobilitésのGTFSを `paris_download.py` で取得し、`paris_inspect.py` で路線・列車・停車時刻・運転日の構造を確認できます。データの取得先は https://eu.ftp.opendatasoft.com/stif/GTFS/IDFM-gtfs.zip です。取得データは `tools/paris/` に置き、Gitには含めません。

今後は対象路線・区間の選択、Europe/Parisの現地時刻と夏時間、運転カレンダー、駅間経路と停車時刻、運休・リアルタイム遅延の扱いを設計します。GTFSの予定時刻表と当日の実運行は区別する必要があります。ダイヤ更新の際はデータ取得日時と有効期間を確認します。

現在の調査ファイルは保存用で、自動ビルド対象のAndroidソースに変更はありません。
