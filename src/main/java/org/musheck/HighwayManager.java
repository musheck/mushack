package org.musheck;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.musheck.utils.WorldUtils;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.musheck.utils.BlockPositions.getCardinalNukerPositions;
import static org.musheck.utils.BlockPositions.getCardinalPositions;
import static org.musheck.utils.PlayerUtils.lockCardinalRotation;
import static org.musheck.utils.PlayerUtils.walkForward;
import static org.musheck.utils.WorldUtils.*;

/**
 * Musheck Highway Builder Manager
 *
 * @author musheck
 */
public class HighwayManager extends ToggleableModule {
    /* Minecraft */
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumSetting<CurrentSetting> currentSetting = new EnumSetting<>("Setting", CurrentSetting.General);

    // General settings=
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.Place)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General);
    private final EnumSetting<Type> type = new EnumSetting<>("Type", Type.Cardinal)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General);
    private final NumberSetting<Integer> width = new NumberSetting<>("Width", "How wide you want the highway to be.", 4, 2, 10)
            .incremental(1)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General);
    private final NumberSetting<Integer> height = new NumberSetting<>("Height", "How high the highway (tunnel) should be", 3, 1, 4)
            .incremental(1)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General);
    private final BooleanSetting rails = new BooleanSetting("Rails", "Place railings on each end of pavement.", true)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General);
    private final BooleanSetting leftRail = new BooleanSetting("Left Rail", "Consider the left railing", true)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General && this.rails.getValue());
    private final BooleanSetting rightRail = new BooleanSetting("Right Rail", "Consider the right railing", true)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.General && this.rails.getValue());

    // Breaking settings
    private final NumberSetting<Integer> maxBreaksPerTick = new NumberSetting<>("Max Breaks / Tick", "How many blocks are allowed to be broken in the same tick", 4, 1, 10)
            .incremental(1)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Breaking);

    // Placement settings
    private final EnumSetting<PlacementMode> placementMode = new EnumSetting<>("Placement Mode", "How it should place blocks, either by rotating or by Airplacing", PlacementMode.Airplace)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Placement);
    private final NumberSetting<Integer> placementDelay = new NumberSetting<>("Placement Delay", "Delay between block placement attempts", 3, 3, 10)
            .incremental(1)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Placement);

    // Render settings
    private final ColorSetting placementColor = new ColorSetting("Placement Color", Color.MAGENTA)
            .setAlphaAllowed(true)
            .setThemeSyncAllowed(true)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Render);
    private final ColorSetting breakingColor = new ColorSetting("Breaking Color", Color.RED)
            .setAlphaAllowed(true)
            .setThemeSyncAllowed(true)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Render);
    private final NumberSetting<Float> lineWidth = new NumberSetting<>("Line Width", 1f, 0f, 5f)
            .incremental(0.25f)
            .setVisibility(() -> currentSetting.getValue() == CurrentSetting.Render);

    List<BlockPos> blocksToBreak = new ArrayList<>();
    List<BlockPos> blocksToPlace = new ArrayList<>();
    public static boolean breakingThisTick = false;
    public static boolean breaking = false;
    public static int swapDelay = 0;
    public static int placeTick = 0;
    public static boolean isCurrentlyBreaking = false;
    int front = 2;
    int back = 2;
    public static int playerX;
    public static int playerY;
    public static int playerZ;
    public static double alignmentX;
    public static double alignmentZ;
    public static Direction initialDirection;

    public static HighwayManager INSTANCE;

	public HighwayManager() {
		super("HighwayManager", "HighwayManager setting options", ModuleCategory.CLIENT);

        INSTANCE = this;
		
		//register settings
		this.registerSettings(
                this.currentSetting,
                // General
                this.mode,
                this.type,
				this.width,
				this.height,
				this.rails,
				this.leftRail,
				this.rightRail,
                // Placement
                this.placementMode,
                this.placementColor,
                // Breaking
                this.maxBreaksPerTick,
                // Render
                this.placementDelay,
                this.breakingColor,
                this.lineWidth
		);
	}

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) return;
        initialDirection = mc.player.getDirection();
        toggleModule("SourceRemover", true);
        if(placementMode.getValue() == PlacementMode.Airplace) {
            toggleModule("Airplace", true);
        }
        playerX = mc.player.getBlockX();
        playerY = mc.player.getBlockY();
        playerZ = mc.player.getBlockZ();
        alignmentX = mc.player.getBlockX() + 0.5;
        alignmentZ = mc.player.getBlockZ() + 0.5;
    }

    @Override
    public void onDisable() {
        toggleModule("SourceRemover", false);
        if(placementMode.getValue() == PlacementMode.Airplace) {
            toggleModule("Airplace", false);
        }
    }

    @Subscribe(stage = Stage.POST)
    public void postTick(EventUpdate event) {
        if (!breakingThisTick && breaking) {
            breaking = false;
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        }
    }

    @Subscribe(stage = Stage.PRE)
    public void preTick(EventUpdate event) {
        if(mc.player == null || mc.gameMode == null || mc.level == null) return;

        breakingThisTick = false;
        placeTick++;

        alignToCardinal(0.5);

        double distance = WorldUtils.distanceBetweenPlayerAndEndOfPavement(Blocks.OBSIDIAN);
        boolean stop = false;
        for(BlockPos pos : getCardinalPositions(0, 2, width.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
            if (org.rusherhack.client.api.utils.WorldUtils.isReplaceble(pos)) stop = true;
        }
        boolean shouldWalk = !isCurrentlyBreaking && distance > 1 && !stop;

        if (mode.getValue() == Mode.Place) {
            lockCardinalRotation(initialDirection);
            // default walking condition already computed in shouldWalk

            if (blocksToBreak.isEmpty()) {
                // Check if blocks need to be broken
                for (BlockPos pos : getCardinalPositions(front, back, width.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
                    Block block = mc.level.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHER_PORTAL || block == Blocks.BEDROCK) continue;
                    // TODO add nuker blacklist setting?

                    if(!org.rusherhack.client.api.utils.WorldUtils.isReplaceble(pos) && block != Blocks.OBSIDIAN) blocksToBreak.add(pos.immutable());
                }
                for(BlockPos pos : getCardinalNukerPositions(front, back, width.getValue(), height.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
                    Block block = mc.level.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHER_PORTAL || block == Blocks.BEDROCK) continue;
                    // TODO add nuker blacklist setting?

                    if(!(block instanceof AirBlock)) blocksToBreak.add(pos.immutable());
                }

                blocksToBreak.sort(Comparator.comparingDouble((value) -> (double) (-value.getY())));
            }

            int count = 0;
            if (!blocksToBreak.isEmpty()) {
                shouldWalk = false; // do not walk while breaking
                for (BlockPos pos : blocksToBreak) {
                    if (count >= maxBreaksPerTick.getValue()) break;
                    switchToPickaxe();
                    breakBlockPacket(pos, true);
                    isCurrentlyBreaking = true;
                    count++;
                    if (!canInstaBreak(pos)) break;
                }
                blocksToBreak.clear();
            } else {
                isCurrentlyBreaking = false;
            }

            if (!breaking) {
                if (blocksToPlace.isEmpty()) {
                    for (BlockPos pos : getCardinalPositions(front, back, width.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
                        if (org.rusherhack.client.api.utils.WorldUtils.isReplaceble(pos)) {
                            blocksToPlace.add(pos.immutable());
                        }
                    }
                }

                // Only place when it doesn't have any blocks to break
                if (!blocksToPlace.isEmpty() && !isCurrentlyBreaking) {
                    // pause walking while placing to avoid stepping off
                    if (placeTick >= placementDelay.getValue()) {
                        shouldWalk = false;
                    }
                    for (BlockPos pos : blocksToPlace) {
                        if (placeTick >= placementDelay.getValue()) {
                            placeBlock(pos, Items.OBSIDIAN);
                        } else break;
                    }
                    blocksToPlace.clear();
                }
            }
        }

        if (mode.getValue() == Mode.Dig) {
            lockCardinalRotation(initialDirection);

            if (blocksToBreak.isEmpty()) {
                // Check if blocks need to be broken
                for(BlockPos pos : getCardinalNukerPositions(front, back, width.getValue(), height.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
                    Block block = mc.level.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHER_PORTAL || block == Blocks.BEDROCK) continue;
                    // TODO add nuker blacklist setting?

                    if(!(block instanceof AirBlock)) blocksToBreak.add(pos.immutable());
                }

                blocksToBreak.sort(Comparator.comparingDouble((value) -> (double) (-value.getY())));
            }

            int count = 0;
            if (!blocksToBreak.isEmpty()) {
                if (breaking) return;
                shouldWalk = false; // do not walk while breaking
                for (BlockPos pos : blocksToBreak) {
                    if (count >= maxBreaksPerTick.getValue()) break;
                    switchToPickaxe();
                    breakBlockPacket(pos, true);
                    count++;
                    if (!canInstaBreak(pos)) break;
                }
                blocksToBreak.clear();
            }
        }

        // Apply walking decision once per tick
        walkForward(shouldWalk);
    }

    private void toggleModule(String moduleName, boolean enabled) {
        ToggleableModule module = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature(moduleName).get();

        if(enabled && !module.isToggled()) {
            module.toggle();
        } else if (!enabled && module.isToggled()) {
            module.toggle();
        }
    }

    public static EnumSetting<PlacementMode> getPlacementMode() {
        return INSTANCE.placementMode;
    }
	
	//3d renderer demo
	@Subscribe
	private void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();
		
		//begin renderer
		renderer.begin(event.getMatrixStack());
        renderer.setLineWidth(lineWidth.getValue());

        if (mode.getValue() == Mode.Place) {
            //highlight to place blocks
            for (BlockPos pos : getCardinalPositions(front, back, width.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
                if (mc.level.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
                    renderer.drawBox(pos, true, true, ColorUtils.transparency(placementColor.getValueRGB(), placementColor.getAlpha()));
                }
            }
        }

        for(BlockPos pos : getCardinalNukerPositions(front, back, width.getValue(), height.getValue(), rails.getValue(), leftRail.getValue(), rightRail.getValue())) {
            if (!(mc.level.getBlockState(pos).getBlock() instanceof AirBlock)) {
                renderer.drawBox(pos, true, true, ColorUtils.transparency(breakingColor.getValueRGB(), placementColor.getAlpha()));
            }
        }
		
		//end renderer
		renderer.end();
	}

    protected enum Mode {
        Place,
        Dig
    }

    protected enum Type {
        Cardinal,
        Diagonal
    }

    protected enum CurrentSetting {
        General,
        Placement,
        Breaking,
        Render
    }

    public enum PlacementMode {
        Rotations,
        Airplace
    }

    protected enum WhitelistMode {
        Whitelist,
        Blacklist
    }
}
