package com.syaru.ae2machines.compat.mek;

/** A native class name alone does not authorize folding foreign injected callbacks. */
public final class MekThermalHooks {
    private static final String COBBLE_HOOK="com.mekanismoptimizer.mixin.TileEntityMekanismMixin";
    private static final String COBBLE_ENGINE="com.mekanismoptimizer.core.CobblestoneSupplyEngine";
    private static final String ITEM_LOOKUP="com.syaru.ae2craftingoptimizer.mixin.MekanismItemLookupIntentMixin";
    private static final String ITEM_RESOLVER="com.syaru.ae2craftingoptimizer.mekanism.MekanismItemRecipeIntent";
    private static final String CHUNK_ANCHOR="appeng.mixins.chunkloading.ChunkMapMixin";
    private static final String SOLAR_LOOKUP="com.mekanismoptimizer.mixin.TileEntitySolarNeutronActivatorMixin";
    // Client/distribution and development mappings have distinct bytecode for vanilla calls.
    private static final java.util.Map<String,java.util.Set<String>> BINARIES=java.util.Map.ofEntries(
        java.util.Map.entry("com.lowdragmc.lowdraglib.core.mixins.BlockEntityMixin",java.util.Set.of("47b98fca521e7d436a5812e8e00df7e0cc0b51720bb8177584ec7db625e5927b","158e571f4faf55087c5f0e67a2cda7a517b834626ecb10e00963d33016f352e0")),
        java.util.Map.entry(COBBLE_HOOK,java.util.Set.of("44642ab2e61e30a7bc2a1b9a1cca4a37d1e2efffa56c1cd63e08b25c7f003a10")),
        java.util.Map.entry(COBBLE_ENGINE,java.util.Set.of("e562f77f0083a1201f7f8da2b7b043a5ecc79b9ceab726f3a603582259d047c1","76c38ab40467fde694d41d63ec8a9d42954ee20aeb6f796c394394259d78356e")),
        // Rendering/audio only. Neither method participates in a server tile tick.
        java.util.Map.entry("com.mekanismoptimizer.mixin.TileEntityMekanismClientMixin",java.util.Set.of("37733eea3b0545db73c2e08a6dc2cc58545bba1762c85c90fef566b54073fc19")),
        java.util.Map.entry("com.mekanismoptimizer.mixin.TileEntityBoundingBlockMixin",java.util.Set.of("2ba7a0747607471a8fb7372a0788ec7165d53d1ae058513926191192a97ac83e")),
        // EntityCulling 1.11.3 adds only private rendering state and its accessors, no native tick hook.
        java.util.Map.entry("dev.tr7zw.entityculling.mixin.CullableMixin",java.util.Set.of("0358d59e38285c368c42d18bff6a8f54acba6bdfbcbc40333aa8ed1c8a6e4a79")),
        java.util.Map.entry(SOLAR_LOOKUP,java.util.Set.of("c7d473cb5918ff01b79bf78e26eca8ef09af1f4d841a15b9c5b3b797504940eb")),
        java.util.Map.entry("fr.iglee42.evolvedmekanism.mixins.tiles.TileEntityMekanismMixin",java.util.Set.of("1f89b2bef5ed80df73be70a51945116e7e33a04f94cbfc19886e0665220da66b")),
        java.util.Map.entry("fr.iglee42.evolvedmekanism.mixins.tiles.cables.TileEntityMechanicalPipeMixin",java.util.Set.of("4a9b8d184cefd95566373e29e01d17d43849c0b6605e3ccebe5f71aecc805f68","a996d27971a774f5770848a8aadee2d2e0913bea640d965d4c72817fcbea63e8")),
        java.util.Map.entry("fr.iglee42.evolvedmekanism.mixins.tiles.cables.TileEntityPressurizedTubeMixin",java.util.Set.of("3b7c71b549e8950e1d200940d6cbb309ca5419e30feef3920c2e4c386aa1167d","392c5bbd4733a1ce4ac6cb0c07648a599704baeb853d3b37d8a0abf5a6f4a29e")),
        // This binary only maps cable upgrades to a replacement block state; it has no tick hook.
        java.util.Map.entry("fr.iglee42.evolvedmekanism.mixins.tiles.cables.TileEntityUniversalCableMixin",java.util.Set.of("a899d42637ac27e2e333f9068eef2ee64b9556d0ad0cdf72af71d3619d5eaeb3")),
        java.util.Map.entry(ITEM_LOOKUP,java.util.Set.of("0727125f0a14c4f132fa6bc0720cbf27999f1bea142f8199f38457fe4c4e0297")),
        java.util.Map.entry(ITEM_RESOLVER,java.util.Set.of("c1e193872502bfc1506c8c29e111e685f3eeab42432340ea51146637c05eb110","9d27b1dd074200b356624c37533cde75c18d7ac6370405f589439f23f0a83263")),
        java.util.Map.entry(CHUNK_ANCHOR,java.util.Set.of("942cc4fa3f4a55c0c249714058adbd4cfad6c5bef18c5588f8c18dd726cfb50a","bfdaaf7b8f0c148d4aef074d15e793ba8695d00842bc42c048bc0848d8b87980","ae279f83c152b178cd53c72083ebe65706cdc8afc5ef2b7a451d8c33a38031b3")));
    private static final java.util.Map<String,Boolean> AUDITED=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean audited(String owner){
        if(!BINARIES.containsKey(owner))return false;
        return AUDITED.computeIfAbsent(owner,key->{
            try(var stream=MekThermalHooks.class.getClassLoader().getResourceAsStream(key.replace('.','/')+".class")){
                return stream!=null && acceptsDigest(key,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }catch(java.io.IOException|java.security.NoSuchAlgorithmException|RuntimeException unavailable){return false;}
        });
    }
    static boolean acceptsDigest(String owner,String digest){return BINARIES.getOrDefault(owner,java.util.Set.of()).contains(digest);}
    private record Audit(String refusal,boolean cobble,boolean lookup,boolean chunkAnchor,boolean retainedOnly){}
    private static final ClassValue<Audit> SAFE=new ClassValue<>(){
        @Override protected Audit computeValue(Class<?> type){
            try{
                boolean cobble=false,lookup=false,chunkAnchor=false,retainedOnly=false;
                for(var current=type;current!=null;current=current.getSuperclass())for(var method:current.getDeclaredMethods())
                    for(var annotation:method.getDeclaredAnnotations())if(annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")){
                        var owner=(String)annotation.annotationType().getMethod("mixin").invoke(annotation);
                        if(owner.startsWith("com.syaru.ae2machines.mixin."))continue;
                        if(owner.equals(COBBLE_ENGINE) || owner.equals(ITEM_RESOLVER) || !audited(owner))return new Audit(owner,false,false,false,false);
                        cobble|=owner.equals(COBBLE_HOOK);
                        lookup|=owner.equals(ITEM_LOOKUP);
                        chunkAnchor|=owner.equals(CHUNK_ANCHOR);
                        retainedOnly|=owner.equals(SOLAR_LOOKUP);
                    }
                return new Audit(null,cobble,lookup,chunkAnchor,retainedOnly);
            }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return new Audit(unavailable.getClass().getName(),false,false,false,false);}
        }
    };
    private static final class ItemLookup {
        private static final java.lang.reflect.Method SIZE=find();
        private static java.lang.reflect.Method find(){
            if(!audited(ITEM_RESOLVER))return null;
            try{
                var registry=Class.forName("com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry",false,MekThermalHooks.class.getClassLoader());
                if(!safe(registry))return null;
                return registry.getMethod("size");
            }catch(ReflectiveOperationException|LinkageError unavailable){return null;}
        }
        static boolean empty(){
            if(SIZE==null)return false;
            try{return ((Number)SIZE.invoke(null)).intValue()==0;}
            catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}
        }
    }
    private record Witness(java.lang.reflect.Field initialized,java.lang.reflect.Field upgrade){}
    private static final class Cobble {
        static final Witness WITNESS=witness();
        static volatile boolean inert;
        static Witness witness(){
            if(!audited(COBBLE_ENGINE))return null;
            var loader=MekThermalHooks.class.getClassLoader();
            try{
                try{Class.forName("astral_mekanism.enums.AMEUpgrade",false,loader);return null;}
                catch(ClassNotFoundException absent){/* No registered Astral upgrade in this mod set. */}
                var engine=Class.forName(COBBLE_ENGINE,false,loader);var audit=SAFE.get(engine);
                if(audit.refusal()!=null || audit.cobble())return null;
                var init=engine.getDeclaredField("ameChecked");var upgrade=engine.getDeclaredField("cobblestoneUpgrade");
                return init.trySetAccessible() && upgrade.trySetAccessible()?new Witness(init,upgrade):null;
            }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return null;}
        }
        static boolean idle(){
            if(inert || WITNESS==null)return inert;
            try{
                // Its native synchronized initializer must have run, and no upgrade can appear
                // after the mod classpath was fixed. Do not execute its optional tick to establish this.
                synchronized(WITNESS.initialized().getDeclaringClass()){
                    return inert=WITNESS.initialized().getBoolean(null) && WITNESS.upgrade().get(null)==null;
                }
            }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}
        }
    }
    private MekThermalHooks(){}
    public static boolean safe(Class<?> type){
        var audit=SAFE.get(type);return audit.refusal()==null && !audit.chunkAnchor() && !audit.lookup() && !audit.retainedOnly() && (!audit.cobble() || Cobble.idle());
    }
    public static boolean chunkSaveSafe(Class<?> type){
        // The audited AE2 anchor hook only changes spawning/random-tick eligibility. Those calls
        // remain native; accepting it here must not authorize folding their clock or chunk work.
        var audit=SAFE.get(type);return audit.refusal()==null && !audit.cobble() && !audit.lookup() && !audit.retainedOnly();
    }
    public static boolean processingSafe(Class<?> type){
        var audit=SAFE.get(type);
        // Pending ACO intents can expire between completions. Do not hide those selection boundaries.
        return audit.refusal()==null && !audit.chunkAnchor() && !audit.retainedOnly() && (!audit.cobble() || Cobble.idle()) && (!audit.lookup() || ItemLookup.empty());
    }
    public static boolean chunkSelectionSafe(Class<?> type){
        // Only the holder selection is reused. The anchor's random-tick eligibility check,
        // native shuffle and chunk work still execute on every virtual tick.
        return type==net.minecraft.server.level.ChunkMap.class && chunkSaveSafe(type);
    }
    public static boolean retainedWorkSafe(Class<?> type){
        var audit=SAFE.get(type);
        // These machines still run every original tick. Recipe-intent expiry and solar-cache
        // deadlines are not crossed silently; only other tiles' structural clocks are folded.
        return audit.refusal()==null && !audit.chunkAnchor() && (!audit.cobble() || Cobble.idle())
                && (!audit.lookup() || audited(ITEM_RESOLVER));
    }
    public static String reason(Class<?> type){
        var audit=SAFE.get(type);
        if(audit.refusal()!=null)return audit.refusal();
        if(audit.chunkAnchor())return "AE2_ANCHOR_ONLY_AUTOSAVE_AUDITED";
        if(audit.retainedOnly())return "OBSERVER_REQUIRES_NATIVE_TICKS";
        if(audit.lookup())return "ACO_ITEM_LOOKUP_REQUIRES_EMPTY_INTENT_REGISTRY";
        return audit.cobble() && !Cobble.idle()?"ACTIVE_OR_UNVERIFIED_COBBLE_HOOK":"NONE";
    }
}
