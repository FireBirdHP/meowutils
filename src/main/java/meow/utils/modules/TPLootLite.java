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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class TPLootLite extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Vec3d startPos = null;
    private Vec3d deathPos = null;
    private boolean returned = false;

    private final SettingGroup gMain = settings.getDefaultGroup();
    private final SettingGroup gFilter = settings.createGroup("Filter");

    private final Setting<Boolean> autoLeave = gMain.add(new BoolSetting.Builder()
        .name("auto-leave")
        .defaultValue(true)
        .build()
    );

    private final Setting<LeaveMode> leaveMode = gMain.add(new EnumSetting.Builder<LeaveMode>()
        .name("leave-mode")
        .defaultValue(LeaveMode.HUB)
        .build()
    );

    private final Setting<TpMode> tpMode = gMain.add(new EnumSetting.Builder<TpMode>()
        .name("tp-mode")
        .defaultValue(TpMode.BYPASS)
        .build()
    );
    private final Setting<Integer> packets = gMain.add(new IntSetting.Builder()
        .name("bypass-packets")
        .min(0)
        .defaultValue(10)
        .visible(() -> tpMode.get() == TpMode.BYPASS)
        .build()
    );
    private final Setting<Boolean> bpground = gMain.add(new BoolSetting.Builder()
        .name("bypass-onground")
        .defaultValue(true)
        .visible(() -> tpMode.get() == TpMode.BYPASS)
        .build()
    );
    private final Setting<SearchMode> searchMode = gMain.add(new EnumSetting.Builder<SearchMode>()
        .name("search-mode")
        .defaultValue(SearchMode.SINGLE)
        .build()
    );

    private final Setting<List<Item>> whitelist = gFilter.add(new ItemListSetting.Builder()
        .name("items")
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

    public enum LeaveMode { HUB, SPAWN }
    public enum TpMode { POS, BYPASS }
    public enum SearchMode { SINGLE, GROUP }

    private final Set<Integer> tracked = new HashSet<>();

    public TPLootLite() {
        super(Utils.CATEGORY, "TPLoot-Lite", "Автолут предметов BETA.");
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getHealth() <= 0) deathPos = mc.player.getPos();
        if (deathPos != null && mc.player.getHealth() > 0) {
            tpSafe(deathPos);
            deathPos = null;
        }

        if (startPos == null) startPos = mc.player.getPos();

        findLoot();
        checkLoot();
    }

    @Override
    public void onActivate() {
        startPos = null;
        deathPos = null;
        tracked.clear();
        returned = false;
    }

    private void findLoot() {
        double r = 500.0;
        Vec3d p = mc.player.getPos();
        Box box = new Box(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r);

        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, box,
            e -> e.getStack() != null && whitelist.get().contains(e.getStack().getItem())
        );

        for (ItemEntity i : items) tracked.add(i.getId());
        if (items.isEmpty()) return;

        Vec3d target = null;

        if (searchMode.get() == SearchMode.SINGLE) {
            for (ItemEntity i : items) {
                Vec3d pos = safePos(i.getPos());
                if (pos != null) {
                    target = pos;
                    break;
                }
            }
        } else {
            double x = 0, y = 0, z = 0;
            int c = 0;
            for (ItemEntity i : items) {
                x += i.getX();
                y += i.getY();
                z += i.getZ();
                c++;
            }
            if (c > 0) target = safePos(new Vec3d(x / c, y / c, z / c));
        }

        if (target != null) tp(target);
    }

    private void checkLoot() {
        if (tracked.isEmpty()) return;

        Set<Integer> current = new HashSet<>();
        for (ItemEntity i : mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(500), e -> true))
            current.add(i.getId());

        boolean gone = true;
        for (Integer id : tracked) {
            if (current.contains(id)) {
                gone = false;
                break;
            }
        }

        if (gone) {
            if (!returned && startPos != null) {
                tpSafe(startPos);
                returned = true;
            }

            if (autoLeave.get()) leave();
            tracked.clear();
        }
    }

    private void leave() {
        if (mc.player == null || mc.world == null) return;
        String cmd = leaveMode.get() == LeaveMode.HUB ? "/hub" : "/spawn";
        ChatUtils.sendPlayerMsg(cmd);
        info("Ливнул на: " + cmd);
        toggle();
    }

    private void tpSafe(Vec3d pos) {
        if (mc.player == null) return;
        switch (tpMode.get()) {
            case POS -> mc.player.setPosition(pos.x, pos.y, pos.z);
            case BYPASS -> {
                if (mc.player.isAlive()) {
                    mc.player.setPosition(pos.x, pos.y, pos.z);
                    for (int i = 0; i < packets.get(); i++) {
                        boolean onGround = (i % 2 == 0);
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos, bpground.get(), false));
                    }
                }
            }
        }
    }

    private void tp(Vec3d pos) {
        if (mc.player == null) return;

        switch (tpMode.get()) {
            case POS -> mc.player.setPosition(pos.x, pos.y, pos.z);
            case BYPASS -> {
                if (mc.player.isAlive()) {
                    mc.player.setPosition(pos.x, pos.y, pos.z);
                    for (int i = 0; i < packets.get(); i++) {
                        boolean onGround = (i % 2 == 0);
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos, bpground.get(), false));
                    }
                }
            }
        }
    }

    private boolean canTp(Vec3d pos) {
        if (mc.world == null || mc.player == null) return false;
        double w = mc.player.getWidth(), h = mc.player.getHeight();
        Box b = new Box(pos.x - w / 2.0, pos.y, pos.z - w / 2.0, pos.x + w / 2.0, pos.y + h, pos.z + w / 2.0);
        return mc.world.isSpaceEmpty(b);
    }

    private Vec3d safePos(Vec3d pos) {
        if (mc.world == null || mc.player == null) return null;
        double[] off = {0.0, 0.5, -0.5};
        List<Vec3d> test = new ArrayList<>();
        test.add(pos);
        for (double dx : off)
            for (double dz : off)
                if (dx != 0 || dz != 0)
                    test.add(pos.add(dx, 0, dz));
        for (Vec3d t : test)
            if (canTp(t)) return t;
        return null;
    }
}
