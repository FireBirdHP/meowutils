package meow.utils.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meow.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;

import java.util.*;

public class TPLootLite extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Vec3d initialPosition = null;
    private Vec3d deathPosition = null;

    // === SETTINGS ===
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");

    private final Setting<Boolean> autoLeaveEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-leave")
        .description("Automatically leave when tracked loot disappears (picked up).")
        .defaultValue(true)
        .build()
    );

    private final Setting<LeaveMode> leaveMode = sgGeneral.add(new EnumSetting.Builder<LeaveMode>()
        .name("leave-mode")
        .description("Command used to leave the server.")
        .defaultValue(LeaveMode.HUB)
        .build()
    );

    // Новый выбор режима телепорта
    private final Setting<TeleportMode> teleportMode = sgGeneral.add(new EnumSetting.Builder<TeleportMode>()
        .name("teleport-mode")
        .description("Choose between safe client teleport or packet-based bypass teleport.")
        .defaultValue(TeleportMode.POSITION)
        .build()
    );

    // Новый выбор режима поиска
    private final Setting<SearchMode> searchMode = sgGeneral.add(new EnumSetting.Builder<SearchMode>()
        .name("search-mode")
        .description("Single item or grouped item teleport logic.")
        .defaultValue(SearchMode.SINGLE)
        .build()
    );

    // Настраиваемый whitelist предметов
    private final Setting<List<Item>> itemWhitelist = sgFilter.add(new ItemListSetting.Builder()
        .name("items")
        .description("Only these items will be tracked and teleported to.")
        .defaultValue(
            Items.TOTEM_OF_UNDYING,
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD,
            Items.NETHERITE_PICKAXE,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.PLAYER_HEAD,
            Items.SHULKER_BOX,
            Items.NETHERITE_INGOT,
            Items.SPLASH_POTION
        )
        .build()
    );

    public enum LeaveMode {
        HUB,
        SPAWN
    }

    public enum TeleportMode {
        POSITION, // Старый метод
        BYPASS    // С пакетом
    }

    public enum SearchMode {
        SINGLE, // Один предмет
        GROUP   // Группа предметов
    }

    // список отслеживаемых ItemEntity
    private final Set<Integer> trackedItems = new HashSet<>();

    public TPLootLite() {
        super(Utils.CATEGORY, "TPLoot-Lite", "Автолут предметов BETA.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getHealth() <= 0) deathPosition = mc.player.getPos();

        if (deathPosition != null && mc.player.getHealth() > 0) {
            teleportSafely(deathPosition);
            deathPosition = null;
        }

        if (initialPosition == null) initialPosition = mc.player.getPos();

        pickUpLoot();
        checkItemDisappearance();
    }

    @Override
    public void onActivate() {
        initialPosition = null;
        deathPosition = null;
        trackedItems.clear();
    }

    // === Основной процесс ===
    private void pickUpLoot() {
        double radius = 500.0;
        Vec3d playerPos = mc.player.getPos();
        Box searchBox = new Box(
            playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
        );

        // ищем предметы только из whitelist
        List<ItemEntity> nearbyItems = mc.world.getEntitiesByClass(ItemEntity.class, searchBox,
            entity -> entity.getStack() != null && itemWhitelist.get().contains(entity.getStack().getItem())
        );

        // добавляем новые предметы в трекинг
        for (ItemEntity item : nearbyItems) {
            trackedItems.add(item.getId());
        }

        // --- если предметов нет, не отправляем пакет ---
        if (nearbyItems.isEmpty()) {
            if (initialPosition != null && !autoLeaveEnabled.get()) {
                teleportSafely(initialPosition); // безопасный возврат без пакетов
                initialPosition = null;
            }
            return;
        }

        Vec3d targetPos = null;

        if (searchMode.get() == SearchMode.SINGLE) {
            // Телепорт к первому подходящему предмету
            for (ItemEntity item : nearbyItems) {
                Vec3d safePos = findSafeTeleportPos(item.getPos());
                if (safePos != null) {
                    targetPos = safePos;
                    break;
                }
            }
        } else {
            // Групповой режим — усредняем координаты всех предметов
            double sumX = 0, sumY = 0, sumZ = 0;
            int count = 0;

            for (ItemEntity item : nearbyItems) {
                sumX += item.getX();
                sumY += item.getY();
                sumZ += item.getZ();
                count++;
            }

            if (count > 0) {
                Vec3d avg = new Vec3d(sumX / count, sumY / count, sumZ / count);
                targetPos = findSafeTeleportPos(avg);
            }
        }

        if (targetPos != null) teleportTo(targetPos);
    }

    // === Проверка исчезновения предметов ===
    private void checkItemDisappearance() {
        if (!autoLeaveEnabled.get() || trackedItems.isEmpty()) return;

        Set<Integer> currentIds = new HashSet<>();
        for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(500), i -> true)) {
            currentIds.add(item.getId());
        }

        for (Integer id : trackedItems) {
            if (!currentIds.contains(id)) {
                autoLeave();
                trackedItems.clear();
                break;
            }
        }
    }

    private void autoLeave() {
        if (mc.player == null || mc.world == null) return;

        String command = switch (leaveMode.get()) {
            case HUB -> "/hub";
            case SPAWN -> "/spawn";
        };

        ChatUtils.sendPlayerMsg(command);
        info("AutoLeave triggered: " + command);
        toggle();
    }

    // === Безопасный возврат без пакетов ===
    private void teleportSafely(Vec3d pos) {
        if (mc.player == null) return;
        mc.player.setPosition(pos.x, pos.y, pos.z);
    }

    // === Метод телепортации с выбором режима ===
    private void teleportTo(Vec3d pos) {
        if (mc.player == null) return;

        switch (teleportMode.get()) {
            case POSITION -> {
                mc.player.setPosition(pos.x, pos.y, pos.z);
            }
            case BYPASS -> {
                if ((mc.player.isAlive()) && (mc.player != null)) {
                    mc.player.setPosition(pos.x, pos.y, pos.z);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos, true, false));
                }
            }
        }
    }

    // === Проверка доступности телепортации ===
    private boolean canTeleportTo(Vec3d pos) {
        if (mc.world == null || mc.player == null) return false;

        double width = mc.player.getWidth();
        double height = mc.player.getHeight();

        Box playerBox = new Box(
            pos.x - width / 2.0,
            pos.y,
            pos.z - width / 2.0,
            pos.x + width / 2.0,
            pos.y + height,
            pos.z + width / 2.0
        );

        return mc.world.isSpaceEmpty(playerBox);
    }

    // === Поиск безопасной позиции рядом с целью ===
    private Vec3d findSafeTeleportPos(Vec3d pos) {
        if (mc.world == null || mc.player == null) return null;

        double[] offsets = {0.0, 0.5, -0.5};
        List<Vec3d> testPositions = new ArrayList<>();
        testPositions.add(pos);

        for (double dx : offsets) {
            for (double dz : offsets) {
                if (dx == 0 && dz == 0) continue;
                testPositions.add(pos.add(dx, 0, dz));
            }
        }

        for (Vec3d testPos : testPositions) {
            if (canTeleportTo(testPos)) return testPos;
        }

        return null;
    }
}
