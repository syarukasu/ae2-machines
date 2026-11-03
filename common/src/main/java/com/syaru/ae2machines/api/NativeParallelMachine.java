package com.syaru.ae2machines.api;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional contract for independent, local native machine states. No recipe/output multiplier. */
public interface NativeParallelMachine {
    int API_VERSION=1;
    Contract nativeParallelContract();

    interface Contract {
        String id();
        int revision();
        /** Explicitly certifies that the native ticker mutates only its own saved state. */
        boolean localStateOnly();
        /**
         * Opt-in for identical independent saved states: native tick and sided IO are
         * deterministic, identity-independent and have no random/shared/per-call effects.
         * A representative's actual transition may stand for copies of that exact state.
         */
        default boolean independentCopies(BlockEntity source){return false;}
        /** Configured native NBT with NO materials, stored energy, paid work or pending outputs. */
        CompoundTag emptyLane(BlockEntity source);
        default net.minecraft.world.level.block.state.BlockState emptyBlockState(BlockEntity source){return source.getBlockState();}
        /** Must include every owned material, fuel, hidden buffer and in-flight reservation. */
        Assets audit(BlockEntity lane);
        /** The original machine's sided IO, including its native filters and limits. */
        MEStorage storage(BlockEntity lane,Direction side);
        String energyUnit();
        default BigInteger unitsPerFE(){return BigInteger.ONE;}
        default boolean energyInput(Direction side){return false;}
        default boolean energyOutput(Direction side){return false;}
        /** Returns actual accepted native units. Simulation must not mutate any state. */
        default BigInteger insertEnergy(BlockEntity lane,BigInteger amount,boolean simulate){return BigInteger.ZERO;}
        default BigInteger extractEnergy(BlockEntity lane,BigInteger amount,boolean simulate){return BigInteger.ZERO;}
        /** Override when native acceptance/limits differ by face; null denotes explicit unsided access. */
        default BigInteger insertEnergy(BlockEntity lane,Direction side,BigInteger amount,boolean simulate){return insertEnergy(lane,amount,simulate);}
        default BigInteger extractEnergy(BlockEntity lane,Direction side,BigInteger amount,boolean simulate){return extractEnergy(lane,amount,simulate);}
        /** Consumers need no extra implementation; generators must report both native totals. */
        default EnergyWork energyWork(CompoundTag before,Assets a,CompoundTag after,Assets b){
            return new EnergyWork(a.energy().subtract(b.energy()),BigInteger.ZERO);
        }
    }
    record Assets(Map<AEKey,BigInteger> materials,BigInteger energy,boolean idle){
        public Assets {
            Objects.requireNonNull(energy);if(energy.signum()<0)throw new IllegalArgumentException("Negative native energy");
            var copy=new HashMap<AEKey,BigInteger>();materials.forEach((key,n)->{
                Objects.requireNonNull(key);Objects.requireNonNull(n);if(n.signum()<0)throw new IllegalArgumentException("Negative native material");
                if(n.signum()>0)copy.put(key,n);
            });materials=Map.copyOf(copy);
        }
        public boolean empty(){return idle && energy.signum()==0 && materials.isEmpty();}
    }
    record EnergyWork(BigInteger consumed,BigInteger generated){
        public EnergyWork {
            Objects.requireNonNull(consumed);Objects.requireNonNull(generated);
            if(consumed.signum()<0 || generated.signum()<0)throw new IllegalArgumentException("Undeclared native generation/consumption");
        }
    }
}
