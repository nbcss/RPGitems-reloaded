package think.rpgitems.power;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.RPGItem;
import think.rpgitems.support.WGSupport;

/**
 * BukkitRunnable that runs {@link PowerTick#tick(Player, ItemStack)}
 */
public class PowerTicker extends BukkitRunnable {

    @Override
    public void run() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (WGSupport.canNotPvP(player)) continue;
            ItemStack[] armour = player.getInventory().getArmorContents();
            for (ItemStack part : armour) {
                RPGItem item = ItemManager.toRPGItem(part);
                if (item == null)
                    continue;
                item.power(player, part, null, Trigger.TICK);
                if (item.getDurability(part) <= 0) {
                    part.setType(Material.AIR);
                }
            }
            ItemStack part = player.getInventory().getItemInMainHand();
            RPGItem item = ItemManager.toRPGItem(part);
            if (item == null)
                continue;
            if (item.getDurability(part) <= 0) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            item.power(player, part, null, Trigger.TICK);
        }
    }

}
