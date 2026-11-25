package willow.train.hexie.interaction.spatial.model;

public record Pose(Vec position, Quat rotation) {
    public Pose compose(Pose local) {
        var a = rotation;
        var b = local.rotation();
        return new Pose(transform(local.position()), new Quat(
                a.w()*b.x()+a.x()*b.w()+a.y()*b.z()-a.z()*b.y(), a.w()*b.y()-a.x()*b.z()+a.y()*b.w()+a.z()*b.x(),
                a.w()*b.z()+a.x()*b.y()-a.y()*b.x()+a.z()*b.w(), a.w()*b.w()-a.x()*b.x()-a.y()*b.y()-a.z()*b.z()));
    }

    public Vec transform(Vec local) {
        return position.add(rotation.rotate(local));
    }
}
