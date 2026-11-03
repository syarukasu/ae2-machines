package com.syaru.ae2machines.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.Recipe;
import java.util.Optional;

/** Only a successful native factory compiler may provide this capability. */
public interface CompiledProgram {
    default java.math.BigInteger nativeLanes(IPatternDetails pattern) { return java.math.BigInteger.ONE; }
    Optional<Recipe<AEKey>> resolve(IPatternDetails pattern);
    boolean boundaryActive();
}
