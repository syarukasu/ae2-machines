package com.syaru.ae2machines.platform;
public final class MekVariableEnergy {
    private MekVariableEnergy(){}
    public static java.math.BigInteger maximum(){return java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(10000));}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static String recipeWitness(Object value,net.minecraft.world.level.Level level){
        var recipe=(net.minecraft.world.item.crafting.Recipe)value;var serializer=recipe.getSerializer();if(serializer==null)return "";
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{serializer.streamCodec().encode(buffer,recipe);var bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);var tag=new net.minecraft.nbt.CompoundTag();tag.putByteArray("recipe",bytes);tag.putString("class",recipe.getClass().getName());tag.putString("serializer",String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer)));return com.syaru.ae2machines.world.NbtWitness.digest(tag);}
        catch(UnsupportedOperationException unsupported){return "";}finally{buffer.release();}
    }
    public static void unpause(mekanism.api.recipes.cache.CachedRecipe<?> recipe){recipe.unpauseErrors();}
    public static void restoreUsage(mekanism.common.tile.prefab.TileEntityConfigurableMachine machine,mekanism.api.recipes.cache.CachedRecipe<?> recipe,int index){
        if(recipe instanceof mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe<?,?> used
                && machine instanceof mekanism.common.recipe.lookup.IRecipeLookupHandler.ConstantUsageRecipeLookupHandler handler)used.loadSavedUsageSoFar(handler.getSavedUsedSoFar(index));
    }
    public static Object savedRecipe(mekanism.common.tile.prefab.TileEntityConfigurableMachine machine,String id){
        if(id.isEmpty())return null;
        var type=com.syaru.ae2machines.compat.mek.MekVariableWork.call(machine,"getRecipeType");
        var recipes=(java.util.List<?>)com.syaru.ae2machines.compat.mek.MekVariableWork.call(type,"getRecipes",machine.getLevel());
        return recipes.stream().map(r->(net.minecraft.world.item.crafting.RecipeHolder<?>)r).filter(r->r.id().toString().equals(id)).map(net.minecraft.world.item.crafting.RecipeHolder::value).findFirst().orElse(null);
    }
}
