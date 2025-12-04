package org.musheck.utils;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.system.IInteractions.*;

import static net.minecraft.core.Direction.*;
import static org.musheck.HighwayManager.*;

public class WorldUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BlockPos currentBreakingPos = null;
    private static int sequenceCounter = 1;
    private static int nextSequence() {
        if (sequenceCounter == Integer.MAX_VALUE) sequenceCounter = 1;
        else sequenceCounter++;
        return sequenceCounter;
    }

    private static boolean startPredictionSend(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction dir) {
        try {
            if (mc.gameMode == null || mc.level == null) return false;
            Object gameMode = mc.gameMode;

            // Resolve expected parameter types in both named and intermediary
            Class<?> clientLevelClass = null;
            Class<?> predictiveActionClass = null;
            try {
                clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            } catch (ClassNotFoundException ignored) {
                clientLevelClass = Class.forName("net.minecraft.class_638");
            }
            try {
                predictiveActionClass = Class.forName("net.minecraft.client.multiplayer.prediction.PredictiveAction");
            } catch (ClassNotFoundException ignored) {
                predictiveActionClass = Class.forName("net.minecraft.class_7204");
            }

            // Find method by exact name then fallback to scanning
            java.lang.reflect.Method target = null;
            Class<?> c = gameMode.getClass();
            while (c != null && target == null) {
                for (String name : new String[]{"startPrediction", "method_41931"}) {
                    try {
                        target = c.getDeclaredMethod(name, clientLevelClass, predictiveActionClass);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                c = c.getSuperclass();
            }

            // As last resort, scan declared methods for 2-arg (ClientLevel, <interface>)
            if (target == null) {
                c = gameMode.getClass();
                scan:
                while (c != null) {
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        if (m.getParameterCount() == 2) {
                            Class<?> p0 = m.getParameterTypes()[0];
                            Class<?> p1 = m.getParameterTypes()[1];
                            if (p0 == clientLevelClass && p1.isInterface()) {
                                target = m;
                                break scan;
                            }
                        }
                    }
                    c = c.getSuperclass();
                }
            }

            if (target == null) return false;
            target.setAccessible(true);

            Class<?> actionIface = target.getParameterTypes()[1];
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    actionIface.getClassLoader(),
                    new Class[]{actionIface},
                    (obj, method, args) -> {
                        // Expect single int sequence parameter
                        int sequence = (args != null && args.length > 0 && args[0] instanceof Integer)
                                ? (Integer) args[0]
                                : 0;
                        return new ServerboundPlayerActionPacket(action, pos, dir, sequence);
                    }
            );

            target.invoke(gameMode, mc.level, proxy);
            System.out.println("[mushack] Sent prediction packet via reflection for action: " + action);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void placeBlock(BlockPos pos, Item item) {
        if (pos == null || mc.player == null || mc.gameMode == null) return;

        if (mc.player.getInventory().getItem(mc.player.getInventory().selected).getItem() != item) {
            swapDelay = 0;
            switchToItem(item);
            return;
        }

        if (swapDelay < 3) {
            swapDelay++;
            return;
        }

        if(getPlacementMode().getValue() == PlacementMode.Airplace) {
            predictiveSwapItemWithOffhand(mc, true);
            BlockHitResult hit = new BlockHitResult(pos.getCenter(), Direction.DOWN, pos, true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, hit);
            mc.player.swing(InteractionHand.OFF_HAND);
            predictiveSwapItemWithOffhand(mc, true);
        }

        if(getPlacementMode().getValue() == PlacementMode.Rotations) {
            Direction side = getPlaceSide(pos);

            if(side != null) {
                RusherHackAPI.getRotationManager().updateRotation(pos.offset(side.getUnitVec3i()));
            }
            RusherHackAPI.interactions().placeBlock(pos, InteractionHand.MAIN_HAND, false);
        }

        placeTick = 0;
    }

    public static float[] getRotation(Vec3 vec) {
        Vec3 eyePos = mc.player.getEyePosition();

        double dx = vec.x - eyePos.x;
        double dy = vec.y - eyePos.y;
        double dz = vec.z - eyePos.z;

        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double yaw = -Math.atan2(dx, dz) / Math.PI * 180;
        double pitch = -Math.asin(dy / r) / Math.PI * 180;

        return new float[]{(float) yaw, (float) pitch};
    }

    public static Direction getPlaceSide(BlockPos blockPos) {
        if (mc.player == null || mc.level == null) return null;

        // Mojang uses Vec3 instead of Vec3d
        Vec3 lookVec = Vec3.atCenterOf(blockPos).subtract(mc.player.getEyePosition());

        double bestRelevancy = -Double.MAX_VALUE;
        Direction bestSide = null;

        for (Direction side : Direction.values()) {
            // neighbor block position
            BlockPos neighbor = blockPos.relative(side);
            BlockState state = mc.level.getBlockState(neighbor);

            // skip air or blocks
            if (state.isAir()) continue;

            // skip fluids
            if (!state.getFluidState().isEmpty()) continue;

            // Mojang 1.21.4: axis.choose(x,y,z) still exists
            // side.getStepX()/Y/Z replace side.getDirection().offset()
            double axisValue = side.getAxis().choose(lookVec.x, lookVec.y, lookVec.z);

            // offset direction is determined by step sign (-1 or +1 on that axis)
            int step = side.getStepX() + side.getStepY() + side.getStepZ();
            double relevancy = axisValue * step;

            if (relevancy > bestRelevancy) {
                bestRelevancy = relevancy;
                bestSide = side;
            }
        }

        return bestSide;
    }

    public static void lookAtBlock(BlockPos pos) {
        Vec3 hitPos = Vec3.atCenterOf(pos);

        Direction hitSide = getPlaceSide(pos);

        if (hitSide != null) {
            BlockPos neighborPos = pos.relative(hitSide);
            Vec3 offset = new Vec3(hitSide.getStepX(), hitSide.getStepY(), hitSide.getStepZ()).scale(0.5);
            hitPos = hitPos.add(offset);
        }

        float[] rot = getRotation(hitPos);
        mc.player.setYRot(rot[0]);
        mc.player.setXRot(rot[1]);

    }

    public static void alignToCardinal(double dif) {
        assert mc.player != null;
        if (mc.player.getBlockZ() != 0) {
            if (initialDirection == EAST || initialDirection == WEST) {
                double difference = Math.abs(mc.player.getZ() - alignmentZ);
                if (difference > dif) {
                    GoalBlock goal = new GoalBlock(mc.player.getBlockX(), playerY, playerZ);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                    return;
                }
            }
        }

        if (mc.player.getBlockX() != 0) {
            if (initialDirection == NORTH || initialDirection == SOUTH) {
                double difference = Math.abs(mc.player.getX() - alignmentX);
                if (difference > dif) {
                    GoalBlock goal = new GoalBlock((int) playerX, playerY, mc.player.getBlockZ());
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                    return;
                }
            }
        }
    }

    public static void predictiveSwapItemWithOffhand(Minecraft client, boolean isPutBack) {
        //if(isPutBack) isPutBack = false; // Sometimes causes issues??
        if(client.player == null || client.gameMode == null) return;
        if(client.player.containerMenu.containerId == 0 && isPutBack) {
            client.gameMode.handleInventoryMouseClick(0, 45, client.player.getInventory().selected, ClickType.SWAP, client.player);
            return;
        }
        client.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        ItemStack mainHandStack = client.player.getMainHandItem();
        ItemStack offHandStack = client.player.getOffhandItem();
        client.player.getInventory().setItem(client.player.getInventory().selected, offHandStack);
        client.player.getInventory().setItem(40, mainHandStack);
    }

    public static boolean canPlaceBlock(BlockPos blockPos) {
        if (blockPos == null) return false;

        // 1. Check if current block is replaceable
        if (!mc.level.getBlockState(blockPos).canBeReplaced()) return false;

        // 2. Check entity collisions at this block space
        AABB placeBB = new AABB(blockPos);
        for (Entity entity : mc.level.getEntities(null, placeBB)) {
            if (!entity.isRemoved() && entity.isPickable()) {
                return false; // obstructed by entity
            }
        }

        return true;
    }

    public static double getEuclideanDistance(BlockPos bP, BlockPos pP) {
        double dx, dz;
        if (bP.getX() > pP.getX()) dx = bP.getX() - pP.getX();
        else dx = pP.getX() - bP.getX();
        if (bP.getZ() > pP.getZ()) dz = bP.getZ() - pP.getZ();
        else dz = pP.getZ() - bP.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distanceBetweenPlayerAndEndOfPavement(Block block) {
        int offsetX = 0, offsetZ = 0;
        Direction direction = mc.player.getDirection();

        switch (direction) {
            case NORTH -> offsetZ = -1;
            case SOUTH -> offsetZ = 1;
            case WEST -> offsetX = -1;
            case EAST -> offsetX = 1;
        }

        BlockPos playerPos = mc.player.blockPosition().below();
        BlockPos pos = null;

        for(int i = 0; i <= 4; i++) {
            int blockX = mc.player.blockPosition().getX() + offsetX * i;
            int blockZ = mc.player.blockPosition().getZ() + offsetZ * i;
            BlockPos bpos = new BlockPos(blockX, mc.player.blockPosition().below().getY(), blockZ);

            if (mc.level.getBlockState(bpos).getBlock() != block) {
                pos = bpos;
                break;
            }
        }

        if (pos == null) return 10;

        return getEuclideanDistance(pos, playerPos);
    }

    public static void startBreakPacket(BlockPos pos) {
        if (mc.level == null) return;
        Direction dir = Direction.DOWN;
        if (!startPredictionSend(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, dir)) {
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        pos,
                        dir
                ));
                System.out.println("[mushack] Fallback: Sent START without sequence");
            }
        }
    }

    public static void stopBreakPacket(BlockPos pos) {
        if (mc.level == null) return;
        Direction dir = Direction.DOWN;
        if (!startPredictionSend(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir)) {
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        pos,
                        dir
                ));
                System.out.println("[mushack] Fallback: Sent STOP without sequence");
            }
        }
    }

    public static void abortBreakPacket(BlockPos pos) {
        if (mc.level == null) return;
        Direction dir = Direction.DOWN;
        if (!startPredictionSend(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, dir)) {
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        pos,
                        dir
                ));
                System.out.println("[mushack] Fallback: Sent ABORT without sequence");
            }
        }
    }

    public static void breakBlockPacket(BlockPos pos, boolean swing) {
        if (mc.player == null || mc.level == null) return;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            if (currentBreakingPos != null && currentBreakingPos.equals(pos)) {
                currentBreakingPos = null;
            }
            return;
        }

        // Determine if we can insta break
        boolean insta = canInstaBreak(pos);

        if (insta) {
            // Single-tick start+stop with proper sequence
            startBreakPacket(pos);
            stopBreakPacket(pos);
            if (swing) mc.player.swing(InteractionHand.MAIN_HAND);
            currentBreakingPos = null;
            return;
        }

        // Sustained break: start once then continue each tick until broken
        Direction dir = Direction.DOWN;
        if (currentBreakingPos == null || !currentBreakingPos.equals(pos)) {
            startBreakPacket(pos);
            currentBreakingPos = pos.immutable();
        } else {
            // Continue breaking using public GameMode API (handles client prediction internally)
            if (mc.gameMode != null) {
                mc.gameMode.continueDestroyBlock(pos, dir);
            }
        }

        // Mark breaking flags for HighwayManager's postTick
        breakingThisTick = true;
        breaking = true;
        if (swing) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // Click once: send START and STOP with the same sequence directly.
    public static void clickOnce(BlockPos pos) {
        if (mc.player == null || mc.level == null || mc.player.connection == null || pos == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;
        int seq = nextSequence();
        Direction dir = Direction.DOWN;
        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                dir,
                seq
        ));
        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                dir,
                seq
        ));
    }

    // Click start only using public GameMode API (lets external FastBreak handle sustain/stop)
    public static void clickStart(BlockPos pos) {
        if (mc.gameMode == null || mc.level == null || pos == null) return;
        Direction dir = getDirection(pos);
        if (dir == null) dir = Direction.DOWN;
        mc.gameMode.startDestroyBlock(pos, dir);
    }

    // Reset held breaking state when switching modes
    public static void resetBreakingState() {
        currentBreakingPos = null;
    }

    //

    // Finds the best block direction to get when interacting with the block.
    public static Direction getDirection(BlockPos pos) {
        if (mc.player == null || mc.level == null) return null;
        // Player eye position
        Vec3 eyesPos = new Vec3(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                mc.player.getZ()
        );

        // Check if block is above player eyes
        if ((double) pos.getY() > eyesPos.y) {
            if (mc.level.getBlockState(pos.offset(0, -1, 0)).canBeReplaced()) {
                return Direction.DOWN;
            } else {
                return mc.player.getDirection().getOpposite();
            }
        }

        // If the block above is NOT replaceable → break from opposite horizontal direction
        if (!mc.level.getBlockState(pos.offset(0, 1, 0)).canBeReplaced()) {
            return mc.player.getDirection().getOpposite();
        }

        return Direction.UP;
    }

    public static boolean canBreak(BlockPos blockPos, BlockState state) {
        if (mc.player == null || mc.level == null) return false;
        // Unbreakable blocks (hardness < 0)
        if (!mc.player.isCreative() && state.getDestroySpeed(mc.level, blockPos) < 0) {
            return false;
        }

        // Must have a hitbox (shape must not be empty)
        return !state.getShape(mc.level, blockPos).isEmpty();
    }

    public static boolean canInstaBreak(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) return true;
        if (mc.player.isCreative()) return true;

        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0) return false; // unbreakable

        float speed = mc.player.getDestroySpeed(state);

        // Tool effectiveness
        if (!mc.player.hasCorrectToolForDrops(state)) {
            speed /= 10f;
        }

        // Haste vs Mining Fatigue
        if (mc.player.hasEffect(MobEffects.DIG_SPEED)) {
            int amplifier = mc.player.getEffect(MobEffects.DIG_SPEED).getAmplifier();
            speed *= (1.0f + (amplifier + 1) * 0.2f);
        }
        if (mc.player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            int amplifier = mc.player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier();
            float multiplier = switch (amplifier) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
            speed *= multiplier;
        }

        // Not on ground penalty
        if (!mc.player.onGround()) {
            speed /= 5f;
        }

        float breakDelta = speed / hardness / 30f;

        return breakDelta >= 1.0f;
    }

    public static void switchToItem(Item item) {
        if (mc.player == null) return;

        if (mc.player.getInventory().getItem(mc.player.getInventory().selected).getItem() != item) {

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == item) {
                    mc.player.getInventory().setSelectedHotbarSlot(i);
                    return;
                }
            }
        }
    }

    public static void switchToPickaxe() {
        if (mc.player == null) return;

        if (mc.player.getInventory().getItem(mc.player.getInventory().selected).getItem() instanceof PickaxeItem) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof PickaxeItem) {
                mc.player.getInventory().setSelectedHotbarSlot(i);
            }
        }
    }

    public static void handleEchestFarming(int amount) {
        // TODO
        // Yet to implement cuz item gathering is not yet finalized or implemented
    }
}
