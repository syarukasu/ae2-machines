package com.syaru.ae2machines.api;

import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.runtime.*;
import java.math.BigInteger;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native capability providers may delegate here outside a lane's own ticker/IO callback. */
public final class NativeParallelAccess {
    private NativeParallelAccess(){}
    public static boolean isManaged(BlockEntity source){return !NativeParallelContext.active() && NativeParallelRuntime.owns(source);}
    public static MEStorage storage(BlockEntity source,Direction side){return NativeParallelContext.active()?null:NativeParallelRuntime.storage(source,side);}
    public static NativeParallelRuntime.Snapshot snapshot(BlockEntity source){if(NativeParallelContext.active())return null;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?null:r.snapshot(source.getBlockPos());}
    public static NativeParallelRuntime.ExactSnapshot exactSnapshot(BlockEntity source){if(NativeParallelContext.active())return null;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?null:r.exactSnapshot(source.getBlockPos());}
    public static NativeParallelRuntime.Grouping grouping(BlockEntity source){if(NativeParallelContext.active())return null;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?null:r.grouping(source.getBlockPos());}
    public static BigInteger insertEnergy(BlockEntity source,BigInteger amount,boolean simulate){if(NativeParallelContext.active())return BigInteger.ZERO;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?BigInteger.ZERO:r.insertEnergy(source.getBlockPos(),amount,simulate);}
    public static BigInteger extractEnergy(BlockEntity source,BigInteger amount,boolean simulate){if(NativeParallelContext.active())return BigInteger.ZERO;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?BigInteger.ZERO:r.extractEnergy(source.getBlockPos(),amount,simulate);}
    public static BigInteger insertEnergy(BlockEntity source,Direction side,BigInteger amount,boolean simulate){if(NativeParallelContext.active())return BigInteger.ZERO;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?BigInteger.ZERO:r.insertEnergy(source.getBlockPos(),side,amount,simulate);}
    public static BigInteger extractEnergy(BlockEntity source,Direction side,BigInteger amount,boolean simulate){if(NativeParallelContext.active())return BigInteger.ZERO;var r=NativeParallelRuntime.get(source);return r==null || !NativeParallelRuntime.owns(source)?BigInteger.ZERO:r.extractEnergy(source.getBlockPos(),side,amount,simulate);}
}
