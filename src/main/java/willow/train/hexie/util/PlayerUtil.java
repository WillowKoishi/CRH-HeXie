package willow.train.hexie.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerUtil {

    public static boolean isPlayerEyeOn(Player player, BlockPos pos) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 eyeVec = player.getViewVector(1.0f);

        int x0 = pos.getX(), x1 = x0 + 1, y0 = pos.getY(), y1 = y0 + 1, z0 = pos.getZ(), z1 = z0 + 1;

        int result = 0;
        result = cross(eyePos, eyeVec, x0, z0, z1, 0, 2) ? result + 1 : result;
        result = cross(eyePos, eyeVec, x1, z0, z1, 0, 2) ? result + 1 : result;
        result = cross(eyePos, eyeVec, z0, x0, x1, 2, 0) ? result + 1 : result;
        result = cross(eyePos, eyeVec, z1, x0, x1, 2, 0) ? result + 1 : result;

        if(result == 2){
            boolean flag = cross(eyePos, eyeVec, x0, y0, y1, 0, 1);
            flag = flag || cross(eyePos, eyeVec, x1, y0, y1, 0, 1);
            flag = flag || cross(eyePos, eyeVec, z0, y0, y1, 2, 1);
            flag = flag || cross(eyePos, eyeVec, z1, y0, y1, 2, 1);

            return flag;
        } else {
            result = 0;
            result = cross(eyePos, eyeVec, x0, y0, y1, 0, 1) ? result + 1 : result;
            result = cross(eyePos, eyeVec, x1, y0, y1, 0, 1) ? result + 1 : result;
            result = cross(eyePos, eyeVec, y0, x0, x1, 1, 1) ? result + 1 : result;
            result = cross(eyePos, eyeVec, y1, x0, x1, 1, 1) ? result + 1 : result;

            if(result == 2) {
                boolean flag = cross(eyePos, eyeVec, x0, z0, z1, 0, 1);
                flag = flag || cross(eyePos, eyeVec, x1, z0, z1, 0, 1);
                flag = flag || cross(eyePos, eyeVec, y0, z0, z1, 2, 1);
                flag = flag || cross(eyePos, eyeVec, y1, z0, z1, 2, 1);

                return flag;
            } else {
                return false;
            }
        }
    }

    // 0 -> x
    // 1 -> y
    // 2 -> z
    private static Vec3 getVecPos(Vec3 fixedPoint, Vec3 directionVector, double posOnOneAxis, int whichAxis) {
        double power = 0;

        switch (whichAxis) {
            case 0 -> power = (posOnOneAxis - fixedPoint.x()) / directionVector.x();
            case 1 -> power = (posOnOneAxis - fixedPoint.y()) / directionVector.y();
            case 2 -> power = (posOnOneAxis - fixedPoint.z()) / directionVector.z();
        }
        return fixedPoint.add(directionVector.scale(power));
    }

    private static boolean cross(Vec3 fixedPoint, Vec3 directionVector, double posOnOneAxis,
                                   double bounding0, double bounding1, int whichAxis, int axisToCheck) {
        Vec3 posNeedCheck = getVecPos(fixedPoint, directionVector, posOnOneAxis, whichAxis);

        switch (axisToCheck) {
            case 0 -> {return (posNeedCheck.x() >= bounding0 && posNeedCheck.x() <= bounding1);}
            case 1 -> {return (posNeedCheck.y() >= bounding0 && posNeedCheck.y() <= bounding1);}
            case 2 -> {return (posNeedCheck.z() >= bounding0 && posNeedCheck.z() <= bounding1);}
        }
        return false;
    }

}
