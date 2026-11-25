package dev.hexie.renderer.mc;

import java.util.*;

public final class ViewPassOwnership {
    public enum Pass { SOLID, CUTOUT, TRANSLUCENT, SHADOW_SOLID, SHADOW_CUTOUT, SHADOW_TRANSLUCENT }
    public enum Owner { HEXIE, CREATE_FLYWHEEL, SABLE, IRIS_VEIL, WORLD }
    public record Key(Object view, Pass pass, Object ownerId, Object submesh) {}
    private final Map<Key,Owner> assigned = new HashMap<>();
    private final Set<Key> submitted = new HashSet<>();
    private int duplicates;
    public void beginFrame() { assigned.clear(); submitted.clear(); duplicates=0; }
    public void assign(Key key,Owner owner) {
        var previous=assigned.putIfAbsent(key,owner);
        if (previous!=null && previous!=owner) throw new IllegalStateException("conflicting owner for "+key);
    }
    /** A host may reserve a native layer before the standard producer visits it. */
    public void assignDefault(Key key,Owner original) { assigned.putIfAbsent(key,original); }
    /** Commit only after the producer has successfully flushed its draw. */
    public boolean submit(Key key,Owner producer,Runnable draw) {
        if (assigned.get(key)!=producer) return false;
        if (!submitted.add(key)) { duplicates++; return false; }
        try { draw.run(); }
        catch (RuntimeException | Error e) { submitted.remove(key); throw e; }
        return true;
    }
    public int submitted() { return submitted.size(); }
    public Map<Owner,Integer> submissionsByOwner() {
        var totals=new EnumMap<Owner,Integer>(Owner.class);
        for (var key:submitted) totals.merge(assigned.get(key),1,Integer::sum);
        return totals;
    }
    public int missing() { return assigned.size()-submitted.size(); }
    public int duplicatesPrevented() { return duplicates; }
}
