# GDB-SPEC v3.50 — Open Questions, Assumptions & Risks Register

## 1. Open Questions (OQ)
* **OQ-001 (Storage Block Alignment):** Does the target filesystem for page spill require explicit direct O_DIRECT / 4096-byte sector alignment on Android flash storage, or should page cache rely on standard NIO buffered channels with fsync?
  * *Current Resolution/Assumption:* Use NIO FileChannels with explicit `force(true)` (fsync) and explicit 4096-byte chunk boundaries, ensuring portable operation across all Android environments while preserving durability.
* **OQ-002 (GPU Acceleration Backend on Android):** For the GPU execution backend on Android devices, should OpenCL, Vulkan Compute, or RenderScript/NNAPI be preferred when available?
  * *Current Resolution/Assumption:* Provide safe CPU Fallback as the primary reliable reference backend. Abstract the ComputeBackend interface so Vulkan Compute can be plugged in without destabilizing core ACID semantics.

## 2. Documented Assumptions (ASM)
* **ASM-001 (Bounded Memory Constraint):** Mobile Android runtimes impose a strict heap budget (often 256MB–512MB per process). The Global Memory Governor must enforce an upper bound strictly below the max runtime heap to guarantee safety under multi-gigabyte or terabyte dataset streaming.
* **ASM-002 (Anti-Fake Telemetry):** Android standard Linux kernel procfs `/proc/stat` may be restricted by SELinux on modern API levels (Android 8+). If CPU/GPU percentages are inaccessible, telemetry MUST report `UNSUPPORTED` rather than generating artificial estimations.

## 3. Risks & Mitigations (RSK)
* **RSK-001 (Crash During WAL Flush):** Power loss or sudden termination during WAL write could leave partial records.
  * *Mitigation:* WAL records include an 8-byte magic header, record length, LSN, payload, and CRC32C trailing checksum. Replay aborts cleanly at the first corrupted or truncated record without affecting committed state.
* **RSK-002 (Buffer Cache Thrashing on Large Scans):** Large sequential scans could evict hot metadata and index pages.
  * *Mitigation:* Segmented allocation classes in MemoryGovernor with dedicated `PAGE_CACHE` vs `PREFETCH` vs `METADATA` budgets and clock/LRU priority.
