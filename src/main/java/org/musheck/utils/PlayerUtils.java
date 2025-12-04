package org.musheck.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

public class PlayerUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static float cardinalDirectionToYaw(Direction dir) {
        if (dir == null) {
            // Fallback to current player yaw if available
            return mc.player != null ? mc.player.getYRot() : 0f;
        }
        return switch (dir) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;   // or -180f
            case EAST -> -90f;
            default -> 0f; // ignore UP and DOWN
        };
    }

    public static void lockCardinalRotation(Direction direction) {
        if (mc.player == null) return;
        Direction dir = direction;
        if (dir == null) dir = mc.player.getDirection();
        // Ensure horizontal direction for yaw computation
        if (dir.getAxis().isVertical()) dir = mc.player.getDirection();
        mc.player.setXRot(30); // To avoid looking at enderman
        mc.player.setYRot(cardinalDirectionToYaw(dir));
    }

    public static void walkForward(boolean pressed) {
        mc.options.keyUp.setDown(pressed);
    }
}
