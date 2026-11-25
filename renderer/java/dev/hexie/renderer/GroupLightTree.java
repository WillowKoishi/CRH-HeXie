package dev.hexie.renderer;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** A tree per owner. A positive beam proof applies to every original leaf. */
public final class GroupLightTree {
    public record Light(int index, LightState state, Vec position) {}
    private record Node(Bounds bounds, Node left, Node right, int index) {
        void visit(IntConsumer sink) {
            if (left==null) sink.accept(index);
            else { left.visit(sink); right.visit(sink); }
        }
        void prove(Bounds receiver, Predicate<Bounds> empty, IntConsumer sink) {
            if (empty.test(bounds.union(receiver))) visit(sink);
            else if (left!=null) { left.prove(receiver,empty,sink); right.prove(receiver,empty,sink); }
        }
    }
    private final List<Node> roots;
    public GroupLightTree(List<Light> lights) {
        Map<Long,List<Light>> groups=new LinkedHashMap<>();
        for (var light:lights) groups.computeIfAbsent(light.state.ownerGroup(),ignore->new ArrayList<>()).add(light);
        roots=groups.values().stream().map(GroupLightTree::build).toList();
    }
    private static Node build(List<Light> lights) {
        Bounds box=Bounds.point(lights.get(0).position);
        for (var light:lights) box=box.union(Bounds.point(light.position));
        if (lights.size()==1) return new Node(box,null,null,lights.get(0).index);
        Vec span=box.high().minus(box.low());
        int axis=span.x()>=span.y() && span.x()>=span.z()?0:span.y()>=span.z()?1:2;
        // Rigidly attached leaves keep a stable owner-space split under rotation.
        boolean local=lights.stream().allMatch(l->l.state.ownerGroup()==l.state.attachment());
        if (local) {
            Bounds localBounds=Bounds.point(lights.get(0).state.localPosition());
            for (var light:lights) localBounds=localBounds.union(Bounds.point(light.state.localPosition()));
            Vec extent=localBounds.high().minus(localBounds.low());
            int split=extent.x()>=extent.y() && extent.x()>=extent.z()?0:extent.y()>=extent.z()?1:2;
            lights.sort(Comparator.comparingDouble(l->l.state.localPosition().at(split)));
        } else lights.sort(Comparator.comparingDouble(l->l.position.at(axis)));
        int mid=lights.size()/2;
        return new Node(box,build(lights.subList(0,mid)),build(lights.subList(mid,lights.size())),-1);
    }
    public void proven(Bounds receiver,Predicate<Bounds> completeAndEmpty,IntConsumer visibleLight) {
        for (var root:roots) root.prove(receiver,completeAndEmpty,visibleLight);
    }
}
