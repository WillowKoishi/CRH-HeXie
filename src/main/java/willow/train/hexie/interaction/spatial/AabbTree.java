package willow.train.hexie.interaction.spatial;

import java.util.function.Consumer;
import willow.train.hexie.interaction.spatial.model.AABB;
import willow.train.hexie.interaction.spatial.model.Vec;
import static willow.train.hexie.interaction.spatial.PickGeometry.union;

/** Height-balanced BVH. Leaves remain addressable when ancestors rotate. */
final class AabbTree<T> {
    static final class Node<T> {
        AABB box;
        final T item;
        Node<T> parent, left, right;
        int height;

        Node(AABB box, T item) { this.box = box; this.item = item; }
        void refit() {
            box = union(left.box, right.box);
            height = 1 + Math.max(left.height, right.height);
        }
    }

    private Node<T> root;
    AABB bounds() { return root == null ? null : root.box; }

    Node<T> insert(AABB bounds, T item) {
        var leaf = new Node<>(bounds, item);
        if (root == null) { root = leaf; return leaf; }
        var near = root;
        while (near.left != null) {
            double a = growth(near.left.box, bounds), b = growth(near.right.box, bounds);
            near = a < b || a == b && near.left.height < near.right.height ? near.left : near.right;
        }
        var branch = new Node<T>(null, null);
        replace(near, branch);
        branch.left = near;
        branch.right = leaf;
        near.parent = leaf.parent = branch;
        refit(branch);
        return leaf;
    }

    void remove(Node<T> leaf) {
        var branch = leaf.parent;
        if (branch == null) { root = null; return; }
        replace(branch, branch.left == leaf ? branch.right : branch.left);
        refit(branch.parent);
    }

    Node<T> move(Node<T> leaf, AABB bounds, double margin) {
        if (leaf != null && contains(leaf.box, bounds)) return leaf;
        if (leaf == null) throw new IllegalArgumentException("Missing leaf");
        remove(leaf);
        return insert(expand(bounds, margin), leaf.item);
    }

    static AABB expand(AABB bounds, double margin) {
        Vec pad = new Vec(margin, margin, margin);
        return new AABB(bounds.min().subtract(pad), bounds.max().add(pad));
    }

    private static boolean contains(AABB a, AABB b) {
        return a.min().x() <= b.min().x() && a.min().y() <= b.min().y() && a.min().z() <= b.min().z()
                && a.max().x() >= b.max().x() && a.max().y() >= b.max().y() && a.max().z() >= b.max().z();
    }
    private static double area(AABB box) {
        Vec d = box.max().subtract(box.min());
        return d.x()*d.y() + d.y()*d.z() + d.z()*d.x();
    }
    private static double growth(AABB a, AABB b) { return area(union(a,b)) - area(a); }

    private void replace(Node<T> old, Node<T> replacement) {
        replacement.parent = old.parent;
        if (old.parent == null) root = replacement;
        else if (old.parent.left == old) old.parent.left = replacement;
        else old.parent.right = replacement;
    }

    private void refit(Node<T> node) {
        while (node != null) {
            int balance = node.right.height - node.left.height;
            if (Math.abs(balance) > 1) {
                // The taller grandchild stays directly under the promoted node.
                var heavy = balance > 0 ? node.right : node.left;
                replace(node, heavy);
                var transfer = heavy.left.height <= heavy.right.height ? heavy.left : heavy.right;
                if (transfer == heavy.left) heavy.left = node; else heavy.right = node;
                if (balance > 0) node.right = transfer; else node.left = transfer;
                transfer.parent = node;
                node.parent = heavy;
                node.refit();
                node = heavy;
            }
            node.refit();
            node = node.parent;
        }
    }

    void query(PickGeometry.Ray ray, SpatialPicker.Query query, Consumer<T> visit) {
        if (root != null && Double.isFinite(entry(root, ray, query))) descend(root, ray, query, visit);
    }

    private double entry(Node<T> n, PickGeometry.Ray ray, SpatialPicker.Query query) {
        query.visited++;
        return ray.entry(n.box, query.limit);
    }

    private void descend(Node<T> n, PickGeometry.Ray ray, SpatialPicker.Query q, Consumer<T> visit) {
        if (n.left == null) { visit.accept(n.item); return; }
        double a = entry(n.left, ray, q), b = entry(n.right, ray, q);
        var first = a <= b ? n.left : n.right;
        if (Double.isFinite(Math.min(a,b)) && Math.min(a,b) <= q.limit) descend(first, ray, q, visit);
        if (Double.isFinite(Math.max(a,b)) && Math.max(a,b) <= q.limit)
            descend(first == n.left ? n.right : n.left, ray, q, visit);
    }
}
