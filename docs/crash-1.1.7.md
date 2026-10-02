# 1.1.7のクラッシュ修正

1.1.6で328,818地点のGoogle候補を選ぶと、MainActivityのEditText.setTextからDynamicLayout / MeasuredParagraphへ進み、192MiBのヒープ上限でOutOfMemoryErrorになった。全座標をテキスト編集欄へ渡していたことが原因。

1.1.7は取得済みRouteを保持し、座標欄には先頭・末尾の最大6地点と説明のみを渡す。全地点は内部バイナリファイルへ保存し、設定XMLへ重複保存しない。GPX出力と再生は全地点を使用する。保存ファイルは一時ファイルから置換し、保存失敗時は前のファイルを残す。

`build.py`で32万8,818地点の完全一致・短い表示・保存失敗・破損ファイルの回帰テストを192MBのJVMヒープで実行する。従来のGoogle経路・GPX・再生・投影のテストも実行する。

`tests/android/RouteLayoutTest.java`はAndroidのStaticLayoutを使う追加検証用。今回の端末実行はADB接続が切れ、完了していない。画面で候補選択から再生までの通し検証も未実施。
