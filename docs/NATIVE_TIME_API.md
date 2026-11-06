# Common native Q/S execution

## Current processing-only scheduler

Version 0.1.3 separates processing time from world/AE time.
See [Processing work execution](PROCESSING_WORK.md) for current scheduling,
cache invalidation, recovery and verification boundaries. The public interfaces
below remain compatible. The old whole-world scheduling description and measured
dev.2 results below are historical, not current performance claims.

## Historical 0.1.2-dev.2 design and evidence

## Direction

GTCEu Modern's [ParallelLogic](https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/src/main/java/com/gregtechceu/gtceu/api/recipe/modifier/ParallelLogic.java)
and [sub-tick overclocking](https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/src/main/java/com/gregtechceu/gtceu/api/recipe/OverclockingLogic.java)
are design references, not runtime dependencies or copied energy formulas.
Q is multiplicity of independent states. S is time within each state. Original
machine code, its IO and its energy accounting remain authoritative.

Normal captured-world execution accepts native equipment without a recipe or
machine-name adapter. The common time scheduler now discovers a native contract,
not a furnace/Mekanism machine allowlist. A block's material name is no longer a
condition for skipping an otherwise inert non-random/non-fluid interval.

The chunk schedule is shared by all native cell worlds. It no longer requires a
Mekanism thermal batch. It preserves native holder order, pending-future fallback,
ticket/topology invalidation and rebuilds at each host-tick boundary.

## Contracts

`NativeTimeParticipant` supplies a read-only `Step(limit, advance, rollback)`.
An interval is bounded by world/AE2 events and by every participant's own limit.
The implementation must preserve original processing, all state, IO, intermediate
events and exact energy. Returning null retains original native ticks.

`NativeTimeMachine` adds this time contract to the existing `NativeParallelMachine`
local-state/audit/independence contract. No runtime machine-name registration is
needed. Q uses one representative per distinct saved state, not Q BlockEntities.
Each representative's original time update runs once per interval; its actual
energy consumption/generation is audited and weighted by its BigInteger copies.

```java
public final class MyMachine extends BlockEntity implements NativeTimeMachine {
    // Implement the existing nativeParallelContract(): empty configured state,
    // exact assets, original sided IO/energy, and certified independence.

    @Override
    public Step nativeTimeStep(int maximumTicks) {
        if (!canAdvanceExactlyWithoutWorldEvents()) return null;
        int boundary = nextOriginalProcessingEvent(maximumTicks);
        return new Step(boundary, ticks -> originalMachineAdvance(ticks));
    }
}
```

The example methods are machine-owned implementation placeholders, not addon APIs.
An addon-side recipe coefficient must never replace `originalMachineAdvance`.
For a local Q machine the runtime snapshots and restores all owned state; the
two-argument Step constructor needs no duplicate rollback implementation. A plain
NativeTimeParticipant must supply a complete rollback itself.

`SubtickCycles.plan(progress, duration, time, executableCycles, poweredWork)` is a
BigInteger work allocator. Its result is complete native cycles, remaining progress
and actual elapsed work. The original machine determines executableCycles from
its own material/output conditions and poweredWork from actual available power.
The allocator neither consumes inventory nor creates outputs.

## Failure and Recovery

Q planning is checked for saved-state mutation. Execution checks native energy
deltas; nonlocal world writes are rejected by the existing local execution scope.
Invalid Q/time work restores owned images, quarantines that cell and retains its
pending time debt. Persistence failures remain fatal; they are not hidden by a
normal fallback. Native world time/debt are rolled back with a failed time span.

Original public API classes and saved generic-Q schemas 1/2 are unchanged. New
interfaces are additive. ACO is still optional. There is no new dependency on GT.

## Deliberate Boundaries

This is not evidence that every unknown Java ticker can be reduced to one call.
Without a truthful exact time contract it continues through original native ticks.
Existing certified furnace/Mekanism intervals are connected through the shared
contract; their safety checks are retained. Specialised native thermal/chemical/GT
compatibility paths are not deleted merely to remove class names, which would
regress proven work or break ownership. They remain compatibility optimisations,
not a prerequisite for accepting an arbitrary factory into a cell.

In particular, a reactor/boiler/turbine circuit shares heat and storage. It must not
falsely declare `localStateOnly` or be multiplied as independent individual blocks.
The existing original thermal execution remains in use. A general exact thermal
circuit time contract, and constant-load acceleration of every unknown machine,
are not delivered by this local Q/S API.

## Verification

The acceptance harnesses and worlds referenced in this section are private and
are not included in the public source snapshot. Published results describe those
fixtures, not an automatically reproducible public test suite. The native API and
production implementation are included; see BUILDING_ALPHA.md for build inputs.

`tools/validate-generic-time.ps1` runs both loaders without opening a client.
It compares original native-tick and aggregate-time execution at Q=1/10000 and
S=1/256, checks quantities/energy/time during continuous processing, validates
long-exceeding uneven native state groups against a separate original-ticker
full-NBT oracle, and checks rollback, reattachment and failure isolation.
It also checks legacy Q saves, furnace/Mekanism routes and abrupt checkpoint
recovery, including rejection of stale witnesses after aggregated work.

See the generated `build/generic-time-final1.json` manifest for the actual run
status and individual logs. Test-only native machines and benchmark code are not
included in release JARs. Timing comparisons describe that fixture, not arbitrary
modpacks or a universal constant-load guarantee.

### Observed Results

Final runtime/build matrix: `generic-time-final1`, verified on both loaders. The
native control disables only the new local time aggregation in the same source;
it is not a comparison against a different mod release. Each row below includes
97 whole-server host-tick spans and exactly 24832 native-time ticks of paid work.

| Loader | Q | S | Native control mean ms | Aggregate mean ms | p95 ms | Native update calls before/after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Forge 1.20.1 | 1 | 256 | 920.322 | 17.813 | 19.709 | 24832 / 194 |
| Forge 1.20.1 | 10000 | 256 | 891.400 | 18.014 | 20.648 | 49664 / 388 |
| NeoForge 1.21.1 | 1 | 256 | 864.376 | 19.044 | 22.190 | 24832 / 194 |
| NeoForge 1.21.1 | 10000 | 256 | 938.310 | 18.919 | 21.188 | 49664 / 388 |

At S=1 both routes execute the same native-tick path; observed means range from
7.595 to 9.546 ms. The single-run spread includes small regressions as well as
improvements, so no S=1 performance gain is asserted. Q=10000 uses at most two
representatives in this equally supplied fixture, not 10000 instances. It is not
a bound on the number of states in arbitrarily heterogeneous factories.

The long-exceeding Q=2^63 case uses uneven native inventories caused by bounded
long IO calls. Its result is checked against 256 original ticker calls per
distinct state, comparing full weighted native NBT and energy, not an assumption
that every copy received the same input.

New time-work fault tests confirm local rollback/quarantine, retained time debt
and a live server. Abrupt JVM-stop tests confirm coordinated resumption and
rejection of an expired witness after additional aggregated work. Original public
API classes (13) remain byte-identical, saved-Q schemas 1/2 load, and neither tests
nor dependencies are bundled. Both production JARs built as 0.1.2-dev.2.

Separate regression matrix `generic-time-thermal1` also passed with Extras and
Evolved on both loaders. For the original reactor/boiler/turbine circuit, all six
native master trace records and both endpoint records match the circuit-disabled
control exactly at S=1/256 (9/2304 native ticks). This verifies preservation of the
existing thermal path, not a new generic constant-load thermal implementation.

Raw manifests, comparisons and hashes are packaged in
`build/distributions/0.1.2-dev.2/verification.json`. No published 0.1.1 JAR, Prism
instance or production world was changed; no new release was uploaded.
