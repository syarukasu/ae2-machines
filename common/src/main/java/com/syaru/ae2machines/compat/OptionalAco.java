package com.syaru.ae2machines.compat;

/** No static reference to ACO on the mandatory mod-loading path. */
public final class OptionalAco {
    private OptionalAco() {}
    public static boolean available() {
        return legacyProcessing() || transactional();
    }
    public static boolean legacyProcessing() {
        try {
            Class<?> api = Class.forName("com.syaru.ae2craftingoptimizer.api.processing.ProcessingPatternBatchTarget", false,
                    OptionalAco.class.getClassLoader());
            return api.getField("API_VERSION").getInt(null) == 1;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | LinkageError e) {
            return false;
        }
    }
    public static boolean transactional() {
        try {
            var api=Class.forName("com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api",false,OptionalAco.class.getClassLoader());
            return api.getField("API_VERSION").getInt(null)==4;
        }catch(ClassNotFoundException|NoSuchFieldException|IllegalAccessException|LinkageError error){return false;}
    }
    public static boolean exactProcessing() {
        try {Class.forName("com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget",false,OptionalAco.class.getClassLoader())
                .getMethod("aco$supportsExactPattern",appeng.api.crafting.IPatternDetails.class);return true;}
        catch(ReflectiveOperationException|LinkageError absent){return false;}
    }
    public static void initialize() {
        if(!transactional() || legacyProcessing())return;
        try {
            Class.forName("com.syaru.ae2machines.compat.aco.MachinesBatchAdapter").getMethod("register").invoke(null);
            if(exactProcessing())Class.forName("com.syaru.ae2machines.compat.aco.AcoWorldBatchAdapter").getMethod("register").invoke(null);
        }
        catch(ReflectiveOperationException error){throw new IllegalStateException("ACO Batch V2 integration could not register",error);}
    }
}
