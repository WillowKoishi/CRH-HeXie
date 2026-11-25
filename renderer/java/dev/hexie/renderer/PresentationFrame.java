package dev.hexie.renderer;

import java.util.*;

public record PresentationFrame(long frameId, float partialTick, List<GroupPose> poses,
                                List<ProceduralDeform> deforms, List<LightState> lights) {
    public PresentationFrame {
        if (!(partialTick >= 0 && partialTick <= 1)) throw new IllegalArgumentException("partialTick");
        poses = List.copyOf(poses); deforms = List.copyOf(deforms); lights = List.copyOf(lights);
    }
    public Map<Long,Pose> worldPoses() {
        Map<Long,GroupPose> byId = new HashMap<>();
        for (var p : poses)
            if (p.groupId() == GroupPose.ROOT || byId.put(p.groupId(),p) != null)
                throw new IllegalArgumentException("invalid group");
        Map<Long,Pose> world = new HashMap<>();
        for (var p : poses) resolve(p, byId, world, new HashSet<>());
        return Map.copyOf(world);
    }
    private Pose resolve(GroupPose p, Map<Long,GroupPose> byId, Map<Long,Pose> world, Set<Long> active) {
        if (world.containsKey(p.groupId())) return world.get(p.groupId());
        if (!active.add(p.groupId())) throw new IllegalArgumentException("attachment cycle");
        Pose pose = p.localAt(partialTick);
        if (p.attachParentId() != GroupPose.ROOT) {
            var parent = byId.get(p.attachParentId());
            if (parent == null) throw new IllegalArgumentException("missing parent");
            pose = resolve(parent,byId,world,active).compose(pose);
        }
        active.remove(p.groupId());
        world.put(p.groupId(),pose);
        return pose;
    }
}
