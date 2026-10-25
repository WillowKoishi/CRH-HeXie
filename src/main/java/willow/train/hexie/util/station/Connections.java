package willow.train.hexie.util.station;

import net.minecraft.world.phys.Vec2;

import javax.annotation.Nullable;

public class Connections {

    public final int x1, y1, x2, y2;
    public int lineWidth = 2, tatics = 0;
    public long lineID = -1;

    protected Connections(
            int x1, int y1, int x2, int y2, @Nullable int lineWidth, @Nullable int tatics) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.lineWidth = lineWidth == 0 ? 2 : lineWidth;
        this.tatics = tatics;
    }

    public static Connections connect(Vec2 pos1, Vec2 pos2, int lineWidth, int tatics) {
        return new Connections(
                (int) pos1.x, (int) pos1.y, (int) pos2.x, (int) pos2.y, lineWidth, tatics);
    }

    public static Connections connect(Vec2 pos1, Vec2 pos2) {
        return connect(pos1, pos2, 2, 0);
    }

    public Connections line(long line) {
        this.lineID = line;
        return this;
    }

}
