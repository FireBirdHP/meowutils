package meow.utils.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import meow.utils.Utils;

public class PathFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General settings
    private final Setting<Integer> xPos = sgGeneral.add(new IntSetting.Builder()
            .name("x-coordinate")
            .description("X coordinate to fly to")
            .defaultValue(0)
            .build()
    );

    private final Setting<Integer> yPos = sgGeneral.add(new IntSetting.Builder()
            .name("y-coordinate")
            .description("Y coordinate to fly to")
            .defaultValue(100)
            .build()
    );

    private final Setting<Integer> zPos = sgGeneral.add(new IntSetting.Builder()
            .name("z-coordinate")
            .description("Z coordinate to fly to")
            .defaultValue(0)
            .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Flying speed")
            .defaultValue(1)
            .min(0.1)
            .sliderRange(0.1, 5)
            .build()
    );

    private final Setting<Boolean> stopAtDestination = sgGeneral.add(new BoolSetting.Builder()
            .name("stop-at-destination")
            .description("Automatically disable when reaching destination")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> arrivalThreshold = sgGeneral.add(new DoubleSetting.Builder()
            .name("arrival-threshold")
            .description("Distance at which you've arrived")
            .defaultValue(0.5)
            .min(0.1)
            .build()
    );

    // Teleport settings
    private final Setting<Boolean> teleportMode = sgTeleport.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Enable teleport mode")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> blocksPerTeleport = sgTeleport.add(new IntSetting.Builder()
            .name("blocks-per-teleport")
            .description("How many blocks to teleport at once")
            .defaultValue(10)
            .min(1)
            .max(100)
            .visible(teleportMode::get)
            .build()
    );

    private final Setting<Integer> teleportDelay = sgTeleport.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay between teleports in ticks (20 ticks = 1 second)")
            .defaultValue(5)
            .min(0)
            .max(100)
            .visible(teleportMode::get)
            .build()
    );

    // Safety settings
    private final Setting<Boolean> avoidBlocks = sgSafety.add(new BoolSetting.Builder()
            .name("avoid-blocks")
            .description("Try to avoid teleporting into solid blocks")
            .defaultValue(true)
            .build()
    );

    // Render settings
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
            .name("render-path")
            .description("Renders the path to the destination")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder()
            .name("path-color")
            .description("Color of the rendered path")
            .defaultValue(new SettingColor(255, 0, 0, 150))
            .visible(renderPath::get)
            .build()
    );

    private final Setting<Double> renderHeight = sgRender.add(new DoubleSetting.Builder()
            .name("render-height")
            .description("Height above blocks to render the path")
            .defaultValue(0.5)
            .min(0)
            .max(2)
            .sliderMax(2)
            .visible(renderPath::get)
            .build()
    );

    private int teleportTimer = 0;
    private Vec3d nextTeleportPos;

    public PathFly() {
        super(Utils.CATEGORY, "PathFly", "Flies or teleports you to specified coordinates");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            error("Player is null!");
            toggle();
            return;
        }

        teleportTimer = 0;
        nextTeleportPos = mc.player.getPos();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            error("Player is null!");
            toggle();
            return;
        }

        Vec3d targetPos = new Vec3d(xPos.get(), yPos.get(), zPos.get());
        Vec3d playerPos = mc.player.getPos();

        // Check if we've arrived
        if (playerPos.distanceTo(targetPos) < arrivalThreshold.get()) {
            if (stopAtDestination.get()) {
                info("Arrived at destination!");
                toggle();
            }
            return;
        }

        if (teleportMode.get()) {
            handleGradualTeleport(playerPos, targetPos);
        } else {
            handleNormalFlight(playerPos, targetPos);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderPath.get() || mc.player == null) return;

        Vec3d start = mc.player.getPos();
        Vec3d target = new Vec3d(xPos.get(), yPos.get(), zPos.get());

        // Adjust height
        start = start.add(0, renderHeight.get(), 0);
        target = target.add(0, renderHeight.get(), 0);

        // Render main line
        event.renderer.line(start.x, start.y, start.z, target.x, target.y, target.z, pathColor.get());

        // If in teleport mode, render next teleport position
        if (teleportMode.get() && nextTeleportPos != null) {
            Vec3d nextPos = nextTeleportPos.add(0, renderHeight.get(), 0);
        }
    }

    private void handleNormalFlight(Vec3d playerPos, Vec3d targetPos) {
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        if (avoidBlocks.get()) {
            direction = adjustDirectionForBlocks(playerPos, direction);
        }

        mc.player.setVelocity(
                direction.x * speed.get(),
                direction.y * speed.get(),
                direction.z * speed.get()
        );
    }

    private void handleGradualTeleport(Vec3d playerPos, Vec3d targetPos) {
        if (teleportTimer <= 0) {
            // Calculate direction to target
            Vec3d direction = targetPos.subtract(playerPos).normalize();

            // Calculate next teleport position
            nextTeleportPos = playerPos.add(direction.multiply(blocksPerTeleport.get()));

            // If we would overshoot the target, just go to target
            if (nextTeleportPos.distanceTo(playerPos) > playerPos.distanceTo(targetPos)) {
                nextTeleportPos = targetPos;
            }

            // Check if position is safe
            if (avoidBlocks.get() && !isPositionSafe(nextTeleportPos)) {
                // Try to find safe position above
                Vec3d safePos = findSafePosition(nextTeleportPos);
                if (safePos != null) {
                    nextTeleportPos = safePos;
                } else {
                    error("Could not find safe position to teleport!");
                    return;
                }
            }

            // Perform the teleport
            mc.player.updatePosition(nextTeleportPos.x, nextTeleportPos.y, nextTeleportPos.z);

            // Reset timer
            teleportTimer = teleportDelay.get();
        } else {
            teleportTimer--;
        }
    }

    private boolean isPositionSafe(Vec3d pos) {
        BlockPos blockPos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
        return mc.world.getBlockState(blockPos).getCollisionShape(mc.world, blockPos) == VoxelShapes.empty();
    }

    private Vec3d findSafePosition(Vec3d originalPos) {
        // Check above
        for (int i = 1; i <= 5; i++) {
            Vec3d checkPos = originalPos.add(0, i, 0);
            if (isPositionSafe(checkPos)) {
                return checkPos;
            }
        }
        return null;
    }

    private Vec3d adjustDirectionForBlocks(Vec3d currentPos, Vec3d direction) {
        Vec3d newPos = currentPos.add(direction.multiply(speed.get() * 2));
        BlockPos blockPos = new BlockPos((int) newPos.x, (int) newPos.y, (int) newPos.z);
        if (mc.world.getBlockState(blockPos).getCollisionShape(mc.world, blockPos) != VoxelShapes.empty()) {
            return new Vec3d(direction.x, 0.5, direction.z).normalize();
        }
        return direction;
    }
}