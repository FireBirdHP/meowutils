package meow.utils.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import meow.utils.Utils;

public class GrimSpeed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // GrimCollision settings
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Speed multiplier.")
            .defaultValue(8.0)
            .min(1.0)
            .max(8.0)
            .sliderMin(1.0)
            .sliderMax(8.0)
            .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
            .name("radius")
            .description("Collision detection radius.")
            .defaultValue(1.0)
            .min(0.5)
            .max(1.5)
            .sliderMin(0.5)
            .sliderMax(1.5)
            .build()
    );

    private long lastSetBack;

    public GrimSpeed() {
        super(Utils.CATEGORY, "grim-speed", "Speed for GrimAC.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (System.currentTimeMillis() - lastSetBack < 1000) return;
        if (!PlayerUtils.isMoving()) return;

        int collisions = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity || entity instanceof BoatEntity)) continue;
            if (!mc.player.getBoundingBox().expand(radius.get()).intersects(entity.getBoundingBox())) continue;

            collisions++;
        }

        if (collisions > 0) {
            Vec3d motion = forward((speed.get() * 0.01) * collisions);
            mc.player.addVelocity(motion.x, 0.0, motion.z);
        }
    }

    private Vec3d forward(double value) {
        float yaw = mc.player.getYaw();
        return new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * value,
                0,
                Math.cos(Math.toRadians(yaw)) * value
        );
    }

    public void onSetBack() {
        lastSetBack = System.currentTimeMillis();
    }
}