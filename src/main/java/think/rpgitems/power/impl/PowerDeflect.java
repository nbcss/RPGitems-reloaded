package think.rpgitems.power.impl;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import think.rpgitems.Events;
import think.rpgitems.I18n;
import think.rpgitems.RPGItems;
import think.rpgitems.power.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static think.rpgitems.power.Utils.*;

/**
 * Power deflect.
 * <p>
 * Deflect arrows or fireballs towards player within {@link #facing} when
 * 1. manual triggered when some of initiative trigger are enabled with a cooldown of {@link #cooldown} and duration {@link #duration}
 * 2. auto triggered when {@link TriggerType#HIT_TAKEN} is enabled with a chance of {@link #chance} and a cooldown of {@link #cooldownpassive}
 * </p>
 */
@SuppressWarnings("WeakerAccess")
@PowerMeta(defaultTrigger = "RIGHT_CLICK")
public class PowerDeflect extends BasePower implements PowerHitTaken, PowerRightClick, PowerLeftClick {

    /**
     * Cooldown time of this power
     */
    @Property(order = 2)
    public int cooldown = 20;

    /**
     * Cooldown time of this power in passive mode
     */
    @Property(order = 4)
    public int cooldownpassive = 20;

    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;

    /**
     * Chance in percentage of triggering this power in passive mode
     */
    @Property
    public int chance = 50;

    /**
     * Duration of this power
     */
    @Property
    public int duration = 50;

    /**
     * Maximum view angle
     */
    @Property(order = 0, required = true)
    public double facing = 30;

    private static Map<UUID, Long> time = new HashMap<>();

    @Override
    public String displayText() {
        return I18n.format("power.deflect", (double) cooldown / 20d);
    }

    @Override
    public String getName() {
        return "deflect";
    }

    @Override
    public void init(ConfigurationSection section) {
        cooldownpassive = section.getInt("cooldownpassive", 20);
        boolean passive = section.getBoolean("passive", false);
        boolean initiative = section.getBoolean("initiative", true);
        boolean isRight = section.getBoolean("isRight", true);
        triggers = new HashSet<>();
        if(passive){
            triggers.add(Trigger.HIT_TAKEN);
        }
        if(initiative){
            triggers.add(isRight ? Trigger.RIGHT_CLICK : Trigger.LEFT_CLICK);
        }
        super.init(section);
    }

    @Override
    public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
        if (!((System.currentTimeMillis() / 50 < time.get(target.getUniqueId()))
                      || (ThreadLocalRandom.current().nextInt(0, 100) < chance) && checkCooldown(this, target, cooldownpassive, false))
                    || !getItem().consumeDurability(stack, cost))
            return PowerResult.noop();
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent byEntityEvent = (EntityDamageByEntityEvent) event;
            if (byEntityEvent.getDamager() instanceof Projectile) {
                Projectile p = (Projectile) byEntityEvent.getDamager();
                if (!(p.getShooter() instanceof LivingEntity)) return PowerResult.noop();
                LivingEntity source = (LivingEntity) p.getShooter();
                Vector relativePosition = target.getEyeLocation().toVector();
                relativePosition.subtract(source.getEyeLocation().toVector());
                if (getAngleBetweenVectors(target.getEyeLocation().getDirection(), relativePosition.multiply(-1)) < facing
                            && (p instanceof SmallFireball || p instanceof LargeFireball || p instanceof Arrow)) {
                    event.setCancelled(true);
                    p.remove();
                    target.getLocation().getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 3.0f);
                    Bukkit.getScheduler().runTaskLater(RPGItems.plugin, () -> {
                        if (!target.isOnline() || target.isDead()) {
                            return;
                        }
                        Projectile t = target.launchProjectile(p.getClass());
                        if (p instanceof TippedArrow) {
                            TippedArrow tippedArrowP = (TippedArrow) p;
                            TippedArrow tippedArrowT = (TippedArrow) t;
                            tippedArrowT.setBasePotionData(tippedArrowP.getBasePotionData());
                            tippedArrowP.getCustomEffects().forEach(potionEffect -> tippedArrowT.addCustomEffect(potionEffect, true));
                        }
                        t.setShooter(target);
                        t.setMetadata("rpgitems.force", new FixedMetadataValue(RPGItems.plugin, 1));
                        Events.removeArrows.add(t.getEntityId());
                    }, 1);
                    return PowerResult.ok(0.0);
                }
            }
        }
        return PowerResult.noop();
    }

    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        if (!checkCooldownByString(player, getItem(), "deflect.initiative", cooldown, true))
            return PowerResult.noop();
        if (!getItem().consumeDurability(stack, cost))
            return PowerResult.cost();
        time.put(player.getUniqueId(), System.currentTimeMillis() / 50 + duration);
        return PowerResult.ok();
    }

    @Override
    public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        if (!checkCooldownByString(player, getItem(), "deflect.initiative", cooldown, true))
            return PowerResult.noop();
        if (!getItem().consumeDurability(stack, cost))
            return PowerResult.cost();
        time.put(player.getUniqueId(), System.currentTimeMillis() / 50 + duration);
        return PowerResult.ok();
    }
}
