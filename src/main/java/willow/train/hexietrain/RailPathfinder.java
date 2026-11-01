package willow.train.hexietrain;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自己的轨道寻路（节点级 BFS）。
 *
 * <p>为什么需要它：Create 的 {@code Navigation.findPathTo} 在本环境下恒定返回 null，
 * 而轨道拓扑、车站点挂载都已验证正常。这里用同样的 {@link TrackGraph} 做一次 BFS：
 *
 * <ul>
 *   <li>从车头点的两个节点出发（覆盖前后两个方向）</li>
 *   <li>目标是车站所在边的两个端点之一</li>
 *   <li>得到节点序列后转成 {@code List<Couple<TrackNode>>}</li>
 * </ul>
 *
 * <p>算出来的 {@link DiscoveredPath} 直接交给 {@link com.simibubi.create.content.trains.entity.Navigation#startNavigation}，
 * 后续的加速、制动、到站判定仍然由 Create 负责。
 */
public final class RailPathfinder {

    private RailPathfinder() {}

    /** 找不到路时返回 null。 */
    public static DiscoveredPath find(Train train, GlobalStation target) {
        if (train == null || target == null || train.graph == null) return null;
        if (train.carriages.isEmpty()) return null;

        TrackGraph graph = train.graph;
        TravellingPoint lp = train.carriages.get(0).getLeadingPoint();
        if (lp == null || lp.node1 == null) return null;

        TrackNodeLocation goalA = target.edgeLocation.getFirst();
        TrackNodeLocation goalB = target.edgeLocation.getSecond();

        // BFS：node -> 前驱节点
        Map<TrackNode, TrackNode> prev = new HashMap<>();
        Map<TrackNode, TrackEdge> prevEdge = new HashMap<>();
        Set<TrackNode> seen = new HashSet<>();
        ArrayDeque<TrackNode> queue = new ArrayDeque<>();

        TrackNode startA = lp.node1;
        TrackNode startB = lp.node2;
        if (startA != null) { queue.add(startA); seen.add(startA); }
        if (startB != null && !seen.contains(startB)) { queue.add(startB); seen.add(startB); }

        TrackNode goal = null;
        while (!queue.isEmpty() && seen.size() <= 20000) {
            TrackNode node = queue.poll();
            TrackNodeLocation loc = node.getLocation();
            if (loc.equals(goalA) || loc.equals(goalB)) {
                goal = node;
                break;
            }
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections == null) continue;
            for (Map.Entry<TrackNode, TrackEdge> e : connections.entrySet()) {
                TrackNode next = e.getKey();
                if (next == null || seen.contains(next)) continue;
                seen.add(next);
                prev.put(next, node);
                prevEdge.put(next, e.getValue());
                queue.add(next);
            }
        }

        if (goal == null) return null;

        // 回溯出边序列
        List<Couple<TrackNode>> edges = new ArrayList<>();
        double distance = 0;
        TrackNode cur = goal;
        while (prev.containsKey(cur)) {
            TrackNode p = prev.get(cur);
            TrackEdge edge = prevEdge.get(cur);
            if (edge != null) distance += edge.getLength();
            edges.add(0, Couple.create(p, cur));
            cur = p;
        }

        // 关键：路径必须以"当前所在的边"开头。
        // Create 的 Navigation.control() 会沿 currentPath 找下一段轨道，
        // 如果第一段不是当前边，它会立刻取消导航（表现为 dest=无、车不动）。
        if (lp.node1 != null && lp.node2 != null) {
            TrackNode firstFrom = edges.isEmpty() ? null : edges.get(0).getFirst();
            if (firstFrom == null || !firstFrom.equals(lp.node2)) {
                edges.add(0, Couple.create(lp.node1, lp.node2));
                if (lp.edge != null) distance += lp.edge.getLength();
            }
        }

        if (edges.isEmpty()) return null;
        return new DiscoveredPath(distance, distance, edges, target);
    }

    /**
     * 从指定节点出发能否走到车站 —— 用来判断该往前开还是往后开。
     */
    public static boolean reachable(Train train, TrackNode from, GlobalStation target) {
        if (train == null || train.graph == null || from == null || target == null) return false;
        TrackGraph graph = train.graph;
        TrackNodeLocation goalA = target.edgeLocation.getFirst();
        TrackNodeLocation goalB = target.edgeLocation.getSecond();

        Set<TrackNode> seen = new HashSet<>();
        ArrayDeque<TrackNode> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty() && seen.size() <= 20000) {
            TrackNode node = queue.poll();
            TrackNodeLocation loc = node.getLocation();
            if (loc.equals(goalA) || loc.equals(goalB)) return true;
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections == null) continue;
            for (TrackNode next : connections.keySet()) {
                if (next == null || seen.contains(next)) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return false;
    }

    /**
     * 从车头到车站沿轨道的剩余距离（找不到返回 -1）。
     *
     * <p>路径总长包含了"当前整条边"，必须扣掉车头在这条边上已经走过的部分，
     * 否则只要车还没跨到下一条边，距离就恒定不变 —— 制动判据永远不成立。</p>
     */
    public static double distanceTo(Train train, GlobalStation target) {
        DiscoveredPath p = find(train, target);
        if (p == null) return -1;
        double d = p.distance;
        try {
            TravellingPoint lp = train.carriages.get(0).getLeadingPoint();
            if (lp != null) {
                d -= lp.position;          // 扣除当前边已走过部分
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0.0, d);
    }
}