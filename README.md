# AE2 Machines

**0.1.0 Alpha** | Minecraft 1.20.1 Forge / 1.21.1 NeoForge

## 日本語

AE2で構築した工場を空間セルへ格納し、空間演算器で加速・並列化するアドオンです。
加工は元の機械が行います。レシピの素材・電力・副産物を独自のアイテム変換に置き換えるMODではありません。

### 主な機能

- 仮想化空間標識で囲った設備を仮想化空間セルへ格納。
- 横方向に増設できる空間演算器と、セルベイ・冷却ユニット・MEチャンネルポート・電力入力。
- セル内の仮想P2Pと仮想電力ポートを通し、実際のMEネットワークと電力系統へ接続。
- 加速演算器T1～T9は最大2～512倍、並列演算器T1～T9は最大16～4,096並列。
- 加工パターンを使う通常のAE2発注と、パターン不要のパッシブ稼働。
- 対応する機械では元の数量指定処理、または同じ状態をまとめる並列処理を使用。設備を並列数分複製する方式ではありません。
- LDLib導入時は、セル内部と演算器の立体プレビューを表示。回転・拡縮・カメラ移動に対応。
- セルのツールチップに格納範囲の寸法を表示。
- セルベイを空の手で右クリックするとセルを取り出し。演算器GUIはコントローラーから開きます。
- 元の標識への復元操作と、対応するMekanismコンテナの容量超過分をセルに残す復元。

### 依存関係

必須MODは **Applied Energistics 2** です。クライアントとサーバーの両方へ導入してください。

**ACOは任意連携**です。対応する経路でBigInteger加工依頼・バッチ連携を利用します。
LDLib、JEI/EMI、ME Access、Mekanism、Applied Mekanistics、Create、GTCEuは、利用する連携に応じて追加してください。他MODは同梱していません。

### セルを現実へ戻す

セルベイから取り出したセルを持ち、元の仮想化空間標識をShift＋右クリックします。復元先は空けておく必要があります。

対応するMekanismコンテナでは、元の容量に収まる分を設備へ戻し、入りきらないアイテム・流体・化学物質・電力・熱は同じセルに保持します。
余剰入りセルを持って同じ標識をShift＋右クリックすると、復元済みの元設備の空き容量へ補充します。設備自体は二度復元しません。
化学物質はMekanism本来の形式で保持し、電力と熱は別々に会計します。

未完了の加工や受領待ちの出力は先に解決してください。未知の保存形式・復旧境界は安全側で拒否します。

### Alpha版の範囲

開発中の初期公開版です。既存ワールドでは必ずバックアップを取り、最初は新規ワールドで確認してください。
サバイバル向けの構成部品・ティアのレシピ整備は未完了です。現段階ではクリエイティブタブで部品を入手して構築する用途を想定しています。

すべてのMOD・機種・マルチブロックに無条件で集約並列を適用するものではありません。
未知機種のQ並列には、元機械の入出力・状態・保存の独立性を確認できる連携契約が必要です。未対応機種を加速だけで並列対応と表示しません。
内部のAE2クラフトCPUと、Induction Matrix専用の加速・並列化は対象外です。
任意の外部倉庫・外部CPU・電源をまたぐ異常終了復旧や、全構成での同一負荷を保証する版ではありません。

## English

AE2 Machines captures an ME-connected factory in a virtual spatial cell and runs it inside a modular spatial computer with acceleration and supported parallel processing.
The original machines perform their own processing. This addon does not replace recipes with direct item conversion or waive material and power costs.

- Minecraft 1.20.1 Forge and 1.21.1 NeoForge, with separate downloads.
- Applied Energistics 2 is required on both the client and server. ACO integration is optional.
- Modular computer, cell bays, channel and energy endpoints, and tiered acceleration/parallel units.
- Tier limits: 2–512x acceleration and 16–4,096 parallel lanes. Actual work remains limited by native machine conditions, materials and supplied power.
- On-demand AE2 crafting and pattern-free passive operation.
- Optional LDLib previews, JEI/EMI structure views and ME Access integration.
- Empty-hand right-click cell ejection, stored-size tooltips and physical restoration through the original virtual spatial pylon.
- Supported Mekanism containers restore to their native capacities. Excess assets stay in a server-owned cell ledger and can refill the original equipment later, without duplicating the factory.

**Experimental Alpha:** back up existing worlds and start in a new world. Aggregate parallel support and crash recovery have explicit compatibility boundaries; universal machine compatibility or identical workload for every setup is not promised. Internal AE2 crafting CPUs and dedicated Induction Matrix acceleration are excluded.
Survival recipes for the modular parts and tiers are not complete. This Alpha is intended for construction with parts obtained from its creative tab.

## Artwork

Original AE2 Machines models and artwork are supplemented by selected AE2 19.2.17 spatial cell and spatial I/O assets.
Applied Energistics 2 contributors; artwork credited by AE2 to Ridanisaurus Rid. Those imported assets retain their [CC BY-NC-SA 3.0 license](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See `LICENSE_ASSETS.txt` included in the downloads. AE2 Machines is an independent addon, not an official AE2 release.
