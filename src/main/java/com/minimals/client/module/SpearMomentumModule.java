package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

/**
 * Spear Momentum: while a spear is held, shows under the crosshair how much of a full-speed
 * charge attack's damage the current movement would deal, as a percentage that turns from
 * white to red as it rises.
 *
 * The maths mirror vanilla exactly ({@link KineticWeapon#damageEntities}): the attacker's
 * speed along the look direction minus the target's speed along the same direction (never
 * below 0), and bonus damage = floor(relativeSpeed * damage_multiplier). Speeds are
 * Entity#getKnownSpeed() * 20 (blocks/second), which is computed from position deltas and so
 * is available on the client without any server data.
 *
 * 100% is defined as the bonus damage a spear would deal at the configured Max Speed; it is
 * a reference point chosen for readability, since vanilla damage has no upper limit.
 */
public class SpearMomentumModule extends Module {

    public final IntSetting maxSpeed = addSetting(new IntSetting("Max Speed", 30, 10, 80, 5, "b/s"));
    public final BoolSetting showDamage = addSetting(new BoolSetting("Show Damage", true));
    public final BoolSetting onlyWhenCharging = addSetting(new BoolSetting("Only When Charging", false));

    public SpearMomentumModule() {
        super("Spear Momentum", Category.COMBAT);
    }

    /** One reading of the current momentum. */
    public record Reading(float percent, int bonusDamage, double relativeSpeed, boolean meetsDamageThreshold) {
    }

    /**
     * Momentum for the held spear, or null when no spear is held (or the module has nothing to show).
     * Uses the entity under the crosshair as the target when there is one, otherwise the
     * attacker's own speed, i.e. what a stationary target would receive.
     */
    public Reading read() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }

        ItemStack stack = player.getMainHandItem();
        KineticWeapon weapon = stack.get(DataComponents.KINETIC_WEAPON);
        if (weapon == null) {
            return null;
        }
        if (onlyWhenCharging.get() && !player.isUsingItem()) {
            return null;
        }

        Vec3 look = player.getLookAngle();
        double attackerProjection = look.dot(motion(player));
        double targetProjection = 0.0;
        Entity target = mc.crosshairPickEntity;
        if (target != null) {
            targetProjection = look.dot(motion(target));
        }
        double relativeSpeed = Math.max(0.0, attackerProjection - targetProjection);

        int bonus = Mth.floor(relativeSpeed * weapon.damageMultiplier());
        int maxBonus = Math.max(1, Mth.floor(maxSpeed.get() * weapon.damageMultiplier()));
        float percent = Mth.clamp((float) bonus / (float) maxBonus, 0f, 1f) * 100f;

        boolean meetsThreshold = weapon.damageConditions()
                .map(c -> relativeSpeed >= c.minRelativeSpeed() && attackerProjection >= c.minSpeed())
                .orElse(true);
        return new Reading(percent, bonus, relativeSpeed, meetsThreshold);
    }

    /**
     * Velocity in blocks per second, same source vanilla's KineticWeapon uses: a passenger
     * counts as its root vehicle (so riding a horse uses the horse's speed), except players.
     */
    private static Vec3 motion(Entity entity) {
        Entity source = entity;
        if (!(source instanceof Player) && source.isPassenger()) {
            source = source.getRootVehicle();
        }
        return source.getKnownSpeed().scale(20.0);
    }
}
