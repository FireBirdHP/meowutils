package meow.utils.modules;

import meow.utils.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

public class ShulkerJump extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> power = sgGeneral.add(new DoubleSetting.Builder()
        .name("power")
        .description("Jump power when near a Shulker")
        .defaultValue(1.0)
        .min(0.1)
        .max(4.0)
        .build()
    );

    public ShulkerJump() {
        super(Utils.CATEGORY, "shulker-jump", "Automatically jumps when near a Shulker.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ChunkPos playerChunkPos = new ChunkPos(mc.player.getBlockPos());

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Chunk chunk = mc.world.getChunk(playerChunkPos.x + x, playerChunkPos.z + z);
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockEntity blockEntity = mc.world.getBlockEntity(pos);
                    if (blockEntity instanceof ShulkerBoxBlockEntity shulker) {
                        double distance = Math.sqrt(Math.pow(mc.player.getX() - (pos.getX() + 0.5), 2) +
                            Math.pow(mc.player.getZ() - (pos.getZ() + 0.5), 2));
                        double yDiff = Math.abs(mc.player.getY() - (pos.getY() + 0.5));

                        if (distance <= 1 && yDiff <= (mc.player.getVelocity().y > 1 ? 30 : 2) && mc.player.fallDistance == 0) {
                            if (shulker.getAnimationProgress(1.0f) > 0.0f && shulker.getAnimationProgress(1.0f) != 1.0) {
                                mc.player.setVelocity(mc.player.getVelocity().x, power.get(), mc.player.getVelocity().z);
                            }
                        }
                    }
                }

            }
        }
    }
}
