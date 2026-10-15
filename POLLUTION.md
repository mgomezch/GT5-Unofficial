# GTNH Pollution System Rework & Pollution Mitigation Multiblocks

This document describes the reworked pollution system in GregTech 5 Unofficial (GT5U) for GT New Horizons, including chunk vertical height tracking, distance falloff mechanics, and the dedicated pollution mitigation multiblocks (**Chimney** and **Industrial Smoke Stack**).

---

## 1. Chunk Pollution Height Tracking

Historically, GTNH chunks stored pollution purely as an integer quantity ($P \in [0, 2\times 10^9]$) with no spatial coordinate inside the chunk. The reworked system associates each chunk's pollution with a **vertical altitude (y-height)**:

1. **Undefined at Zero Pollution**:
   When a chunk has $0$ pollution, its height is undefined (`Float.NaN`).
2. **Weighted-Average Height Updates**:
   Whenever new pollution is emitted into a chunk, the updated pollution height $H_{\text{new}}$ is calculated as the weighted average of the existing pollution and the newly added pollution:
   $$H_{\text{new}} = \frac{P_{\text{old}} \cdot H_{\text{old}} + P_{\text{add}} \cdot H_{\text{add}}}{P_{\text{old}} + P_{\text{add}}}$$
   If existing pollution $P_{\text{old}} = 0$ or $H_{\text{old}}$ is undefined, $H_{\text{new}} = H_{\text{add}}$.
3. **Inter-Chunk Propagation**:
   When pollution spreads to adjacent chunks during world ticking, the transferred pollution carries the source chunk's height and blends into the destination chunk's height using the exact same weighted average formula.
4. **Data Storage & Migration**:
   Region data storage (`GTChunkAssociatedData`) uses format version 1:
   - Writes `amount` (int) followed by `height` (float).
   - Automatically migrates version 0 saves (reads integer amount and assigns `Float.NaN` until new emissions occur).

---

## 2. Vertical Distance Diminishing Effect

Pollution hazards (smog, poison, confusion, blindness, and vegetation decay) do not affect all vertical levels equally. If a polluting factory or chimney vents at $y = 150$, players and crops at ground level ($y = 65$) suffer significantly reduced effects.

The probability of an effect triggering on an entity or block is scaled by:
$$f(\Delta y) = 0.2^{\frac{|\Delta y|}{50.0}}$$

- **At the pollution altitude ($\Delta y = 0$)**: $f(0) = 1.0$ (100% effect).
- **At 50 blocks vertical distance ($\Delta y = 50$)**: $f(50) = 0.2$ (20% effect).
- **At 100 blocks vertical distance ($\Delta y = 100$)**: $f(100) = 0.04$ (4% effect).
- If pollution has no defined height (`Float.NaN`), $f = 1.0$ (unscaled legacy behavior).

---

## 3. Chimney Multiblock

The **Chimney** is an early-game primitive multiblock that draws ground pollution and vents it into the upper atmosphere.

- **Controller**: `Machine_Multi_Chimney` (ID: 12024), placed at the **bottom center**.
- **Dimensions**: $3 \times 25 \times 3$ vertical hollow brick tower (height 25 blocks).
- **Operation**:
  - Requires a burning furnace at the **bottom front center** (`Blocks.furnace` or `Blocks.lit_furnace`).
  - When the furnace is lit, every 5 seconds (100 ticks) it removes up to **500 pollution** from the chunk and re-emits it at the top of the chimney ($y_{\text{base}} + 24$).
- **Hatches**:
  - **Top layer outer ring**: Accepts up to 1 optional Maintenance Hatch.
  - **Top center**: Optionally accepts a Muffler Hatch. If present, the muffler reduces the re-emitted pollution according to its tier rating while preserving the atmospheric height venting effect.
- **Visuals**: Emits smoke particles from the top center when active (if no muffler is installed).

---

## 4. Industrial Smoke Stack Multiblock

The **Industrial Smoke Stack** is an active, powered multiblock designed for large-scale industrial pollution management.

### Tiers & Physical Specifications

| Tier | Voltage / Amperage | Total Height | Structure Groups | Manhattan Range | Max Pollution / Cycle (1A) | Max Pollution / Cycle (4A) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | LV (32 EU/t @ 1A) | 30 blocks | 1 group: 3x3 hollow tower (30 blocks) | 1 chunk ($\le 5$ chunks) | 2,000 | 8,000 |
| **Tier 2** | MV (128 EU/t @ 1A) | 60 blocks | 2 groups: 5x5 hollow base (30) + 3x3 hollow top (30) | 2 chunks ($\le 13$ chunks) | 8,000 | 32,000 |
| **Tier 3** | HV (512 EU/t @ 1A) | 90 blocks | 3 groups: 7x7 hollow base (30) + 5x5 middle (30) + 3x3 top (30) | 3 chunks ($\le 25$ chunks) | 32,000 | 128,000 |

### Power Supply Options
The structure **must** have either Steam power OR EU power in its bottom two layers ($y=0$ and $y=1$):
1. **Steam Power**:
   - Accepts up to 8 Steam Hatches (`MTEHatchCustomFluidBase` / `GregtechItemList.Hatch_Input_Steam` or Steam Input Hatches).
   - Consumes steam equivalent to 1 amp of on-tier power ($1 \text{ EU} = 2 \text{ L steam}$).
   - Generates a **white smoke** decal plume.
2. **Electric Power**:
   - Accepts 1 to 4 single-amp Energy Hatches matching the machine's tier (LV for T1, MV for T2, HV for T3).
   - Multi-amp energy hatches and off-tier hatches are strictly rejected.
   - 1 hatch consumes 1 amp (base throughput); 4 hatches consume 4 amps (next-tier throughput).
   - Generates a **black soot** decal plume.
3. **Mutual Exclusivity**:
   - Structure will not form if both steam hatches and energy hatches are present, or if neither is present.

### Spatial Efficiency & Math
1. **Horizontal Manhattan Distance Falloff**:
   Cleaning efficiency decreases linearly from 100% at the center chunk to 50% at the maximum range $d_{\text{max}} = \text{tier}$:
   $$\text{eff}_{\text{dist}}(d) = 1.0 - 0.5 \times \frac{d}{d_{\text{max}}}$$
2. **Vertical Distance Diminishing Efficiency**:
   Pollution available to draw from a chunk decreases exponentially based on the vertical distance between the controller and the chunk's pollution height:
   $$f(\Delta y) = 0.5^{\frac{|\Delta y|}{H / 2}} = 2^{-\frac{|\Delta y|}{H / 2}}$$
   Reaches 50% efficiency at half the smoke stack's structural height ($15$ blocks for T1, $30$ blocks for T2, $45$ blocks for T3).

### Powerfail Penalty (Accumulated Pollution Ejection)
As the machine operates and displaces pollution, it tracks an internal accumulation counter:
- Accumulates displaced pollution up to a maximum cap of **$100 \times \text{maxPollutionPerCycle}$**.
- Persists across chunk unloads and server restarts.
- **Power Failure**: If the machine runs out of EU or steam while active, it immediately dumps **all** accumulated pollution directly at the controller's ground level ($y_{\text{controller}}$) in the center chunk.

### Structure Check Optimization
Because of the massive vertical height of these towers (up to 90 blocks), structure checks run only once every **15 seconds** (300 ticks).

### Construction Materials & Mod Compatibility
- Primarily built from **Concrete** (`GregTechAPI.sBlockConcretes`, meta 0 default for hologram/auto-building).
- The structure check dynamically accepts:
  - Any GregTech concrete variant (smooth, chiseled, brick, dark, light, reinforced).
  - Industrial Strength Concrete (`BlockCasings9`, meta 3).
  - Any **Chisel** concrete block (`chisel:concrete` variants).
  - Any **ArchitectureCraft** block whose underlying material is concrete.

### Wind-Directed Visual Decal
- Renders at the top opening using a semi-transparent floating decal made of soft circular particle disks.
- Automatically reads the local wind speed and direction from **IndustrialCraft 2's WindSim** (`WorldData.get(world).windSim.getWindAt(topHeight)`).
- The smoke plume offsets in real time according to the wind vector.
- Plume color dynamically reflects power source: **white** for steam power, **dark grey/black** for electricity.

---

## 5. Pollution Incentives for Multiblock Machines

To incentivize pollution management and reward building tall chimneys and industrial smoke stacks, GregTech multiblock machines suffer performance and maintenance degradation when operating in polluted environments at machine altitude.

### At-Height Adjusted Gibbl Score
Multiblock penalties evaluate the pollution intensity at the machine's actual vertical coordinate:
$$G = P_{\text{chunk}} \times 0.5^{\frac{|\Delta y|}{30.0}} = P_{\text{chunk}} \times 2^{-\frac{|y_{\text{machine}} - y_{\text{pollution}}|}{30.0}}$$

- **Zero Vertical Distance ($\Delta y = 0$)**: Machine experiences 100% of the chunk's pollution ($G = P$).
- **30 Blocks Distance ($\Delta y = 30$)**: Machine experiences 50% of the chunk's pollution ($G = 0.5 \cdot P$).
- **60 Blocks Distance ($\Delta y = 60$)**: Machine experiences 25% of the chunk's pollution ($G = 0.25 \cdot P$).
- **Undefined Pollution Altitude**: If no altitude is recorded, $G = P_{\text{chunk}}$.

### Effect 1: Machine Efficiency Penalties
Operating in high adjusted pollution decreases a multiblock's running efficiency ($m\text{Efficiency}$):
1. **First 10% Decrease ($G \le 50,000$)**:
   - Efficiency decreases by **1% (100 efficiency points out of 10,000) for every 5,000 adjusted gibbl**.
   - At $50,000$ adjusted gibbl, the penalty reaches $10\%$ ($1,000$ points).
2. **Beyond 50,000 Gibbl ($G > 50,000$)**:
   - Efficiency decreases by **1% for every 10,000 adjusted gibbl** beyond $50,000$:
   $$\text{Penalty (\%)} = 10.0\% + \frac{G - 50,000}{10,000} \times 1.0\%$$
3. **Machine Mechanics**:
   - The efficiency penalty stacks directly with maintenance problem penalties.
   - **Power Consumers**: Inversely increases energy consumption via $\text{ActualEnergyUsage} = \frac{-\text{EUt} \times 10,000}{\max(1000, m\text{Efficiency})}$. A 10% efficiency drop results in $\sim 11.1\%$ higher power draw.
   - **Power Generators**: Linearly reduces EU generation via $\frac{\text{EUt} \times m\text{Efficiency}}{10,000}$.
   - **Production Speeds**: Affects multiblocks that scale output or tick rate by $getCurrentEfficiency()$.
   - **Scanner / Portable Diagnostics**: Portable scanner and Waila displays report both the degraded efficiency and the exact pollution penalty: `Pollution Efficiency Penalty: -X%`.

### Effect 2: Increased Maintenance Problem Frequency
High adjusted pollution accelerates mechanical wear and tear:
- **Doubling Rate**: Every **2,000 adjusted at-height gibbl** doubles the frequency of random maintenance problems:
  $$M = 2^{\frac{G}{2000.0}}$$
- The baseline maintenance check (1 in 6,000 chance per 1,000 ticks) is scaled by $M$:
  $$P(\text{problem}) = \min\left(1.0, \frac{1}{6000} \times 2^{\frac{G}{2000.0}}\right)$$
- At $G = 2,000$: maintenance problems are $2\times$ as frequent.
- At $G = 4,000$: maintenance problems are $4\times$ as frequent.
- At $G = 6,000$: maintenance problems are $8\times$ as frequent.
- At $G \ge 25,100$: maintenance problems trigger reliably every check cycle until repaired.

