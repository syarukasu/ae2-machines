package com.syaru.ae2machines.platform;

import appeng.api.storage.MEStorage;
import appeng.parts.AEBasePart;
import appeng.parts.PartAdjacentApi;
import appeng.capabilities.Capabilities;
import java.util.function.Supplier;

public final class CapturedStorageAccess {
    private CapturedStorageAccess(){}
    public static PartAdjacentApi<MEStorage> create(AEBasePart part,Supplier<MEStorage> target) {
        return new PartAdjacentApi<>(part,Capabilities.STORAGE) {
            @Override public MEStorage find(){return target.get();}
        };
    }
}
