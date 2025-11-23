package org.musheck.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

import static org.musheck.HighwayManager.*;

public class BlockPositions {
    private static final Minecraft mc = Minecraft.getInstance();

    public static BlockPos[] getCardinalPositions(int back, int front, int width, boolean rails, boolean leftRail, boolean rightRail) {

        // Calculate left/right bounds
        int leftBound, rightBound;
        if (width % 2 == 0) {
            leftBound = width / 2;
            rightBound = leftBound - 1;
        } else {
            leftBound = rightBound = (width - 1) / 2;
        }

        Direction direction = mc.player.getDirection();

        // Offsets for each cardinal direction
        int dx, dz, start, end, step, sideSign;
        switch (direction) {
            case NORTH -> {dx = 0; dz = 1; start = front; end = -back; step = -1; sideSign = 1; playerZ = mc.player.getBlockZ();}
            case SOUTH -> {dx = 0; dz = 1; start = -front; end = back; step = 1; sideSign = -1; playerZ = mc.player.getBlockZ();}
            case EAST  -> {dx = 1; dz = 0; start = -front; end = back; step = 1; sideSign = 1; playerX =  mc.player.getBlockX();}
            case WEST  -> {dx = 1; dz = 0; start = front; end = -back; step = -1; sideSign = -1; playerX =  mc.player.getBlockX();}
            default -> { return new BlockPos[0]; }
        }

        List<BlockPos> positions = new ArrayList<>();

        for (int i = start; i != end + step; i += step) {
            for (int j = -leftBound; j <= rightBound; j++) {
                int x = playerX + dx * i + dz * (j * sideSign);
                int z = playerZ + dz * i + dx * (j * sideSign);
                positions.add(new BlockPos(x, playerY - 1, z));
            }

            if (rails) {
                if (leftRail) {
                    int railLeftX = playerX + dx * i + dz * ((-leftBound - 1) * sideSign);
                    int railLeftZ = playerZ + dz * i + dx * ((-leftBound - 1) * sideSign);
                    positions.add(new BlockPos(railLeftX, playerY, railLeftZ));
                }

                if (rightRail) {
                    int railRightX = playerX + dx * i + dz * ((rightBound + 1) * sideSign);
                    int railRightZ = playerZ + dz * i + dx * ((rightBound + 1) * sideSign);
                    positions.add(new BlockPos(railRightX, playerY, railRightZ));
                }

            }
        }
        return positions.toArray(new BlockPos[0]);
    }

    public static BlockPos[] getCardinalNukerPositions(int back, int front, int width, int height, boolean rails, boolean leftRail, boolean rightRail) {
        // Calculate left/right bounds
        int leftBound, rightBound;
        if (width % 2 == 0) {
            leftBound = (width / 2) + 1;
            rightBound = leftBound - 1;
        } else {
            leftBound = rightBound = ((width - 1) / 2) + 1;
        }

        Direction dir = mc.player.getDirection();
        List<BlockPos> positions = new ArrayList<>();

        // Directional offsets
        int dx = 0, dz = 0, step = 1;
        switch (dir) {
            case NORTH -> { dz = 1; step = -1; playerZ = mc.player.getBlockZ();}
            case SOUTH -> { dz = -1; playerZ = mc.player.getBlockZ();}
            case EAST  -> { dx = 1; playerX =  mc.player.getBlockX();}
            case WEST  -> { dx = -1; step = -1; playerX =  mc.player.getBlockX();}
            default -> { return new BlockPos[0]; }
        }

        // Loop behind the player
        for (int i = -back; i <= front; i++) {
            int offset = i * step;

            // Clear rails above (if enabled)
            if (rails) {
                for (int k = 0; k < height; k++) {
                    if (leftRail) {
                        // left rail
                        int railLeftX = playerX + dx * offset + dz * -leftBound;
                        int railLeftZ = playerZ + dz * offset + dx * -leftBound;
                        positions.add(new BlockPos(railLeftX, playerY + k + 1, railLeftZ));
                    }

                    if (rightRail) {
                        // right rail
                        int railRightX = playerX + dx * offset + dz * rightBound;
                        int railRightZ = playerZ + dz * offset + dx * rightBound;
                        positions.add(new BlockPos(railRightX, playerY + k + 1, railRightZ));
                    }
                }
            }

            // Fill between rails (normal digging area)
            for (int j = -leftBound + 1; j < rightBound; j++) {
                for (int k = 0; k <= height; k++) {
                    int x = playerX + dx * offset + dz * j;
                    int z = playerZ + dz * offset + dx * j;
                    positions.add(new BlockPos(x, playerY + k, z));
                }
            }
        }

        return positions.toArray(new BlockPos[0]);
    }
}
