package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;

/** Loaded only when Applied Mekanistics supplies its chemical AEKey type. */
public final class MekGasPipeAccess {
    private MekGasPipeAccess() {}
    public static boolean accepts(AEKey key) {
        return key instanceof MekanismKey chemical && chemical.getStack() instanceof mekanism.api.chemical.gas.GasStack;
    }
    public static GenericStack contents(TileEntityPressurizedTube tube) {
        var stack=tube.getTransmitter().getTransmitterNetwork().getBuffer().getChemicalStack();
        return stack.isEmpty()?null:new GenericStack(MekanismKey.of(stack),stack.getAmount());
    }
    public static void restoreShare(TileEntityPressurizedTube p,AEKey key,long amount) {
        var stack=amount==0?mekanism.api.chemical.gas.GasStack.EMPTY:
                (mekanism.api.chemical.gas.GasStack)((MekanismKey)key).withAmount(amount);
        p.getTransmitter().saveShare=mekanism.api.chemical.merged.BoxedChemicalStack.box(stack);
        p.getTransmitter().getGasTank().setStack(stack);
    }
}
