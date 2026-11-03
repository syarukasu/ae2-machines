# AE2 Machines 0.1.0 Alpha

Minecraft **1.20.1 Forge** / **1.21.1 NeoForge**。対応するファイルを1つだけ導入してください。

AE2の工場を空間セルに格納し、横増設式の演算器で元の機械を加速・並列化する初期Alpha版です。
AE2は必須、ACOは任意連携です。

- 仮想化空間標識・セル・仮想P2P・内外の電力ポート・空間演算器とT1～T9の演算ユニット。
- セルベイの空手右クリック取り出し、格納寸法表示、コントローラーからの操作。
- LDLibの工場・構造プレビューとカメラ移動、JEI/EMI・ME Accessの任意連携。
- 現実への復元では、対応Mekanismコンテナの通常容量分を設備へ戻し、余剰をセルに保持。空いた元設備へ後から補充できます。
- 復元・回収中の保存台帳、配管の保存用分配量、未反映の熱の取り扱いを修正。

**Alpha版です。既存ワールドはバックアップし、新規ワールドでの確認から始めてください。**
構成部品・ティアのサバイバルレシピは未整備のため、現段階ではクリエイティブタブから部品を入手して構築してください。
全機種の無条件な集約並列、任意の外部設備をまたぐ復旧、すべての構成での同一負荷を保証する版ではありません。
内部AE2 CPUとInduction Matrix専用の加速・並列化は対象外です。未完了の加工・未受領の出力・未知の保存形式では、安全側で復元を拒否する場合があります。

## English

Initial experimental Alpha for **Minecraft 1.20.1 Forge** and **1.21.1 NeoForge**. Install only the matching download, on both the client and server. Applied Energistics 2 is required; ACO integration is optional.

Includes spatial factory capture, a modular computer with tiered acceleration/parallel units, virtual ME/power endpoints, optional previews and integrations, empty-hand cell-bay ejection and stored-size tooltips.

Supported Mekanism containers physically restore to native capacity; excess assets remain in a server-owned cell ledger for later refill into the original equipment. Material, electrical and thermal accounting stay separate. Native pipe save shares and deferred-heat persistence are handled during restoration and recovery.

**Back up existing worlds and start in a new world.** Compatibility, aggregate parallel processing and recovery have explicit boundaries. This Alpha does not promise universal machine support, identical workload for every setup or crash recovery across arbitrary external equipment. Internal AE2 crafting CPUs and dedicated Induction Matrix acceleration are excluded.
Survival recipes for modular parts and tiers are incomplete; this Alpha targets construction using the creative tab.
