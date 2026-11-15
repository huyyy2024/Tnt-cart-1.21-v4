package com.firebow.mixin;

import com.firebow.event.FireBowRuntime;
import com.firebow.util.PlacementCalc;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook vào tick() của tên đang bay.
 * Mỗi tick kiểm tra:
 *   - Có phải tên của mình không?
 *   - Có đang cháy không?
 *   - Dự đoán điểm chạm đất từ velocity hiện tại
 *   - Nếu tên còn ~8 tick nữa mới chạm đất → enqueue đặt Rail+Cart
 *
 * Dùng tick() thay vì onBlockHit() để đặt TRƯỚC khi tên đến.
 */
@Environment(EnvType.CLIENT)
@Mixin(PersistentProjectileEntity.class)
public class FireArrowMixin {

    // Tránh enqueue nhiều lần cho cùng 1 tên
    private boolean firebow$hasQueued = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity)(Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Chỉ xử lý 1 lần mỗi tên
        if (firebow$hasQueued) return;
        if (!isOwnArrow(self, mc)) return;
        if (!self.isOnFire()) return;

        // Tên đã cắm đất rồi thì bỏ qua
        if (self.isOnGround()) return;

        World world = self.getWorld();
        Vec3d pos      = self.getPos();
        Vec3d velocity = self.getVelocity();

        // Dự đoán điểm chạm đất
        BlockPos railPos = PlacementCalc.predictLandingRailPos(world, pos, velocity);
        if (railPos == null) return;

        // Tính số tick còn lại tới điểm đó
        int ticksLeft = estimateTicksToLand(pos, velocity, railPos);

        // Đặt Rail+Cart khi còn khoảng 8 tick (nửa giây)
        // để xe kịp spawn và chạy trước khi tên đến
        if (ticksLeft <= 8) {
            FireBowRuntime.get().enqueue(railPos);
            firebow$hasQueued = true;
        }
    }

    /**
     * Ước tính số tick còn lại trước khi tên đến railPos.
     * Mô phỏng nhanh trajectory.
     */
    private int estimateTicksToLand(Vec3d pos, Vec3d vel, BlockPos target) {
        double x  = pos.x, y  = pos.y, z  = pos.z;
        double vx = vel.x, vy = vel.y, vz = vel.z;
        Vec3d  t  = Vec3d.ofCenter(target);

        for (int i = 0; i < 60; i++) {
            vy -= 0.05;
            vx *= 0.99; vy *= 0.99; vz *= 0.99;
            x += vx; y += vy; z += vz;

            double dist = Math.sqrt(
                (x - t.x)*(x - t.x) +
                (y - t.y)*(y - t.y) +
                (z - t.z)*(z - t.z)
            );
            if (dist < 1.5) return i;
        }
        return 60;
    }

    private boolean isOwnArrow(PersistentProjectileEntity arrow, MinecraftClient mc) {
        if (mc.player == null) return false;
        Entity owner = arrow.getOwner();
        return owner != null && owner.getId() == mc.player.getId();
    }
}
