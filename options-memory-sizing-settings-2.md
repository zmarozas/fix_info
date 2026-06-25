# Options OMS — Memory Sizing & Settings Changes

**Box:** `xcelxeqt95p` (options) — 376 GB RAM, swap ≈ 0
**Shards:** 11, 13 (primary); 12, 14 (backup) — JDK 17 + G1GC

---

## Summary

- **Shard 13 — keep at 64 GB.** Its end-of-day working set is ~32 GB, so 64 GB ≈ 2×
  headroom (correctly sized). The earlier OOM was an undersized 14 GB heap, **not a
  leak**. Collector is healthy — no Full GCs under load.
- **Shards 11/12/14 — raise from 20 GB to 31 GB each.** Efficient headroom bump that
  stays under the compressed-oops cliff. Box absorbs it with large margin.
- **Hard ceiling: ~70 GB per shard** (all three at once). 31 GB is the recommendation;
  do not exceed 70.

---

## Settings to change

**Shard 13** (next planned restart):

| Change | From | To | Why |
|---|---|---|---|
| Heap flags | `-Xmx14g … -Xmx64g` (duplicated) | `-Xmx64g -Xms64g` | Remove stale duplicate. |
| `G1HeapRegionSize` | `7m` | remove (defaults to 32m) | 7m was sized for the old 14g heap; causes humongous-allocation cycles at 64g. |
| `ObjectAlignmentInBytes` | — | `16` *(dev-test first)* | Restores compressed oops, disabled above the 32 GB heap threshold. |
| Heap dump | — | `+HeapDumpOnOutOfMemoryError` + `HeapDumpPath` | Safety net for any future OOM. |

Keep: `+UseG1GC`, `+AlwaysPreTouch`, `MaxGCPauseMillis=100`, tenuring, Metaspace.

**Shards 11/12/14:** set heap to **31 GB** (`-Xmx31g -Xms31g`); remove any
`G1HeapRegionSize=7m` carryover; add the same heap-dump flags.

---

## Memory ceiling — the math

`+AlwaysPreTouch` pins each heap fully resident at startup, and swap ≈ 0 (no safety
net — over-commit kills a trading process). So hold the box at **≤ 80% = ~301 GB**.

| Fixed (unchanged) | GB |
|---|---|
| Shard 13 (64g + off-heap) | 70.6 |
| Off-heap of 11/12/14 | ~10.8 |
| 4 faxers | ~1.1 |
| OS / monitoring | ~6.5 |
| **Fixed total** | **~89** |

Budget for the three heaps = 301 − 89 = **212 GB → ~70 GB per shard (hard ceiling).**

At the recommended **31 GB each**: 89 + 93 = **182 GB (≈ 48% of box)** — large margin.

**Avoid 32–47 GB** on any shard: above 32 GB heap the JVM drops compressed pointers,
so you get more heap but *less* usable memory.

---

## Before raising 11/12/14

Confirm with platform that no failover scenario could move an additional shard onto
this box. All four current shards are already resident, so normal HA promotion adds
no memory — but a co-located 5th shard would need its footprint reserved first.
