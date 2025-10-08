package meow.utils.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meow.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.List;

public class NoClip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // === Enum для режимов выхода из noclip ===
    public enum NoClipMode {
        SIMPLE,
        DOUBLE,
        DESYNC,
        NONE
    }

    // === Setting для выбора режима ===
    private final Setting<NoClipMode> releaseMode = sgGeneral.add(new EnumSetting.Builder<NoClipMode>()
        .name("mode")
        .description("Тип выхода из noclip")
        .defaultValue(NoClipMode.SIMPLE)
        .build()
    );

    private final Setting<Integer> semiPackets = sgGeneral.add(new IntSetting.Builder()
        .name("Packets")
        .description("Количество отправляемых пакетов при полу-заклинивании")
        .defaultValue(2)
        .min(1)
        .max(15)
        .sliderRange(1, 15)
        .build()
    );

    private final List<PlayerMoveC2SPacket> bufferedPackets = new ArrayList<>();
    private boolean semiPacketSent = false;
    private boolean skipReleaseOnDisable = false;

    public NoClip() {
        super(Utils.CATEGORY, "NoClip", "Позволяет ходить сквозь блоки и стены (экспериментально).");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            bufferedPackets.add(packet);
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // === Включаем клиентский noclip ===
        mc.player.noClip = true;
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0); // горизонтальная фиксация движения

        var box = mc.player.getBoundingBox().expand(-0.001);
        var stream = mc.world.getStatesInBox(box);
        long total = stream.count();

        stream = mc.world.getStatesInBox(box);
        long solid = stream.filter(BlockState::isSolid).count();

        boolean semiInside = solid > 0 && solid < total;
        boolean noSolid = solid == 0;

        // Отправка пакетов при полу-заклинивании
        if (!semiPacketSent && semiInside) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            boolean onGround = mc.player.isOnGround();

            for (int i = 0; i < semiPackets.get(); i++) {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, true)
                );
            }
            semiPacketSent = true;
            return;
        }

        // Деактивация при выходе из блока
        if (semiPacketSent && noSolid) {
            skipReleaseOnDisable = true;
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.noClip = false; // возвращаем коллизии

        if (!skipReleaseOnDisable && semiPacketSent) {
            if (releaseMode.get() != NoClipMode.NONE) {
                runReleaseSequence(releaseMode.get());
            } else {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getYaw(), mc.player.getPitch(),
                    mc.player.isOnGround(), true
                ));
            }
        }

        if (mc.player != null && mc.player.networkHandler != null && !bufferedPackets.isEmpty()) {
            for (var packet : bufferedPackets) {
                mc.player.networkHandler.sendPacket(packet);
            }
            bufferedPackets.clear();
        }

        super.onDeactivate();
    }

    @Override
    public void onActivate() {
        bufferedPackets.clear();
        semiPacketSent = false;
        skipReleaseOnDisable = false;
        super.onActivate();
    }

    private void runReleaseSequence(NoClipMode mode) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        switch (mode) {
            case SIMPLE -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x - 5000, y, z - 5000, false, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, mc.player.isOnGround(), true));
            }
            case DOUBLE -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x - 5000, y, z - 5000, false, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x + 5000, y, z + 5000, false, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, mc.player.isOnGround(), true));
            }
            case DESYNC -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.03125, z, true, true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, mc.player.isOnGround(), true));
            }
            case NONE -> {
                // ничего не делаем
            }
        }
    }
}
