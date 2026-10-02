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

## 1.1.6の修正

既存のユーザー添付画面を主参照として、標準ボタンとダイアログを維持します。Google Maps側の表示を比較の参照に使い、取得した距離・候補名を選択前に提示します。

| 決定 | 根拠 |
| --- | --- |
| 「Googleのルート候補を取得」と表示 | 共有画面で選んだ候補を保証できないため、完全一致の変換と呼ばない |
| Google表示距離・候補名・地点数を選択ダイアログに表示 | 添付画像の12,929kmとの照合が可能 |
| OSRMへ自動で切り替えない | ユーザーが元の道筋の取得を要求 |
| Mercator・連続経度を使う | Google Mapsとの見え方の差・日付変更線の折返しを解消 |

Web応答スキーマの調査参照: https://github.com/alltechdev/vela-dpad/blob/main/docs/SPEC.md 。実装は独自のprotobufテキスト組立と厳格なE7座標解析。Googleの非公式Web形式を利用するため、変更時は失敗を表示し、代替形状は生成しません。検証用の応答本文や利用者の具体的リンクはGitと配布ZIPへ含めません。
