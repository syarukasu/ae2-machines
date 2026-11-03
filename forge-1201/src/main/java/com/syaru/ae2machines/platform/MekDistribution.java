package com.syaru.ae2machines.platform;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import mekanism.common.lib.distribution.*;
import mekanism.common.util.EmitUtils;
import java.util.List;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Mekanism's native fair-share algorithm, with captured material endpoints. */
public final class MekDistribution {
    private MekDistribution() {}
    public static long send(AEKey key,long amount,List<MEStorage> endpoints,IActionSource source) {
        if(amount<=0 || endpoints.isEmpty())return 0;
        var target=new Target<MEStorage,Long,AEKey>(endpoints) {
            @Override protected Long simulate(MEStorage storage,AEKey resource) {
                long n=storage.insert(resource,amount,Actionable.SIMULATE,source);
                require(n>=0 && n<=amount,"PIPE_SIMULATION_AMOUNT");return n;
            }
            @Override protected void acceptAmount(MEStorage storage,SplitInfo<Long> split,Long n) {
                long accepted=storage.insert(key,n,Actionable.MODULATE,source);
                require(accepted>=0 && accepted<=n,"PIPE_INSERTION_AMOUNT");split.send(accepted);
            }
        };
        return EmitUtils.sendToAcceptors(target,amount,key);
    }
}
