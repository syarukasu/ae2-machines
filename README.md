# AE2 Machines

**0.1.4 Pre-release** | Minecraft 1.20.1 Forge / 1.21.1 NeoForge

## 日本語

AE2で構築した工場を仮想化空間セルへ格納し、空間演算器で元の機械を動かすアドオンです。
加工パターンの出力を直接生成するのではなく、元機械の素材・電力消費と実際の加工結果を使います。

### 機能

- 仮想化空間標識、空間セル、横方向に増設できる空間演算器。
- 仮想P2Pと仮想電力ポートによる、外側のME・電力系統とセル内部の接続。
- AE2からのオンデマンド加工と、加工パターン不要のパッシブ加工。
- 同じ加工依頼の数量集約、設備と接続情報の再利用。
- LDLibのセル内部・演算器プレビュー、JEI/EMIの構造表示、ME Accessの任意連携。
- セルの回収と元の標識への復元。対応容器の通常容量を超えた余剰はセルに保持します。

### 並列設定

| Tier | 並列演算器 | 多重演算コア |
| --- | ---: | ---: |
| T1 | 16 | 2 |
| T2 | 64 | 4 |
| T3 | 256 | 8 |
| T4 | 1,024 | 16 |
| T5 | 4,096 | 32 |
| T6 | 8,192 | 64 |
| T7 | 16,384 | 128 |
| T8 | 32,768 | 256 |
| T9 | 65,536 | 512 |

T9同士の設定上限は33,554,432並列です。多重演算コアは台数倍率であり、OSスレッド数ではありません。
現在のマルチブロックは等速で稼働します。実際の稼働量は素材・受電・出力先・元機械の条件に従います。

### 導入と注意

**AE2は必須、ACOは任意連携です。** 対応する本体JARをクライアント・サーバー双方へ導入してください。
Mekanism、Applied Mekanistics、Create、GTCEu、LDLib、JEI/EMI、ME Accessなどは利用する連携に応じて追加します。
Cooking for Blockheadsも任意です。

最大並列の実機試験では、流体量のint上限によりボイラー・タービン系統が停止する問題が見つかりました。
0.1.4では、セル内の対応するMekanism流体の合計量をBigIntegerで保持し、配管・ボイラー・タービンのint量制限への対処を追加しました。修正後の実環境再試験は今回は行っていません。
全機種の自動集約や、最大並列での性能・完走を保証する版ではありません。
原子炉の並列台数は基準炉の温度状態を共有します。
内部AE2 CPU、Induction Matrix専用の並列化、任意の外部設備をまたぐ全体復旧は保証対象外です。

**実験的プレリリースです。最終版の実行時試験は省略しています。**
既存ワールドをバックアップしてください。拡張流体量を保存したワールドの直接ダウングレードは避けてください。
サバイバルレシピは未整備です。構成部品はクリエイティブタブから入手できます。
変更点・制約・確認状況は [CHANGELOG](CHANGELOG.md) を参照してください。

## English

Capture an ME-connected factory in a virtual spatial cell and run its original
machines in a modular spatial computer. AE2 is required on both sides; ACO and
other integrations remain optional. No third-party mod is bundled.

The current modular setup uses normal-speed processing and multiplicative virtual
parallel cores, with a configured T9/T9 maximum of 33,554,432 lanes. This is not
an OS thread count or a throughput guarantee. Native recipes, paid energy and
actual machine outputs remain authoritative. Fission lanes share a reference
thermal state.

The maximum-parallel trial exposed an int fluid-quantity ceiling that stalled
the boiler/turbine circuit. Version 0.1.4 addresses it with extended fluid
accounting and aggregated native Mekanism fluid transfers. Final runtime
acceptance was not rerun. It is not universal machine
support or crash-recovery certification. Back up worlds and do not downgrade
worlds containing extended fluid quantities. See [CHANGELOG](CHANGELOG.md) and
[building instructions](docs/BUILDING_ALPHA.md).

## Artwork

Selected AE2 19.2.17 spatial-cell and spatial-I/O assets are credited to Applied
Energistics 2 contributors and Ridanisaurus Rid, retaining their
[CC BY-NC-SA 3.0 license](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See LICENSE_ASSETS.txt. This is an independent addon, not an official AE2 release.
