package meow.utils.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import meow.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class TPLoot extends Module {
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");
    private final SettingGroup sgSmooth = settings.createGroup("Smooth");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Filter settings
    private final Setting<Boolean> onlyPickupable = sgFilter.add(new BoolSetting.Builder()
        .name("only-pickupable")
        .description("Only pickup items that can be picked up.")
        .defaultValue(true)
        .build()
    );
    private final Setting<OperationMode> itemFilteringMode = sgFilter.add(new EnumSetting.Builder<OperationMode>()
        .name("item-filtering-mode")
        .description("Defines how items will be filtered when using the item sucker.")
        .defaultValue(OperationMode.Blacklist)
        .build()
    );
    private final Setting<List<Item>> itemWhitelist = sgFilter.add(new ItemListSetting.Builder()
        .name("item-whitelist")
        .description("Items to be exclusively picked up by the item sucker.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> itemFilteringMode.get() == OperationMode.Whitelist)
        .build()
    );
    private final Setting<List<Item>> itemBlacklist = sgFilter.add(new ItemListSetting.Builder()
        .name("item-blacklist")
        .description("Items which the item sucker should ignore.")
        .defaultValue(Items.POISONOUS_POTATO)
        .visible(() -> itemFilteringMode.get() == OperationMode.Blacklist)
        .build()
    );
    private final Setting<Double> suckingRange = sgFilter.add(new DoubleSetting.Builder()
        .name("sucking-range")
        .description("Range within which items can be picked up.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 80)
        .build()
    );
    private final Setting<Boolean> onlyOnGround = sgFilter.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only pick up items that are on the floor.")
        .defaultValue(true)
        .build()
    );

    // General settings
    private final Setting<MoveMode> moveMode = sgGeneral.add(new EnumSetting.Builder<MoveMode>()
        .name("move-mode")
        .description("Set the move mode of the item sucker.")
        .defaultValue(MoveMode.Teleport)
        .build()
    );

    // Teleport mode settings
    private final Setting<Boolean> checkCollisions = sgTeleport.add(new BoolSetting.Builder()
        .name("check-collisions")
        .description("Check if player can teleport to an item and not collide with blocks.")
        .defaultValue(true)
        .visible(() -> moveMode.get() == MoveMode.Teleport)
        .build()
    );
    private final Setting<Boolean> tpToOrigin = sgTeleport.add(new BoolSetting.Builder()
        .name("tp-to-origin")
        .description("Automatically teleport player to initial position once all items have been picked up.")
        .defaultValue(true)
        .visible(() -> moveMode.get() == MoveMode.Teleport)
        .build()
    );
    private final Setting<Integer> waitTime = sgTeleport.add(new IntSetting.Builder()
        .name("wait-time")
        .description("Time to wait after teleport (in ticks).")
        .min(0)
        .defaultValue(10)
        .visible(() -> moveMode.get() == MoveMode.Teleport && tpToOrigin.get())
        .build()
    );
    private final Setting<Boolean> resetTimeAfterTp = sgTeleport.add(new BoolSetting.Builder()
        .name("reset-time-after-tp")
        .description("Resets wait time after teleport.")
        .defaultValue(true)
        .visible(() -> moveMode.get() == MoveMode.Teleport && tpToOrigin.get())
        .build()
    );
    private final Setting<Boolean> autoLeave = sgTeleport.add(new BoolSetting.Builder()
        .name("auto-leave")
        .description("Automatically execute /spawn or /hub after picking up an item.")
        .defaultValue(false)
        .visible(() -> moveMode.get() == MoveMode.Teleport)
        .build()
    );
    private final Setting<LeaveCommand> leaveCommand = sgTeleport.add(new EnumSetting.Builder<LeaveCommand>()
        .name("leave-command")
        .description("Which command to execute after picking up an item.")
        .defaultValue(LeaveCommand.Spawn)
        .visible(() -> moveMode.get() == MoveMode.Teleport && autoLeave.get())
        .build()
    );

    // Smooth mode settings
    private final Setting<Double> smoothSpeed = sgSmooth.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("Speed of smooth movement.")
        .defaultValue(0.5)
        .min(0.1)
        .max(10)
        .sliderRange(0.1, 10)
        .visible(() -> moveMode.get() == MoveMode.Smooth)
        .build()
    );
    private final Setting<Double> smoothHeight = sgSmooth.add(new DoubleSetting.Builder()
        .name("smooth-height")
        .description("Height to fly above items when using smooth mode.")
        .defaultValue(1.0)
        .min(-5)
        .max(5)
        .sliderRange(0, 5)
        .visible(() -> moveMode.get() == MoveMode.Smooth)
        .build()
    );
    private final Setting<Boolean> smoothReverse = sgSmooth.add(new BoolSetting.Builder()
        .name("smooth-reverse")
        .description("Return to original position after collecting items.")
        .defaultValue(true)
        .visible(() -> moveMode.get() == MoveMode.Smooth)
        .build()
    );
    private final Setting<Double> smoothReturnHeight = sgSmooth.add(new DoubleSetting.Builder()
        .name("smooth-return-height")
        .description("Height when returning to origin in smooth mode.")
        .defaultValue(1.5)
        .min(-5)
        .max(5)
        .visible(() -> moveMode.get() == MoveMode.Smooth && smoothReverse.get())
        .build()
    );

    // Render settings
    private final Setting<Boolean> showTeleportBox = sgRender.add(new BoolSetting.Builder()
        .name("show-teleport-box")
        .description("Displays player hitbox at items position.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of box.")
        .defaultValue(new SettingColor(0, 0, 255, 40))
        .visible(showTeleportBox::get)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of box.")
        .defaultValue(new SettingColor(0, 0, 255, 100))
        .visible(showTeleportBox::get)
        .build()
    );

    private int timer = 0;
    private Vec3d startPos = null;
    private Vec3d targetPos = null;
    private boolean returning = false;
    private ItemEntity lastTargetItem = null;

    public TPLoot() {
        super(Utils.CATEGORY, "tploot", "Ворует предметы.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        startPos = null;
        targetPos = null;
        returning = false;
        lastTargetItem = null;
    }

    @Override
    public void onDeactivate() {
        lastTargetItem = null;
    }

    public Box getBoundingBoxAtPosition(Vec3d pos) {
        Vec3d offset = pos.subtract(mc.player.getBoundingBox().getHorizontalCenter());
        return mc.player.getBoundingBox().offset(offset.getX(), offset.getY(), offset.getZ());
    }

    public boolean canTeleportToItem(Vec3d pos) {
        Box box = getBoundingBoxAtPosition(pos);

        Iterable<VoxelShape> collisions = mc.world.getBlockCollisions(mc.player, box);
        List<VoxelShape> collisionsList = StreamSupport.stream(collisions.spliterator(), false)
            .filter(voxelShape -> !voxelShape.isEmpty()).toList();

        return collisionsList.isEmpty();
    }

    private boolean filter(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            boolean isPickupable = true;
            if (onlyPickupable.get()) {
                isPickupable = !itemEntity.cannotPickup();
            }
            boolean isWithinRange = PlayerUtils.isWithin(entity, suckingRange.get());
            boolean isOnGround = true;
            if (onlyOnGround.get()) {
                isOnGround = entity.isOnGround();
            }
            boolean isItemAllowed = (itemFilteringMode.get() == OperationMode.Blacklist && !itemBlacklist.get().contains(itemEntity.getStack().getItem()))
                || (itemFilteringMode.get() == OperationMode.Whitelist && itemWhitelist.get().contains(itemEntity.getStack().getItem()));
            boolean canTeleport = true;
            if (moveMode.get() == MoveMode.Teleport) {
                canTeleport = checkCollisions.get() && canTeleportToItem(itemEntity.getPos());
            }

            return isPickupable && isWithinRange && isOnGround && isItemAllowed && canTeleport;
        }
        return false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        timer = 0;
        startPos = null;
        targetPos = null;
        returning = false;
        lastTargetItem = null;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (moveMode.get() == MoveMode.Smooth && targetPos != null) {
            Vec3d playerPos = mc.player.getPos();
            Vec3d direction = targetPos.subtract(playerPos).normalize().multiply(smoothSpeed.get());

            if (!returning) {
                double heightAdjust = smoothHeight.get() * 0.1;
                if (playerPos.distanceTo(targetPos) < 2) {
                    heightAdjust *= playerPos.distanceTo(targetPos) / 2;
                }
                direction = direction.add(0, heightAdjust, 0);
            } else {
                double heightAdjust = smoothReturnHeight.get();
                if (playerPos.distanceTo(targetPos) < 2) {
                    heightAdjust *= playerPos.distanceTo(targetPos) / 2;
                }
                direction = direction.add(0, heightAdjust, 0);
            }

            ((IVec3d) event.movement).meteor$set(direction.x, direction.y, direction.z);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        switch (moveMode.get()) {
            case Teleport -> handleTeleportMode();
            case Smooth -> handleSmoothMode();
        }
    }

    private void handleTeleportMode() {
        Entity target = TargetUtils.get(this::filter, SortPriority.LowestDistance);

        if (timer > 0) timer -= 1;

        if (target != null) {
            if (tpToOrigin.get()) {
                if (resetTimeAfterTp.get()) timer = waitTime.get();
                if (startPos == null) {
                    startPos = mc.player.getPos();
                }
            }
            if (target instanceof ItemEntity) {
                lastTargetItem = (ItemEntity) target;
            }
            mc.player.setPosition(target.getX(), target.getY(), target.getZ());
        }

        if (timer <= 0 && tpToOrigin.get() && startPos != null) {
            mc.player.setPosition(startPos.getX(), startPos.getY(), startPos.getZ());
            startPos = null;
            timer = waitTime.get();
        }
    }

    private void handleSmoothMode() {
        List<Entity> targets = new ArrayList<>();
        TargetUtils.getList(targets, this::filter, SortPriority.LowestDistance, Integer.MAX_VALUE);

        if (!targets.isEmpty()) {
            if (startPos == null && smoothReverse.get()) {
                startPos = mc.player.getPos();
            }

            Entity closest = targets.get(0);
            targetPos = closest.getPos().add(0, smoothHeight.get(), 0);
            returning = false;
        } else if (smoothReverse.get() && startPos != null) {
            targetPos = startPos;
            returning = true;

            if (mc.player.getPos().distanceTo(startPos) < 1.1) {
                startPos = null;
                targetPos = null;
                returning = false;
            }
        } else {
            targetPos = null;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (showTeleportBox.get()) {
            List<Entity> entities = new ArrayList<>();
            TargetUtils.getList(entities, this::filter, SortPriority.LowestDistance, Integer.MAX_VALUE);
            entities.forEach(entity -> event.renderer.box(getBoundingBoxAtPosition(entity.getPos()), sideColor.get(), lineColor.get(), ShapeMode.Both, 0));
        }
    }

    @EventHandler
    public void onItemPickup(PacketEvent.Receive event) {
        if (event.packet instanceof ItemPickupAnimationS2CPacket packet &&
            autoLeave.get() &&
            moveMode.get() == MoveMode.Teleport &&
            lastTargetItem != null &&
            packet.getEntityId() == lastTargetItem.getId() &&
            packet.getCollectorEntityId() == mc.player.getId()) {

            String command = leaveCommand.get() == LeaveCommand.Spawn ? "/spawn" : "/hub";
            ChatUtils.sendPlayerMsg(command);
            lastTargetItem = null;
        }
    }

    public enum OperationMode {
        Whitelist,
        Blacklist
    }

    public enum MoveMode {
        Teleport,
        Smooth
    }

    public enum LeaveCommand {
        Spawn,
        Hub
    }
}
