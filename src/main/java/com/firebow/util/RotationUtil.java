package com.firebow.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Silent rotation — xoay player nhìn vào target block
 * mà không thay đổi render rotation (trông tự nhiên).
 * Giống kỹ thuật silentRotation trong BOOST-FPS.
 */
public final class RotationUtil {

    private RotationUtil() {}

    /**
     * Tính yaw/pitch để nhìn vào tâm block.
     * @return float[2] = {yaw, pitch}
     */
    public static float[] calcRotation(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes  = player.getEyePos();
        Vec3d tVec  = Vec3d.ofCenter(target);

        double dx = tVec.x - eyes.x;
        double dy = tVec.y - eyes.y;
        double dz = tVec.z - eyes.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float  yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float  pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        return new float[]{
            MathHelper.wrapDegrees(yaw),
            MathHelper.clamp(pitch, -90f, 90f)
        };
    }

    /**
     * Áp dụng rotation vào player packet rotation (silent).
     * Server thấy player đang nhìn vào block nhưng camera không thay đổi.
     */
    public static void applySilent(ClientPlayerEntity player, float[] rotation) {
        player.setYaw(rotation[0]);
        player.setPitch(rotation[1]);
    }

    /**
     * Kiểm tra block có trong tầm tay người (~4.5 block) không.
     */
    public static boolean inReach(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d tVec = Vec3d.ofCenter(target);
        double dist = eyes.distanceTo(tVec);
        return dist <= 4.5;
    }
}
