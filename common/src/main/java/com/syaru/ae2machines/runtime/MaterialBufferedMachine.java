package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.Recipe;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Typed native material slots; batteries and container-filling slots are not recipe materials. */
public final class MaterialBufferedMachine implements BufferedMachine {
    public interface Port {
        boolean input();
        boolean visible(Direction side);
        boolean ejecting();
        default boolean ejecting(Direction side){return ejecting();}
        default boolean allows(AEKey key,Direction side,boolean insert){return visible(side);}
        AEKey key();
        long amount();
        long insert(AEKey key,long amount,boolean simulate,boolean internal);
        long extract(AEKey key,long amount,boolean simulate,boolean internal);
    }
    private final List<Port> ports;
    private final Runnable changed;
    private final Component name;
    private WorkAccess work;
    @Override public boolean bindWork(WorkAccess access){work=Objects.requireNonNull(access);return true;}
    private final java.util.function.Function<Map<AEKey,BigInteger>,Optional<Recipe<AEKey>>> discover;
    public MaterialBufferedMachine(List<Port> ports,Runnable changed,Component name) {
        this(ports,changed,name,inputs->Optional.empty());
    }
    public MaterialBufferedMachine(List<Port> ports,Runnable changed,Component name,
            java.util.function.Function<Map<AEKey,BigInteger>,Optional<Recipe<AEKey>>> discover) {
        this.ports=List.copyOf(ports);this.changed=changed;this.name=name;this.discover=discover;
    }
    @Override public Optional<Recipe<AEKey>> discoverRecipe() {
        var inputs=new LinkedHashMap<AEKey,BigInteger>();
        for(var port:ports)if(port.input() && port.amount()>0 && port.key()!=null)
            inputs.merge(port.key(),BigInteger.valueOf(port.amount()),BigInteger::add);
        return inputs.isEmpty()?Optional.empty():discover.apply(Map.copyOf(inputs));
    }
    private long move(AEKey key,long amount,boolean insert,boolean simulate,boolean internal) {
        long left=amount;
        for(var port:ports) {
            if(internal ? port.input()==insert : port.input()!=insert)continue;
            long moved=insert?port.insert(key,left,simulate,internal):port.extract(key,left,simulate,internal);
            require(moved>=0 && moved<=left,"NATIVE_MATERIAL_AMOUNT");left-=moved;
            if(left==0)break;
        }
        return amount-left;
    }
    @Override public MEStorage storage(Direction side) {
        return new MEStorage() {
            private long transfer(AEKey key,long amount,Actionable mode,boolean insert) {
                if(amount<=0)return 0;
                long left=amount;
                for(var port:ports)if(port.input()==insert && port.allows(key,side,insert)) {
                    long n=insert?port.insert(key,left,mode.isSimulate(),false):port.extract(key,left,mode.isSimulate(),false);
                    require(n>=0 && n<=left,"NATIVE_MATERIAL_AMOUNT");left-=n;if(left==0)break;
                }
                if(left!=amount && !mode.isSimulate())changed.run();return amount-left;
            }
            @Override public long insert(AEKey key,long n,Actionable mode,IActionSource source){
                if(n<=0)return 0;
                if(work!=null && ports.stream().anyMatch(p->p.input() && p.allows(key,side,true) && p.insert(key,1,true,false)>0)) {
                    // Native item/fluid recipe lookups accept bounded stacks, not a job's total quantity.
                    long probe=(key instanceof AEItemKey || key instanceof AEFluidKey)?Math.min(n,Integer.MAX_VALUE):n;
                    var recipe=discover.apply(Map.of(key,BigInteger.valueOf(probe)));
                    if(recipe.isPresent() && recipe.get().inputs().size()==1) {
                        long accepted=work.insert(recipe.get(),key,n,mode.isSimulate());
                        require(accepted>=0 && accepted<=n,"NATIVE_MATERIAL_AMOUNT");
                        if(accepted>0)return accepted;
                    }
                }
                return transfer(key,n,mode,true);
            }
            @Override public long extract(AEKey key,long n,Actionable mode,IActionSource source){
                if(n<=0)return 0;
                long taken=transfer(key,n,mode,false);
                if(work!=null && taken<n && outputVisible(key,side)) {
                    long extra=work.extract(key,n-taken,mode.isSimulate());
                    require(extra>=0 && extra<=n-taken,"NATIVE_MATERIAL_AMOUNT");taken+=extra;
                }
                return taken;
            }
            @Override public void getAvailableStacks(KeyCounter out) {
                var available=new LinkedHashMap<AEKey,BigInteger>();
                for(var port:ports)if(!port.input() && port.amount()>0 && port.key()!=null && port.allows(port.key(),side,false))
                    available.merge(port.key(),BigInteger.valueOf(port.extract(port.key(),port.amount(),true,false)),BigInteger::add);
                if(work!=null)work.outputs().forEach((key,n)->{
                    if(outputVisible(key,side))available.merge(key,n,BigInteger::add);
                });
                // Only the legacy ME view is bounded; the receipt and extraction accounting remain exact.
                available.forEach((key,n)->out.add(key,n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()));
            }
            @Override public Component getDescription(){return name;}
        };
    }
    private boolean acceptsOutput(Port port,AEKey key) {
        return !port.input() && (key.equals(port.key()) || port.insert(key,1,true,true)>0);
    }
    private boolean outputVisible(AEKey key,Direction side) {
        return ports.stream().anyMatch(p->p.allows(key,side,false) && acceptsOutput(p,key));
    }
    @Override public boolean ejecting(){return ports.stream().anyMatch(p->!p.input() && p.ejecting());}
    @Override public boolean ejecting(AEKey key,Direction side) {
        return ports.stream().anyMatch(p->p.allows(key,side,false) && p.ejecting(side) && acceptsOutput(p,key));
    }
    @Override public BigInteger availableExecutions(Recipe<AEKey> recipe) {
        // Without a native receipt, do not independently reserve competing outputs in one tank.
        if(recipe.inputs().isEmpty() || recipe.outputs().isEmpty() || work==null && recipe.outputs().size()!=1)return BigInteger.ZERO;
        BigInteger count=null;
        for(var input:recipe.inputs().entrySet()) {
            long n=move(input.getKey(),Long.MAX_VALUE,false,true,true);
            var runs=BigInteger.valueOf(n).divide(input.getValue());count=count==null?runs:count.min(runs);
        }
        // Bound native work owns and persists its output receipt independently of physical tank capacity.
        if(work!=null)return count;
        var output=recipe.outputs().entrySet().iterator().next();
        var fits=BigInteger.valueOf(move(output.getKey(),Long.MAX_VALUE,true,true,true)).divide(output.getValue());
        return count.min(fits);
    }
    @Override public void takeInputs(Recipe<AEKey> recipe,BigInteger runs) {
        require(runs.signum()>0 && availableExecutions(recipe).compareTo(runs)>=0,"NATIVE_BUFFER_RECIPE_CHANGED");
        for(var in:recipe.inputs().entrySet()) {
            long n=in.getValue().multiply(runs).longValueExact();
            require(move(in.getKey(),n,false,false,true)==n,"NATIVE_BUFFER_INPUT_MISMATCH");
        }
    }
    @Override public boolean putOutputs(Map<AEKey,BigInteger> outputs) {
        require(outputs.size()==1,"NATIVE_BUFFER_OUTPUT_COUNT");
        var out=outputs.entrySet().iterator().next();long n=out.getValue().longValueExact();
        require(n>0,"NATIVE_BUFFER_OUTPUT_AMOUNT");
        if(move(out.getKey(),n,true,true,true)!=n)return false;
        require(move(out.getKey(),n,true,false,true)==n,"NATIVE_BUFFER_OUTPUT_MISMATCH");return true;
    }
}
