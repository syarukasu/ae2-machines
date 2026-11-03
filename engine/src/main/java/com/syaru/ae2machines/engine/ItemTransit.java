package com.syaru.ae2machines.engine;

import java.util.*;

/** Immutable captured topology; packets own their items until an endpoint actually accepts them. */
public final class ItemTransit<K> {
    public record Endpoint(long pipe,long position,int face,int color) {
        public Endpoint {
            if(face<0 || face>5 || color < -2 || color>15)throw new IllegalArgumentException("Invalid endpoint");
        }
    }
    public record Node(long id,int speed,double cost,int color,List<Long> neighbors,List<Endpoint> endpoints) {
        public Node {
            if(speed<=0 || !Double.isFinite(cost) || cost<=0 || color < -1 || color>15)throw new IllegalArgumentException("Invalid transporter tier");
            neighbors=List.copyOf(neighbors);endpoints=List.copyOf(endpoints);
        }
    }
    public record Packet<K>(long id,K key,int amount,long pipe,long home,int color,int progress,List<Long> route,Endpoint target) {
        public Packet {
            Objects.requireNonNull(key);route=List.copyOf(route);
            if(id<0 || amount<=0 || progress<0 || progress>=100 || color < -1 || color>15)throw new IllegalArgumentException("Invalid transit packet");
        }
    }
    public interface Access<K> {
        long capacity(Endpoint endpoint,K key,int offer,List<Packet<K>> reservations);
        long deliver(Endpoint endpoint,K key,int offer);
    }
    private record Route(List<Long> nodes,Endpoint endpoint,int accepted) {}
    private record Step(long node,double cost) {}
    private final Map<Long,Node> nodes;
    private final Access<K> access;
    private final List<Packet<K>> packets=new ArrayList<>();
    private long nextId;
    public ItemTransit(Collection<Node> topology,Access<K> access,long nextId,List<Packet<K>> saved) {
        var map=new LinkedHashMap<Long,Node>();
        for(var node:topology)if(map.put(node.id(),node)!=null)throw new IllegalArgumentException("Duplicate transporter");
        if(nextId<0)throw new IllegalArgumentException("Invalid packet generation");
        nodes=Map.copyOf(map);this.access=Objects.requireNonNull(access);this.nextId=nextId;
        for(var node:nodes.values()) {
            if(!nodes.keySet().containsAll(node.neighbors()))throw new IllegalArgumentException("Missing transporter neighbor");
            for(var endpoint:node.endpoints())if(endpoint.pipe()!=node.id())throw new IllegalArgumentException("Wrong endpoint owner");
        }
        var ids=new HashSet<Long>();
        for(var packet:saved) {
            if(!ids.add(packet.id()) || packet.id()>=nextId)throw new IllegalArgumentException("Invalid packet generation");
            validate(packet);packets.add(packet);
        }
    }
    private void validate(Packet<K> p) {
        if(!nodes.containsKey(p.pipe()) || p.color() < -1)throw new IllegalArgumentException("Missing packet node");
        if(p.route().isEmpty()) {
            if(p.target()!=null)throw new IllegalArgumentException("Target without a route");
            return;
        }
        if(p.route().get(0)!=p.pipe() || p.target()==null)throw new IllegalArgumentException("Invalid packet route");
        for(int i=0;i<p.route().size();i++) {
            var node=nodes.get(p.route().get(i));
            if(node==null || node.color()!=-1 && node.color()!=p.color())throw new IllegalArgumentException("Route color mismatch");
            if(i>0 && !nodes.get(p.route().get(i-1)).neighbors().contains(node.id()))throw new IllegalArgumentException("Disconnected route");
        }
        if(!nodes.get(p.route().get(p.route().size()-1)).endpoints().contains(p.target()))
            throw new IllegalArgumentException("Missing route endpoint");
        if(p.target().color()!=-2 && p.target().color()!=p.color())throw new IllegalArgumentException("Endpoint color mismatch");
    }
    public List<Packet<K>> packets(){return List.copyOf(packets);}
    public long nextId(){return nextId;}
    public int insert(K key,int amount,long pipe,long home,boolean simulate) {
        var node=Objects.requireNonNull(nodes.get(pipe),"Missing ingress");
        return insert(key,amount,pipe,home,node.color(),simulate);
    }
    public int insert(K key,int amount,long pipe,long home,int color,boolean simulate) {
        Objects.requireNonNull(key);
        if(color < -1 || color>15)throw new IllegalArgumentException("Invalid ingress color");
        if(amount<=0)return 0;
        var node=Objects.requireNonNull(nodes.get(pipe),"Missing ingress");
        if(node.color()!=-1 && node.color()!=color)return 0;
        var route=route(key,amount,pipe,home,color,-1,false);
        if(route==null)return 0;
        if(!simulate)packets.add(new Packet<>(allocate(),key,route.accepted(),pipe,home,color,0,route.nodes(),route.endpoint()));
        return route.accepted();
    }
    /** A source already extracted these items. Keep ownership even if its target changed meanwhile. */
    public void hold(K key,int amount,long pipe,long home) {
        var node=Objects.requireNonNull(nodes.get(pipe));packets.add(new Packet<>(allocate(),key,amount,pipe,home,node.color(),0,List.of(),null));
    }
    private long allocate(){long id=nextId;nextId=Math.incrementExact(nextId);return id;}
    private Route route(K key,int amount,long start,long home,int color,long exclude,boolean returning) {
        var distances=new HashMap<Long,Double>();var previous=new HashMap<Long,Long>();
        var queue=new PriorityQueue<Step>(Comparator.comparingDouble(Step::cost).thenComparingLong(Step::node));
        distances.put(start,0.0);queue.add(new Step(start,0));
        var reserved=packets.stream().filter(p->p.id()!=exclude && p.target()!=null).toList();
        while(!queue.isEmpty()) {
            var step=queue.remove();if(step.cost()!=distances.get(step.node()))continue;
            var node=nodes.get(step.node());
            for(var endpoint:node.endpoints()) {
                if((endpoint.position()==home)!=returning || endpoint.color()!=-2 && endpoint.color()!=color)continue;
                long accepted=access.capacity(endpoint,key,amount,reserved);
                if(accepted<0 || accepted>amount)throw new IllegalStateException("Invalid reserved endpoint capacity");
                if(accepted==0)continue;
                var path=new ArrayList<Long>();long cursor=node.id();path.add(cursor);
                while(cursor!=start){cursor=previous.get(cursor);path.add(cursor);}
                Collections.reverse(path);return new Route(List.copyOf(path),endpoint,(int)accepted);
            }
            for(long neighbor:node.neighbors()) {
                var next=nodes.get(neighbor);if(next.color()!=-1 && next.color()!=color)continue;
                double distance=step.cost()+next.cost();
                if(distance<distances.getOrDefault(neighbor,Double.POSITIVE_INFINITY)) {
                    distances.put(neighbor,distance);previous.put(neighbor,node.id());queue.add(new Step(neighbor,distance));
                }
            }
        }
        return null;
    }
    public boolean tick() {
        boolean changed=false;
        for(int i=0;i<packets.size();) {
            var p=packets.get(i);
            if(p.route().isEmpty()) {
                var route=route(p.key(),p.amount(),p.pipe(),p.home(),p.color(),p.id(),false);
                if(route==null)route=route(p.key(),p.amount(),p.pipe(),p.home(),p.color(),p.id(),true);
                if(route==null){i++;continue;}
                p=new Packet<>(p.id(),p.key(),p.amount(),p.pipe(),p.home(),p.color(),p.progress(),route.nodes(),route.endpoint());
            }
            long progress=(long)p.progress()+nodes.get(p.pipe()).speed();changed=true;
            if(progress<100) {
                packets.set(i,new Packet<>(p.id(),p.key(),p.amount(),p.pipe(),p.home(),p.color(),(int)progress,p.route(),p.target()));i++;continue;
            }
            if(p.route().size()>1) {
                var path=p.route().subList(1,p.route().size());
                packets.set(i,new Packet<>(p.id(),p.key(),p.amount(),path.get(0),p.home(),p.color(),(int)(progress%100),path,p.target()));i++;continue;
            }
            long sent=access.deliver(p.target(),p.key(),p.amount());
            if(sent<0 || sent>p.amount())throw new IllegalStateException("Invalid transporter delivery");
            if(sent==p.amount()){packets.remove(i);continue;}
            // Retain and re-route the remainder, never synthesize a refund or drop it.
            packets.set(i,new Packet<>(p.id(),p.key(),p.amount()-(int)sent,p.pipe(),p.home(),p.color(),0,List.of(),null));i++;
        }
        return changed;
    }
}
