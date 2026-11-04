# AE2 Machines

**0.1.1 Alpha** | Minecraft 1.20.1 Forge / 1.21.1 NeoForge

## 日本語

AE2で構築した工場を空間セルへ格納し、空間演算器で加速・並列化するアドオンです。
加工は元の機械が行います。素材・電力・触媒・副産物を独自のアイテム変換で置き換えません。

### 機能

- 仮想化空間標識、仮想化空間セル、横方向に増設できる空間演算器。
- セル内の仮想P2P・仮想電力ポートを通した、実際のMEネットワークと電力系統への接続。
- 通常のAE2発注と、加工パターン不要のパッシブ稼働。
- 対応機械の数量指定処理、または同じ状態を代表へまとめる並列処理。設備を並列数分複製しません。
- LDLibによるセル内部・演算器の立体プレビュー、JEI/EMIの構造表示、ME Accessの任意連携。
- セルベイの空手右クリック取り出し、格納寸法表示、コントローラーからの操作。
- 元の標識への現実復元。対応Mekanismコンテナの余剰はセルへ保持し、後から元設備へ補充。

### 演算器の上限

| Tier | 並列Q | 加速S |
| --- | ---: | ---: |
| T1 | 16 | 1 |
| T2 | 64 | 2 |
| T3 | 256 | 4 |
| T4 | 1,024 | 8 |
| T5 | 4,096 | 16 |
| T6 | 8,192 | 32 |
| T7 | 16,384 | 64 |
| T8 | 32,768 | 128 |
| T9 | 65,536 | 256 |

加速T1は等速です。T9同士の理論最大は16,777,216倍ですが、実測の達成倍率ではありません。
実際の稼働量は素材・実受電・出力先・元機械の条件に従います。
旧512倍など新上限を超える保存済み設定は勝手に丸めず、上限内へ変更するまで停止します。

### 依存関係

**Applied Energistics 2は必須、ACOは任意連携です。** クライアント・サーバーの両方に導入してください。
LDLib、JEI/EMI、ME Access、Mekanism、Applied Mekanistics、Create、GTCEuは利用する連携に応じて追加します。他MODは同梱しません。

### Alpha版の範囲

**既存ワールドはバックアップし、まず新規ワールドで確認してください。**
構成部品・ティアのサバイバルレシピは未整備です。クリエイティブタブから入手して構築するテストプレイを想定しています。

0.1.1では加速中の構造走査、状態が変わらない構成部品の更新、チャンク候補の重複走査を削減します。
本家の加工・発電・熱・実搬送処理は維持します。並列・加速を常に単体と同じ負荷で動かせる版ではありません。

新T9上限（65,536並列×256倍速）での実加工完走は未確認です。未知機種の集約並列には、状態・入出力・保存の独立性を確認できる連携契約が必要です。
未対応処理を加速だけで並列対応と表示しません。未確認の追加処理では本家経路へ戻ります。
Evolvedの保存・アップグレード用Mixinは、確認したクラス内容に限って集約と併用します。内容が異なる場合は安全側で戻します。

内部AE2クラフトCPUとInduction Matrix専用の加速・並列化は対象外です。
任意の外部倉庫・外部CPU・電源をまたぐ異常終了復旧、全MOD・全構成での動作や20TPSを保証する版ではありません。

## English

Capture an ME-connected factory in a virtual spatial cell and run it inside a
modular spatial computer. Original machines perform their own processing;
material, power and byproduct rules are not replaced with direct item conversion.

AE2 is required on both sides. ACO and other integrations are optional.
Tier limits now range from 16 to 65,536 parallel lanes and from normal speed to
256x acceleration. T1 acceleration is normal speed. The theoretical T9 product
is 16,777,216, not a measured throughput guarantee. Saved settings above the new
hardware cap pause without being silently truncated.

This update reduces repeated structural work during acceleration. It does not
guarantee single-machine cost, universal aggregate parallel support, 20 TPS or
recovery across arbitrary external equipment. The new maximum has not been
verified in a complete maximum-Q/S processing run. Unknown addon callbacks retain native fallback;
only inspected serialization/upgrade callback binaries receive a narrow exception.
Internal AE2 CPUs and dedicated Induction Matrix acceleration remain excluded.

**Experimental Alpha: back up existing worlds and start in a new world.**
Survival recipes for modular parts and tiers are incomplete; obtain parts from
the creative tab for testing.

## Artwork

Original AE2 Machines artwork is supplemented by selected AE2 19.2.17 spatial
cell and spatial I/O assets. Applied Energistics 2 contributors; artwork credited
by AE2 to Ridanisaurus Rid. Imported assets retain their
[CC BY-NC-SA 3.0 license](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See `LICENSE_ASSETS.txt`. This is an independent addon, not an official AE2 release.
