package meow.utils.modules;

import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import meow.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LeaveFinder extends Module {
    private final Map<UUID, Vec3d> lastPlayerPositions = new HashMap<>();

    public LeaveFinder() {
        super(Utils.CATEGORY, "leave-finder", "Отслеживает выход игроков и их возможные телепортации.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Ловим пакеты телепортации игроков
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            if (mc.player == null) return;

            // Проверяем всех игроков рядом
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof AbstractClientPlayerEntity) || entity instanceof ClientPlayerEntity) continue;

                AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) entity;
                double distance = mc.player.distanceTo(player);

                // Если игрок в радиусе 100 блоков, сохраняем его последнюю позицию
                if (distance < 100) {
                    lastPlayerPositions.put(player.getUuid(), player.getPos());
                }
            }
        }
    }

    @EventHandler
    private void onEntityLeave(EntityRemovedEvent event) {
        Entity entity = event.entity;

        if (!isEntityValid(entity)) return;

        UUID playerUuid = entity.getUuid();
        Vec3d lastPosition = lastPlayerPositions.get(playerUuid);

        String message;
        if (lastPosition != null) {
            // Если была записана предыдущая позиция, предполагаем телепортацию
            message = String.format(
                "§cИгрок §f%s §cтелепортировался с §7[%.1f, %.1f, %.1f] §cна §7[%.1f, %.1f, %.1f]",
                entity.getDisplayName().getString(),
                lastPosition.x, lastPosition.y, lastPosition.z,
                entity.getX(), entity.getY(), entity.getZ()
            );
            lastPlayerPositions.remove(playerUuid);
        } else {
            // Если данных нет, просто фиксируем выход
            message = String.format(
                "§cИгрок §f%s §cливнул на §7[%.1f, %.1f, %.1f]",
                entity.getDisplayName().getString(),
                entity.getX(), entity.getY(), entity.getZ()
            );
        }

        info(message);
    }

    private boolean isEntityValid(Entity entity) {
        if (!(entity instanceof AbstractClientPlayerEntity) || entity instanceof ClientPlayerEntity) {
            return false;
        }
        return mc.player != null && mc.player.distanceTo(entity) < 100;
    }
}
