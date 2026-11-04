package com.syaru.ae2machines.compat.mek;

/** A native class name alone does not authorize folding foreign injected callbacks. */
public final class MekThermalHooks {
    // Audited Evolved 1.3 callbacks only serialize containers or handle a player tier upgrade.
    // They do not run in the omitted native server tick. Changed binaries remain untrusted.
    private static final java.util.Map<String,java.util.Set<String>> NON_TICKING=java.util.Map.of(
        "fr.iglee42.evolvedmekanism.mixins.tiles.TileEntityMekanismMixin",java.util.Set.of("1f89b2bef5ed80df73be70a51945116e7e33a04f94cbfc19886e0665220da66b"),
        "fr.iglee42.evolvedmekanism.mixins.tiles.cables.TileEntityMechanicalPipeMixin",java.util.Set.of("4a9b8d184cefd95566373e29e01d17d43849c0b6605e3ccebe5f71aecc805f68","a996d27971a774f5770848a8aadee2d2e0913bea640d965d4c72817fcbea63e8"),
        "fr.iglee42.evolvedmekanism.mixins.tiles.cables.TileEntityPressurizedTubeMixin",java.util.Set.of("3b7c71b549e8950e1d200940d6cbb309ca5419e30feef3920c2e4c386aa1167d","392c5bbd4733a1ce4ac6cb0c07648a599704baeb853d3b37d8a0abf5a6f4a29e"));
    private static final java.util.Map<String,Boolean> AUDITED=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean nonTicking(String owner){
        if(!NON_TICKING.containsKey(owner))return false;
        return AUDITED.computeIfAbsent(owner,key->{
            try(var stream=MekThermalHooks.class.getClassLoader().getResourceAsStream(key.replace('.','/')+".class")){
                return stream!=null && acceptsDigest(key,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }catch(java.io.IOException|java.security.NoSuchAlgorithmException|RuntimeException unavailable){return false;}
        });
    }
    static boolean acceptsDigest(String owner,String digest){return NON_TICKING.getOrDefault(owner,java.util.Set.of()).contains(digest);}
    private static final ClassValue<Boolean> SAFE=new ClassValue<>(){
        @Override protected Boolean computeValue(Class<?> type){
            try{
                for(var current=type;current!=null;current=current.getSuperclass())for(var method:current.getDeclaredMethods())
                    for(var annotation:method.getDeclaredAnnotations())if(annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")){
                        var owner=(String)annotation.annotationType().getMethod("mixin").invoke(annotation);
                        if(!owner.startsWith("com.syaru.ae2machines.mixin.") && !nonTicking(owner))return false;
                    }
                return true;
            }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}
        }
    };
    private MekThermalHooks(){}
    public static boolean safe(Class<?> type){return SAFE.get(type);}
}
