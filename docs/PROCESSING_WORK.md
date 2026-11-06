# Processing work execution (0.1.3)

## Current boundary

1. Native AE2 requests use a counted provider bridge. The identity includes the
   actual provider, native pattern (including components/NBT), recipe revision
   and destination machine. Identical unstarted requests can append to one job.
   Input holders, native CPU waiting amounts and native operation allowances
   remain authoritative. No new CPU or transfer throttle is added.
2. A pattern is a request, not a receipt. The original machine processor must
   validate/consume the exact inputs and paid energy. Only its actual outputs
   are delivered. Q and owned work quantities remain BigInteger.
3. Provider routes and prepared native processors are reused. Pattern changes
   invalidate that provider; equipment changes invalidate adjacent/recorded
   dependents. Mekanism's native side-configuration, upgrade and redstone-control
   change callbacks invalidate only the relevant dependency set; request lookup
   does not poll or copy every machine's NBT. Chunk reloads, recipe reloads,
   cell reattachment and spanning GT
   structural changes rebuild the affected cell index. No cross-grid inventory
   cache or discovery is introduced.
4. S applies to processing, not to a repeated ServerLevel tick. There are no
   additional AE2 tick events, chunk-management ticks, weather updates or
   global mod tick callbacks in the accelerated processing loop.

## Native execution

NativeProcessingSchedule retains native ticker order and caches resident
processing membership. Exact NativeTimeParticipant/NativeTimeMachine intervals
advance original machine state in aggregate. Existing counted work ledgers run
once on the host clock; their occupied machines are not ticked a second time.
Uncertain processing retains ordered original machine callbacks, not whole-world
replay. Local thermal shell aggregation is retained; heat-producing masters and
material networks still execute their original transitions.

The persisted processing clock is separate from the world/AE clock. A native
machine callback sees processing time; LevelData, native scheduled block/fluid
events and AE2 TickTracker registration use the unscaled world clock. Daylight,
entities and random world growth are not accelerated by S. This is an intentional
processing-only boundary, not a claim of accelerated arbitrary world simulation.

Original NativeTimeParticipant and NativeParallelMachine APIs are unchanged.
Legacy clock saves initialize processing time from their former world time.
An old clock recovery witness is accepted only for an actually loaded legacy
clock, before new work; new work cannot validate against that old witness.

## Ownership and recovery

Counted native CPU handoffs use the existing CPU before-image and journal
transaction. Identical work is appended only before execution starts. Native
blocking, crafting locks, plan receivers and ambiguous output routes retain
their original path. Unsupported recovery participants do not enter the counted
path. Outputs are native receipts, not multiplied pattern declarations.

The native counted handoff retains the existing external-only transaction
boundary. An internal ME drive/other internal storage participant is not silently
added to that transaction: such a route stays on native AE2 admission until a
multi-world custody protocol is verified. Internal AE2 CPUs remain excluded.

Time-contract mutation/errors restore the owned state, quarantine the affected
cell and retain unpaid processing debt. Persistence failure is still fatal.
Cell detach/reload and the coordinated native/external checkpoint protocol remain
in use. This does not establish recovery for arbitrary undeclared external assets.

## Verification boundary

The private headless harness uses real AE2 CPU/provider/cell/Mekanism work without
ACO. It checks Q=1 and Q=65,536, S=1/256, finite power, split admission, reload,
native receipts, route restrictions and provider-local invalidation. It also
toggles real input faces, speed upgrades and redstone control, checking that only
the affected route is rebuilt and the current native recipe is used. The large
CPU admission test supplies a test-only native operation allowance; production
does not create additional co-processors or bypass the CPU allowance.

The generic time harness checks native full-state results, Q=1/10,000 and Q=2^63,
S=1/256, exact energy/materials, stable schedules, unscaled world time, scheduled
events, rollback and faulty-contract isolation. Thermal tests use real finite
fuel, sodium circulation, boiler water/steam and turbine generation/condensation.
Thermal ledger timing includes detailed auditing and is not a Spark TPS claim.
Forced-process-exit tests cover the existing coordinated thermal checkpoint and
stale-checkpoint rejection on both loaders. The new counted CPU path currently
has clean partial-work reload coverage; those thermal tests do not prove an
arbitrary crash during a new counted CPU handoff.

ACO remains optional. No ACO, third-party optimizer, production world or installed
Prism JAR is changed by this candidate. ACO Instant work is a separate candidate;
it is not bundled or claimed as completed here.

This candidate does not demonstrate constant-load acceleration for every unknown
machine or nonlinear thermal circuit. It eliminates repeated world/AE management
and aggregates declared processing work; it does not guess recipe success.
