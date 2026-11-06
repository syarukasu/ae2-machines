# AE2 Machines

**0.1.3 Pre-release** | Minecraft 1.20.1 Forge / 1.21.1 NeoForge

## 日本語

AE2で構築した工場を空間セルへ格納し、空間演算器で加速・並列化するアドオンです。
加工の正本は元機械です。パターンの出力を直接生成するのではなく、実際の素材・電力消費と加工結果を使います。

### 機能

- 仮想化空間標識、仮想化空間セル、横方向に増設できる空間演算器。
- 仮想P2Pと仮想電力ポートによる、セル内の工場と外側のME・電力系統の接続。
- 通常のAE2発注と、加工パターン不要のパッシブ加工。
- 同じ加工依頼を数量付きでまとめる受付と、設備・接続構成の再利用。
- 元機械の数量指定処理、または同じ状態を個数付きの代表にまとめる並列処理。
- 正確な時間契約を持つ加工の区間集約。加速のためにAE2やチャンク管理まで繰り返しません。
- LDLibのセル内部・演算器プレビュー、JEI/EMIの構造表示、ME Accessの任意連携。
- セルベイからの回収、格納寸法表示、元の標識への復元。対応コンテナの容量を超える余剰はセルに保持します。

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

T1加速は等速です。T9同士の理論積は16,777,216倍で、実測の達成倍率ではありません。
実際の稼働量は素材・実受電・出力先・元機械の条件に従います。

### 導入

**AE2は必須、ACOは任意連携です。**
対応するMinecraft・ローダーの本体JARを、クライアントとサーバーの双方へ導入してください。
Mekanism、Applied Mekanistics、Create、GTCEu、LDLib、JEI/EMI、ME Accessは利用する連携に応じて追加します。
他MODを同梱したり、GTを必須にしたりするものではありません。

### 0.1.3の範囲

加工依頼の集約、接続情報の再利用、加工時刻の分離、加速中の構造部材処理の軽量化を含みます。
BigInteger会計と既存APIを維持しています。
原子炉・ボイラー・タービンの熱と搬送には本家の逐次処理が残り、単体と同じ負荷を保証する版ではありません。

Sは加工側へ適用します。昼夜、エンティティ、作物のランダム成長は倍率分進めません。
内部AE2 CPUとInduction Matrix専用の加速・並列化は対象外です。
任意の外部設備をまたぐ全体復旧、全機種の自動集約、全構成の20 TPSは保証しません。
ACO Instantの別候補は同梱していません。

変更点と確認範囲は [CHANGELOG](CHANGELOG.md)、実装境界は [Processing work](docs/PROCESSING_WORK.md)、
追加連携APIは [Native Time API](docs/NATIVE_TIME_API.md) を参照してください。

**実験的プレリリースです。既存ワールドをバックアップし、新規ワールドで確認してください。**
サバイバルレシピは未整備です。構成部品はクリエイティブタブから入手できます。

## English

Capture an ME-connected factory in a virtual spatial cell and operate its original
machines inside a modular spatial computer. AE2 is required on both sides.
ACO and other integrations remain optional; no third-party mod is bundled.

Version 0.1.3 groups identical unstarted requests, reuses provider and equipment
routes, separates processing time from world/AE time, and reduces repeated
housekeeping during accelerated thermal execution. Original machine IO, energy
and actual outputs remain authoritative. Existing APIs and BigInteger accounting
are preserved.

This is not universal automatic aggregation or constant-load acceleration.
Native heat and transport transitions still scale with acceleration. S does not
multiply daylight, entities or random crop growth. Internal AE2 CPUs and dedicated
Induction Matrix acceleration are excluded. Arbitrary external crash recovery and
20 TPS for every factory are not guaranteed. ACO Instant changes are separate.

**Experimental pre-release: back up worlds and start with a new test world.**
Survival recipes remain incomplete. Private test fixtures, worlds, logs and local
development dependencies are excluded. See [building instructions](docs/BUILDING_ALPHA.md).

## Artwork

Selected AE2 19.2.17 spatial-cell and spatial-I/O assets are credited to Applied
Energistics 2 contributors and Ridanisaurus Rid, retaining their
[CC BY-NC-SA 3.0 license](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See LICENSE_ASSETS.txt. This is an independent addon, not an official AE2 release.
