# AE2 Machines 0.1.4

Minecraft **1.20.1 Forge** / **1.21.1 NeoForge** 向けの実験的プレリリースです。
対応する本体JARを1つだけ、クライアントとサーバー双方へ導入してください。
AE2は必須、ACOは任意連携です。他MOD本体は同梱しません。

## 修正の背景

最大並列で原子炉から反物質を生産する試験中、流体量のint上限（2,147,483,647）に引っかかり、ボイラー・タービン系統が停止する問題が見つかりました。
単体用のFluidStack APIの上限が、仮想並列分の水・凝縮水や配管内の合計量にも適用され、必要な給水・循環を維持できなくなっていました。
0.1.4は、この試験で判明した制限を合計量のBigInteger会計と対応する搬送処理で修正するプレリリースです。
修正後のシンク構成による実環境再試験は今回は行っていません。試験での問題発見と、修正後の実生産確認は別です。

## 変更点

- セル内のMekanism流体タンク・機械配管について、仮想並列分の合計量をBigIntegerで保持します。通常のFluidStack APIのint値を、工場全体の保有量の上限として扱わない構成へ変更しました。
- 対応するセル内の搬送先同士では、本家の挿入・取り出し判定を通しつつ、転送量を集約します。未対応の外部容器や有限の他MOD容器を、無条件に数量倍化することはありません。
- 本家ボイラーの1台分の沸騰判定と合計の水・蒸気・熱会計を分離しました。タービンの凝縮量も、対応する仮想並列分を合計量で扱います。
- 流体の追加数量を保存データへ記録し、配管の保存共有分・ネットワークの結合・アップグレード・セルからの復元会計へ引き継ぐ処理を追加しました。
- Cooking for Blockheadsのシンクは、本家の無限水設定でセル内の機械配管が取り出す場合に集約対象として扱います。シンクやMekanism本体の設定を書き換える機能ではありません。
- 並列演算器と多重演算コアを組み合わせ、T9同士の設定上限を33,554,432並列とします。多重演算コアは仮想台数の倍率であり、同数のOSスレッドを生成しません。現行マルチブロックの加速設定は等速です。
- 原子炉は合意済みの「基準炉の温度状態を共有する」モデルで並列数を扱います。各仮想炉の異なる温度履歴を個別再現する仕様ではありません。
- 既存の公開APIとACO任意連携を維持します。内部AE2 CPUとInduction Matrix専用の並列化は対象外です。

## 確認状況

この公開物はビルドと静的な配布物確認を行った候補です。
公開前の最終GameTest・起動試験・シンク経由の再試験は省略しています。
作業途中の限定テストは成功完了しておらず、最終版の数量・熱・復旧の実行時保証には使っていません。

最大並列での335,544,320個の反物質ペレット実生産、全構成の安定動作、性能改善を確認済みとするリリースではありません。
SPSや他MOD APIに残る個別の型上限を、すべて除去した版でもありません。
通常APIとGUIにはint量の表示窓が残るため、表示値だけで合計数量を判断しないでください。

**既存ワールドのバックアップを推奨します。**
int上限を超えた流体を保存したワールドを旧版で開くと、追加数量を旧版が読み取れません。直接ダウングレードしないでください。
公開JARに私用試験コード・ワールド・ログ・依存MODは含めていません。

## English

Experimental pre-release for Forge 1.20.1 and NeoForge 1.21.1.
AE2 is required on both sides; ACO remains optional.

During the maximum-parallel reactor-to-antimatter trial, the int fluid-quantity
ceiling (2,147,483,647) stalled the boiler/turbine circuit. The native single-unit
FluidStack limit was also limiting aggregate water, condensate and pipe contents,
preventing sufficient water supply and circulation. This release addresses that
observed failure; the revised sink-based circuit has not been retested in-game.

Virtual Mekanism fluid inventories now retain aggregate quantities with BigInteger
instead of using the native int FluidStack view as the total inventory limit.
Supported native pipe transfers, boiler water/steam accounting, turbine condensate,
save shares and restoration accounting use the extended quantities.
Cooking for Blockheads sinks can participate in aggregated pulls in their native
infinite-water mode. Unknown finite external tanks are not multiplied.

The modular computer currently runs at normal speed with up to 33,554,432 virtual
lanes from the T9 parallel core and T9 multiplicative core. This is a configured
limit, not measured throughput or a claim of constant CPU cost. Fission lanes share
the reference reactor's thermal state.

Final runtime, GameTest and sink-circuit acceptance were intentionally not rerun.
Intermediate focused tests did not complete successfully. This release does not
claim completed maximum-parallel pellet production, crash-recovery certification,
or removal of every int limit in other APIs. Existing public APIs remain unchanged.

Back up worlds. Do not downgrade a world containing extended fluid quantities:
older versions cannot read those additional amounts.
