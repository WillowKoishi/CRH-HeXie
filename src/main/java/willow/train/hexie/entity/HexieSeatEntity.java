package willow.train.hexie.entity;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import willow.train.hexie.init.EntityInit;

import java.util.Set;

public class HexieSeatEntity extends SeatEntity {

    public HexieSeatEntity(EntityType<?> p_i48580_1_, Level p_i48580_2_) {
        super(p_i48580_1_, p_i48580_2_);
    }

    public HexieSeatEntity(Level world) {
        this(EntityInit.YZ_SEAT_ENTITY.get(), world);
    }

    @Override
    public void tick() {}

    @Override
    protected void removePassenger(Entity entity) {
        super.removePassenger(entity);
        Set<String> tags = entity.getTags();
        for (String t : tags) {
            if (t.startsWith("sitting_on_yz_seat")) {
                return;
            }
        }
    }
}
