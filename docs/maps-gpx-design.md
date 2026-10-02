# Google Maps・GPX操作の設計

既存のRoute GPS画面とユーザー添付のスクリーンショットを主方向として固定します。Android標準入力・ボタン、既存の文字サイズと配色を継続します。Refero Designのcopywriting.mdを補助参照とし、入力→作成→保存の動詞と制限を明示します。

| 決定 | 根拠 |
| --- | --- |
| 経路リンクの入力と作成ボタンを並べる | 既存の座標入力→道路生成の操作を継続 |
| GPX保存を独立ボタンにする | 端末内の保存先はAndroidのファイル選択で指定 |
| 場所名の候補を選択する | 同名の場所を勝手に確定しない |
| 再計算で道順が変わることを作成前に明示する | Google Maps URLは地点を表す。Googleの表示経路を取得する機能とは異なる |
| 100000地点・50MBを読込ボタンの下に表示する | 添付画像にある5000地点エラーの解消を確認できる |

URLはGoogle公式Maps URLsのorigin/destination/waypointsを読み取ります。通常の/maps/dir/パスと旧saddr/daddr形式にも対応します。@で始まる表示中心は経由地として使いません。短縮URLは既知のGoogle MapsホストへのHTTPS転送だけを追跡します。場所名検索はNominatim、経路生成はOSRMの徒歩プロファイルです。

参考： https://developers.google.com/maps/documentation/urls/get-started 、 https://operations.osmfoundation.org/policies/nominatim/

既存のAPI公開環境ではAndroid画面のレンダリング・ファイル選択を実機で自動検証できていません。URL解析、GPX読み書き、ルート計算とAndroidコンパイルをホスト上で検証します。
