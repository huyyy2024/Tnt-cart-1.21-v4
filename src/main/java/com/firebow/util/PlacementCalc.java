package com.firebow.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Dự đoán điểm tên sẽ chạm đất từ vị trí + velocity hiện tại.
 * Mô phỏng physics của Minecraft: gravity 0.05, drag 0.99 mỗi tick.
 * Tìm block solid đầu tiên tên sẽ bay qua.
 */
public final class PlacementCalc {

    private static final double GRAVITY = 0.05;
    private static final double DRAG    = 0.99;
    private static final int    MAX_TICKS = 60; // tối đa 3 giây bay

    private PlacementCalc() {}

    /**
     * Tính điểm tên sẽ chạm đất.
     * Trả về BlockPos ngay trên block solid đó, hoặc null nếu không tìm được.
     */
    public static BlockPos predictLandingRailPos(World world, Vec3d pos, Vec3d velocity) {
        double x  = pos.x;
        double y  = pos.y;
        double z  = pos.z;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        for (int t = 0; t < MAX_TICKS; t++) {
            // Vị trí tick trước
            double prevX = x;
            double prevY = y;
            double prevZ = z;

            // Cập nhật velocity (gravity + drag — giống Arrow tick trong MC)
            vy -= GRAVITY;
            vx *= DRAG;
            vy *= DRAG;
            vz *= DRAG;

            // Cập nhật vị trí
            x += vx;
            y += vy;
            z += vz;

            BlockPos cur  = BlockPos.ofFloored(x, y, z);
            BlockPos prev = BlockPos.ofFloored(prevX, prevY, prevZ);

            // Nếu block hiện tại là solid → tên sẽ cắm vào đây
            if (!world.getBlockState(cur).isAir()) {
                // Thử đặt rail tại block trước (nơi tên còn ở không khí)
                return findRailPos(world, prev);
            }

            // Kiểm tra block dưới chân để bắt tên chạm mặt trên
            BlockPos below = cur.down();
            if (!world.getBlockState(below).isAir() && isSolidGround(world, below)) {
                return findRailPos(world, cur);
            }
        }
        return null;
    }

    /**
     * Tìm vị trí AIR hợp lệ ngay trên nền solid, bắt đầu từ pos.
     * Xuống tối đa 8 block.
     */
    public static BlockPos findRailPos(World world, BlockPos start) {
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos candidate = start.down(dy);
            BlockPos below     = candidate.down();
            if (!world.getBlockState(candidate).isAir()) return null;
            if (isSolidGround(world, below)) return candidate;
        }
        return null;
    }

    public static boolean isSolidGround(World world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        if (s.isAir())            return false;
        if (s.isOf(Blocks.WATER)) return false;
        if (s.isOf(Blocks.LAVA))  return false;
        return s.isSolidBlock(world, pos);
    }
}
