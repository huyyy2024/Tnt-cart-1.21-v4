package com.firebow.event;

import com.firebow.util.AsyncRunner;
import com.firebow.util.RotationUtil;
import com.firebow.util.TickSynchronizer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime singleton — đặt Rail + TNT Cart với anti-cheat bypass:
 *
 * 1. Silent rotation trước khi interact
 * 2. AsyncRunner với delay ms thực (không tick-based)
 * 3. TickSynchronizer đồng bộ với client tick
 * 4. Delay legit giữa Rail và Cart (80-120ms)
 * 5. Giới hạn tầm đặt ≤4.5 block
 *
 * Flow:
 *   Tick T   : enqueue(railPos) từ FireArrowMixin
 *   Async    : chờ 1 tick → rotate → đặt Rail → chờ 80-120ms → đặt Cart → restore
 */
public final class FireBowRuntime {

    private static final FireBowRuntime INSTANCE = new FireBowRuntime();
    public static FireBowRuntime get() { return INSTANCE; }

    // Delay giả lập tay người (ms) — đủ để anti-cheat không flag
    private static final long RAIL_TO_CART_DELAY_MS = 95L;
    private static final long SLOT_RESTORE_DELAY_MS = 60L;

    private final Queue<BlockPos>   queue    = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean     busy     = new AtomicBoolean(false);
    private final TickSynchronizer  tickSync = new TickSynchronizer();

    private static final int MAX_QUEUE = 3;

    private FireBowRuntime() {}

    public void enqueue(BlockPos railPos) {
        if (queue.size() < MAX_QUEUE) {
            queue.add(railPos);
        }
        // Kick off async worker nếu chưa chạy
        if (!busy.get()) {
            processNext();
        }
    }

    /** Gọi từ ClientTickMixin mỗi tick — notify synchronizer. */
    public void onClientTick(MinecraftClient client) {
        tickSync.onClientTick();

        // Kick off nếu có pending và worker không bận
        if (!queue.isEmpty() && !busy.get()) {
            processNext();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Async worker — chạy trên thread riêng
    // ─────────────────────────────────────────────────────────────
    private void processNext() {
        if (!busy.compareAndSet(false, true)) return;
        BlockPos railPos = queue.poll();
        if (railPos == null) {
            busy.set(false);
            return;
        }

        AsyncRunner.run(() -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                // Kiểm tra tầm đặt
                if (!RotationUtil.inReach(mc.player, railPos)) return;

                // Chờ đầu tick tiếp theo để đồng bộ
                tickSync.awaitNextTick(100L);

                // === Đặt Rail ===
                mc.execute(() -> {
                    if (mc.player == null || mc.interactionManager == null) return;

                    // Silent rotate nhìn vào block dưới railPos
                    float[] rot = RotationUtil.calcRotation(mc.player, railPos.down());
                    float prevYaw   = mc.player.getYaw();
                    float prevPitch = mc.player.getPitch();
                    RotationUtil.applySilent(mc.player, rot);

                    int prevSlot = mc.player.getInventory().selectedSlot;
                    int railSlot = findInHotbar(mc.player, Items.RAIL);
                    if (railSlot == -1) railSlot = findInHotbar(mc.player, Items.POWERED_RAIL);

                    if (railSlot != -1) {
                        mc.player.getInventory().selectedSlot = railSlot;
                        BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofCenter(railPos).add(0, -0.5, 0),
                            Direction.UP,
                            railPos.down(),
                            false
                        );
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    }

                    // Restore rotation ngay
                    mc.player.setYaw(prevYaw);
                    mc.player.setPitch(prevPitch);

                    // Lưu prevSlot để restore sau
                    final int slotToRestore = prevSlot;

                    // Delay legit trước khi đặt Cart
                    AsyncRunner.runDelayed(() -> {
                        // Chờ đầu tick tiếp theo
                        tickSync.awaitNextTick(100L);

                        mc.execute(() -> {
                            if (mc.player == null || mc.interactionManager == null) return;

                            // Kiểm tra rail đã có chưa
                            boolean hasRail =
                                mc.world.getBlockState(railPos).isOf(Blocks.RAIL) ||
                                mc.world.getBlockState(railPos).isOf(Blocks.POWERED_RAIL);
                            if (!hasRail) return;

                            // === Đặt Cart ===
                            float[] rot2 = RotationUtil.calcRotation(mc.player, railPos);
                            float py = mc.player.getYaw();
                            float pp = mc.player.getPitch();
                            RotationUtil.applySilent(mc.player, rot2);

                            int cartSlot = findInHotbar(mc.player, Items.TNT_MINECART);
                            if (cartSlot != -1) {
                                mc.player.getInventory().selectedSlot = cartSlot;
                                BlockHitResult hit2 = new BlockHitResult(
                                    Vec3d.ofCenter(railPos).add(0, 0.1, 0),
                                    Direction.UP,
                                    railPos,
                                    false
                                );
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit2);
                            }

                            mc.player.setYaw(py);
                            mc.player.setPitch(pp);

                            // Restore slot sau 1 tick
                            AsyncRunner.runDelayed(() -> {
                                tickSync.awaitNextTick(100L);
                                mc.execute(() -> {
                                    if (mc.player != null) {
                                        mc.player.getInventory().selectedSlot = slotToRestore;
                                    }
                                    busy.set(false);
                                    // Xử lý pending tiếp theo
                                    if (!queue.isEmpty()) processNext();
                                });
                            }, SLOT_RESTORE_DELAY_MS);
                        });
                    }, RAIL_TO_CART_DELAY_MS);
                });

            } catch (Exception e) {
                busy.set(false);
            }
        });
    }

    private int findInHotbar(ClientPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }
}
