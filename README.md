# AE2 Machines

**0.1.2-dev.2 Pre-release** | Minecraft 1.20.1 Forge / 1.21.1 NeoForge

## 日本語

AE2で構築した工場を空間セルへ格納し、空間演算器で加速・並列化するアドオンです。
加工は元の機械が行い、素材・電力・触媒・副産物を独自のアイテム変換で置き換えません。

### 機能

- 仮想化空間標識、仮想化空間セル、横方向に増設できる空間演算器。
- セル内の仮想P2P・仮想電力ポートを介した、実際のMEネットワークと電力系統への接続。
- 通常のAE2発注と、加工パターン不要のパッシブ稼働。
- 対応機械の数量指定処理、または同じ状態を個数付きの代表へまとめる並列処理。Q台分を一律には複製しません。
- 元機械の一括更新契約を使う共通Q/S経路。Qは状態の個数、Sは状態内の経過時間として別々に扱います。
- LDLibによるセル内部・演算器のプレビュー、JEI/EMIの構造表示、ME Accessの任意連携。
- セルベイからの回収、格納寸法表示、元の標識への現実復元。対応コンテナの余剰はセルへ保持します。

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

T1加速は等速です。T9同士の理論積は16,777,216倍ですが、実測の達成倍率ではありません。
実際の稼働量は素材・実受電・出力先・元機械の条件に従います。
旧512倍など上限を超える設定は、資産を切り捨てず停止します。

### 依存関係

**AE2は必須、ACOは任意連携です。** 両側に対応するローダーの本体JARを導入してください。
LDLib、JEI/EMI、ME Access、Mekanism、Applied Mekanistics、Create、GTCEuは利用する連携に応じて追加します。
他MODは同梱しません。今回GTを必須依存にはしていません。

### 今回の変更と範囲

共通スケジューラの加工機ごとの分岐を、`NativeTimeParticipant` / `NativeTimeMachine` の契約へ接続しました。
GTの並列・サブティック処理を参考にしていますが、GTのレシピや消費電力へ置換する実装ではありません。
元機械の一括更新が正確であることを保証できない場合は、元のtick処理へ戻ります。
既存API・BigInteger会計・Q保存形式を維持しています。

**全機種が自動で集約される版ではありません。機種別処理の全面撤去、熱共有回路の汎用集約は未完了です。**
原子炉などの検証済み既存経路と安全チェックは残しています。内部AE2 CPUとInduction Matrix専用の加速・並列化は対象外です。
単体と常に同じ負荷、任意の外部設備をまたぐ全体復旧、全構成での20TPSを保証しません。

共通契約を使う検証機械では、Q=10,000・S=256で素材・電力・時刻を照合し、両版の平均ホストtick時間は約18～19 msでした。
long超え、不均等状態、セル再装着、強制終了復旧も確認しています。実機種全般の性能へ読み替えることはできません。
物理T9演算器での最大Q/S構成そのものの統合実加工試験を意味する結果でもありません。
詳細は [CHANGELOG](CHANGELOG.md) と [Native Time API](docs/NATIVE_TIME_API.md) を参照してください。

**実験的プレリリースです。既存ワールドはバックアップし、新規ワールドで確認してください。**
サバイバルレシピは未整備です。構成部品はクリエイティブタブから入手できます。

## English

Capture an ME-connected factory in a virtual spatial cell and run the original
machines inside a modular spatial computer. Processing is not replaced by direct
item conversion. AE2 is required on both sides; ACO and other integrations remain
optional. GT is a design reference, not a new mandatory dependency.

This snapshot adds common native Q/S contracts. Q groups independent identical
states; S advances time within each state. Original machine IO, power and state
remain authoritative. Without an exact contract, original native ticks remain in
use. Existing public APIs, BigInteger accounting and Q saves are preserved.

This is not universal automatic aggregation. General shared-heat circuit
aggregation and removal of all specialised compatibility paths are unfinished.
Existing validated thermal execution remains. No universal single-machine cost,
20 TPS or arbitrary external recovery guarantee is made. Internal AE2 CPUs and
dedicated Induction Matrix acceleration are excluded.

**Experimental pre-release: back up worlds and start testing in a new world.**
Survival recipes are incomplete. Private acceptance fixtures, worlds, logs and
local development dependencies are excluded from this source snapshot; see
[building instructions](docs/BUILDING_ALPHA.md).

## Artwork

Selected AE2 19.2.17 spatial-cell and spatial-I/O assets are credited to Applied
Energistics 2 contributors and Ridanisaurus Rid, retaining their
[CC BY-NC-SA 3.0 license](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See `LICENSE_ASSETS.txt`. This is an independent addon, not an official AE2 release.
