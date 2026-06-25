# Options OMS — JVM Settings Changes (shards 11/12/13/14)

**Box:** `xcelxeqt95p` — 376 GB RAM, JDK 17 + G1GC
**Heap plan:** shard 13 stays **64 GB**; shards 11/12/14 → **48 GB** each.

> Note: at 48 GB the other shards also cross the 32 GB compressed-oops line, so the
> alignment change below now applies to all four, not just shard 13.

---

## Changes

| Shard(s) | Change | Action |
|---|---|---|
| 11/12/13/14 | `-XX:G1HeapRegionSize=7m` | **Remove** (let it default — 16m at 48g, 32m at 64g) |
| 11/12/13/14 | `-XX:ObjectAlignmentInBytes=16` | **Add** — *dev-test first* |
| 11/12/14 | heap size | Set `-Xmx48g -Xms48g` |
| 13 | heap size | Keep `-Xmx64g -Xms64g` |

No heap-dump flag (see below). All other flags unchanged. Each change needs a restart.

---

## Why

**Remove `G1HeapRegionSize=7m`.** It was sized for the old 14 GB heap. At 48–64 GB it
forces ~9,000+ regions vs G1's ~2,048 target and drops the humongous threshold to
3.5 MB, so normal options messages get allocated as humongous — the cause of the
`G1 Humongous Allocation` concurrent cycles in shard 13's log. Oracle's stated fix is
exactly this: increase (or stop pinning) the region size so those objects follow the
normal path.
- Oracle HotSpot G1 tuning guide: <https://www.oracle.com/technical-resources/articles/java/g1gc.html>
- Oracle Garbage-First GC Tuning: <https://docs.oracle.com/javase/9/gctuning/garbage-first-garbage-collector-tuning.htm>

**Add `ObjectAlignmentInBytes=16`.** Above ~32 GB the JVM disables compressed object
pointers (`Compressed Oops: Disabled` in shard 13's log), inflating every reference.
`=16` restores them up to ~64 GB. It's a real trade-off (larger object padding), so it
must be validated in dev before prod.
- Shipilëv, Object Alignment: <https://shipilev.net/jvm/anatomy-quarks/24-object-alignment/>
- Shipilëv, Compressed References: <https://shipilev.net/jvm/anatomy-quarks/23-compressed-references/>
- Atlassian KB (avoid 32–47 GB heaps): <https://support.atlassian.com/confluence/kb/dont-use-heap-sizes-between-32-gb-and-47-gb-in-confluence-java-compressed-oops/>

**No heap-dump flag.** `-XX:+HeapDumpOnOutOfMemoryError` is valuable for leaks, but at
48–64 GB the dump is live-set-sized (tens of GB) and can fill the log volume. Shard 13
was confirmed undersized, not leaking, so the value is low right now. Add only if a
future OOM looks leak-shaped.

---

## Validation

Dev-replay a heavy options day, then confirm in the gc.log: compressed oops re-enabled,
no `G1 Humongous Allocation` cycles, pause times and post-GC floor unchanged or better.
Roll one shard at a time; keep prior launch config for rollback.
