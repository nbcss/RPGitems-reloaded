package think.rpgitems;

import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaacore.Message;
import com.google.common.base.Strings;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.Quality;
import think.rpgitems.item.RPGItem;
import think.rpgitems.power.*;
import think.rpgitems.support.WGSupport;
import think.rpgitems.utils.MaterialUtils;
import think.rpgitems.utils.NetworkUtils;
import think.rpgitems.utils.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static think.rpgitems.utils.NetworkUtils.Location.GIST;

public class Handler extends RPGCommandReceiver {
    private final RPGItems plugin;

    Handler(RPGItems plugin, LanguageRepository i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "";
    }

    @SubCommand("reload")
    @Attribute("command")
    public void reload(CommandSender sender, Arguments args) {
        plugin.cfg = new Configuration(plugin);
        plugin.cfg.load();
        plugin.i18n.load();
        WGSupport.reload();
        ItemManager.reload(plugin);
        if (plugin.cfg.localeInv) {
            Events.useLocaleInv = true;
        }
        plugin.managedPlugins.forEach(Bukkit.getPluginManager()::disablePlugin);
        plugin.managedPlugins.clear();
        plugin.loadExtensions();
        plugin.managedPlugins.forEach(Bukkit.getPluginManager()::enablePlugin);
        sender.sendMessage(ChatColor.GREEN + "[RPGItems] Reloaded RPGItems.");
    }

    @SubCommand("list")
    @Attribute("command:name:,display:,type:")
    public void listItems(CommandSender sender, Arguments args) {
        int perPage = RPGItems.plugin.cfg.itemPerPage;
        String nameSearch = args.argString("n", args.argString("name", ""));
        String displaySearch = args.argString("d", args.argString("display", ""));
        String typeSearch = args.argString("t", args.argString("type", ""));
        List<RPGItem> items = ItemManager.itemByName.values()
                                                    .stream()
                                                    .filter(i -> i.getName().contains(nameSearch))
                                                    .filter(i -> i.getDisplay().contains(displaySearch))
                                                    .filter(i -> i.getType().contains(typeSearch))
                                                    .sorted(Comparator.comparing(RPGItem::getName))
                                                    .collect(Collectors.toList());
        Stream<RPGItem> stream = items.stream();
        int max = (int) Math.ceil(items.size() / (double) perPage);
        int page = args.top() == null ? 1 : args.nextInt();
        if (!(0 < page && page <= max)) {
            throw new BadCommandException("message.num_out_of_range", page, 0, max);
        }
        stream = stream
                         .skip((page - 1) * perPage)
                         .limit(perPage);
        sender.sendMessage(ChatColor.AQUA + "RPGItems: " + page + " / " + max);

        stream.forEach(
                item -> new Message("")
                                .append(I18n.format("message.item.list", item.getName()), Collections.singletonMap("{item}", item.getComponent()))
                                .send(sender)
        );
    }

    @SubCommand("worldguard")
    @Attribute("command")
    public void toggleWorldGuard(CommandSender sender, Arguments args) {
        if (!WGSupport.isEnabled()) {
            msg(sender, "message.worldguard.error");
            return;
        }
        if (WGSupport.useWorldGuard) {
            msg(sender, "message.worldguard.disable");
        } else {
            msg(sender, "message.worldguard.enable");
        }
        WGSupport.useWorldGuard = !WGSupport.useWorldGuard;
        RPGItems.plugin.cfg.useWorldGuard = WGSupport.useWorldGuard;
        RPGItems.plugin.cfg.save();
    }

    @SubCommand("wgforcerefresh")
    @Attribute("command")
    public void toggleForceRefresh(CommandSender sender, Arguments args) {
        if (!WGSupport.isEnabled()) {
            msg(sender, "message.worldguard.error");
            return;
        }
        if (WGSupport.forceRefresh) {
            msg(sender, "message.wgforcerefresh.disable");
        } else {
            msg(sender, "message.wgforcerefresh.enable");
        }
        WGSupport.forceRefresh = !WGSupport.forceRefresh;
        RPGItems.plugin.cfg.wgForceRefresh = WGSupport.forceRefresh;
        RPGItems.plugin.cfg.save();
    }

    @SubCommand("wgignore")
    @Attribute("item")
    public void itemToggleWorldGuard(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (!WGSupport.isEnabled()) {
            msg(sender, "message.worldguard.error");
            return;
        }
        item.ignoreWorldGuard = !item.ignoreWorldGuard;
        if (item.ignoreWorldGuard) {
            msg(sender, "message.worldguard.override.active");
        } else {
            msg(sender, "message.worldguard.override.disabled");
        }
        ItemManager.save();
    }

    @SubCommand("create")
    @Attribute("item")
    public void createItem(CommandSender sender, Arguments args) {
        String itemName = args.nextString();
        if (ItemManager.newItem(itemName.toLowerCase(), sender) != null) {
            msg(sender, "message.create.ok", itemName);
            ItemManager.save();
        } else {
            msg(sender, "message.create.fail");
        }
    }

    @SubCommand("giveperms")
    @Attribute("command")
    public void givePerms(CommandSender sender, Arguments args) {
        RPGItems.plugin.cfg.givePerms = !RPGItems.plugin.cfg.givePerms;
        if (RPGItems.plugin.cfg.givePerms) {
            msg(sender, "message.giveperms.required");
        } else {
            msg(sender, "message.giveperms.canceled");
        }
        RPGItems.plugin.cfg.save();
    }

    @SubCommand("give")
    @Attribute("item")
    public void giveItem(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (args.length() == 2) {
            if (sender instanceof Player) {
                if ((!plugin.cfg.givePerms && sender.hasPermission("rpgitem")) || (plugin.cfg.givePerms && sender.hasPermission("rpgitem.give." + item.getName()))) {
                    item.give((Player) sender);
                    msg(sender, "message.give.ok", item.getDisplay());
                } else {
                    msg(sender, "message.error.permission", item.getDisplay());
                }
            } else {
                msg(sender, "message.give.console");
            }
        } else {
            Player player = args.nextPlayer();
            int count;
            try {
                count = args.nextInt();
            } catch (BadCommandException e) {
                count = 1;
            }
            for (int i = 0; i < count; i++) {
                item.give(player);
            }

            msg(sender, "message.give.to", item.getDisplay() + ChatColor.AQUA, player.getName());
            msg(player, "message.give.ok", item.getDisplay());
        }
    }

    @SubCommand("remove")
    @Attribute("item")
    public void removeItem(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        ItemManager.remove(item);
        msg(sender, "message.remove.ok", item.getName());
    }

    @SubCommand("display")
    @Attribute("item")
    public void itemDisplay(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String value = args.next();
        if (value != null) {
            item.setDisplay(value);
            msg(sender, "message.display.set", item.getName(), item.getDisplay());
            ItemManager.save(item);
        } else {
            msg(sender, "message.display.get", item.getName(), item.getDisplay());
        }
    }

    @SubCommand("quality")
    @Attribute("item")
    public void itemQuality(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        try {
            Quality quality = args.nextEnum(Quality.class);
            item.setQuality(quality);
            msg(sender, "message.quality.set", item.getName(), item.getQuality().toString().toLowerCase());
            ItemManager.save(item);
        } catch (BadCommandException e) {
            msg(sender, "message.quality.get", item.getName(), item.getQuality().toString().toLowerCase());
        }
    }

    @SubCommand("damage")
    @Attribute("item")
    public void itemDamage(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        try {
            int damageMin = args.nextInt();
            int damageMax;
            if (damageMin > 32767) {
                msg(sender, "message.error.damagetolarge");
                return;
            }
            try {
                damageMax = args.nextInt();
            } catch (BadCommandException e) {
                damageMax = damageMin;
            }
            item.setDamage(damageMin, damageMax);
            if (damageMin != damageMax) {
                msg(sender, "message.damage.set.range", item.getName(), item.getDamageMin(), item.getDamageMax());
            } else {
                msg(sender, "message.damage.set.value", item.getName(), item.getDamageMin());
            }
            ItemManager.save(item);
        } catch (BadCommandException e) {
            msg(sender, "message.damage.get", item.getName(), item.getDamageMin(), item.getDamageMax());
        }
    }

    @SubCommand("armour")
    @Attribute("item")
    public void itemArmour(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        try {
            int armour = args.nextInt();
            item.setArmour(armour);
            msg(sender, "message.armour.set", item.getName(), item.getArmour());
            ItemManager.save(item);
        } catch (BadCommandException e) {
            msg(sender, "message.armour.get", item.getName(), item.getArmour());
        }
    }

    @SubCommand("type")
    @Attribute("item")
    public void itemType(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String type = args.next();
        if (type != null) {
            item.setType(type);
            msg(sender, "message.type.set", item.getName(), item.getType());
            ItemManager.save(item);
        } else {
            msg(sender, "message.type.get", item.getName(), item.getType());
        }
    }

    @SubCommand("hand")
    @Attribute("item")
    public void itemHand(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String type = args.next();
        if (type != null) {
            item.setHand(type);
            msg(sender, "message.hand.set", item.getName(), item.getType());
            ItemManager.save(item);
        } else {
            msg(sender, "message.hand.get", item.getName(), item.getType());
        }
    }

    @SubCommand("item")
    @Attribute("item")
    public void itemItem(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (args.length() == 2) {
            new Message("")
                    .append(I18n.format("message.item.get", item.getName(), item.getItem().name(), item.getDataValue()), new ItemStack(item.getItem()))
                    .send(sender);
        } else if (args.length() >= 3) {
            String materialName = args.nextString();
            Material material = MaterialUtils.getMaterial(materialName, sender);
            if (material == null || !material.isItem()) {
                msg(sender, "message.error.material", materialName);
                return;
            }
            item.setItem(material, false);
            if (args.length() == 4) {
                int dam;
                try {
                    dam = Integer.parseInt(args.top());
                } catch (Exception e) {
                    String hexColour = "";
                    try {
                        hexColour = args.nextString();
                        dam = Integer.parseInt(hexColour, 16);
                    } catch (NumberFormatException e2) {
                        sender.sendMessage(ChatColor.RED + "Failed to parse " + hexColour);
                        return;
                    }
                }
                ItemMeta meta = item.getLocaleMeta();
                if (meta instanceof LeatherArmorMeta) {
                    ((LeatherArmorMeta) meta).setColor(Color.fromRGB(dam));
                } else {
                    ((Damageable) meta).setDamage(dam);
                }
                item.updateLocaleMeta(meta);
            }
            item.rebuild();
            ItemManager.refreshItem();

            new Message("")
                    .append(I18n.format("message.item.set", item.getName(), item.getItem().name(), item.getDataValue()), new ItemStack(item.getItem()))
                    .send(sender);
            ItemManager.save(item);
        }
    }

    @SubCommand("print")
    @Attribute("item")
    public void itemInfo(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.print(sender);
    }

    @SubCommand("enchantment")
    @Attribute("item:clone,clear")
    public void itemListEnchant(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (args.length() == 2) {
            if (item.enchantMap != null) {
                msg(sender, "message.enchantment.listing", item.getName());
                if (item.enchantMap.size() == 0) {
                    msg(sender, "message.enchantment.empty_ench");
                } else {
                    for (Enchantment ench : item.enchantMap.keySet()) {
                        msg(sender, "message.enchantment.item",
                                ench.getKey().toString(), item.enchantMap.get(ench));
                    }
                }
            } else {
                msg(sender, "message.enchantment.no_ench");
            }
        }
        String command = args.nextString();
        switch (command) {
            case "clone": {
                if (sender instanceof Player) {
                    ItemStack hand = ((Player) sender).getInventory().getItemInMainHand();
                    if (hand == null || hand.getType() == Material.AIR) {
                        msg(sender, "message.enchantment.fail");
                    } else {
                        if (hand.getType() == Material.ENCHANTED_BOOK) {
                            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) hand.getItemMeta();
                            item.enchantMap = meta.getStoredEnchants();
                        } else if (hand.hasItemMeta()) {
                            item.enchantMap = new HashMap<>(hand.getItemMeta().getEnchants());
                        } else {
                            item.enchantMap = Collections.emptyMap();
                        }
                        item.rebuild();
                        ItemManager.refreshItem();
                        ItemManager.save(item);
                        msg(sender, "message.enchantment.success");
                    }
                } else {
                    msg(sender, "message.enchantment.fail");
                }
            }
            break;
            case "clear": {
                item.enchantMap = null;
                item.rebuild();
                ItemManager.refreshItem();
                ItemManager.save(item);
                msg(sender, "message.enchantment.removed");
            }
            break;
            default:
                throw new BadCommandException("message.error.invalid_option", command, "enchantment", "clone,clear");
        }
    }

    @SubCommand("removepower")
    @Attribute("power")
    public void itemRemovePower(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String powerStr = args.nextString();
        int nth = args.top() == null ? 1 : args.nextInt();
        try {
            Class<? extends Power> p = PowerManager.getPower(powerStr);
            if (p == null) {
                msg(sender, "message.power.unknown", powerStr);
                return;
            }
            List<? extends Power> powers = item.getPower(p);
            Optional<? extends Power> op = powers.stream().skip(nth - 1).findFirst();

            if (op.isPresent()) {
                item.removePower(op.get());
                msg(sender, "message.power.removed", powerStr);
                ItemManager.refreshItem();
                ItemManager.save(item);
            } else {
                msg(sender, "message.num_out_of_range", nth, 1, powers.size());
            }
        } catch (UnknownExtensionException e) {
            msg(sender, "message.error.unknown.extension", e.getName());
        }
    }

    @SubCommand("description")
    @Attribute("item:add,set,remove")
    public void itemAddDescription(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String command = args.nextString();
        switch (command) {
            case "add": {
                String line = args.nextString();
                item.addDescription(ChatColor.WHITE + line);
                msg(sender, "message.description.ok");
                ItemManager.refreshItem();
                ItemManager.save(item);
            }
            break;
            case "set": {
                int lineNo = args.nextInt();
                String line = args.nextString();
                if (lineNo < 0 || lineNo >= item.description.size()) {
                    msg(sender, "message.num_out_of_range", lineNo, 0, item.description.size());
                    return;
                }
                item.description.set(lineNo, ChatColor.translateAlternateColorCodes('&', ChatColor.WHITE + line));
                item.rebuild();
                ItemManager.refreshItem();
                msg(sender, "message.description.change");
                ItemManager.save(item);
            }
            break;
            case "remove": {
                int lineNo = args.nextInt();
                if (lineNo < 0 || lineNo >= item.description.size()) {
                    msg(sender, "message.num_out_of_range", lineNo, 0, item.description.size());
                    return;
                }
                item.description.remove(lineNo);
                item.rebuild();
                ItemManager.refreshItem();
                msg(sender, "message.description.remove");
                ItemManager.save(item);
            }
            break;
            default:
                throw new BadCommandException("message.error.invalid_option", command, "description", "add,set,remove");
        }
    }

    @SubCommand("removerecipe")
    @Attribute("item")
    public void itemRemoveRecipe(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.hasRecipe = false;
        item.resetRecipe(true);
        ItemManager.save(item);
        msg(sender, "message.recipe.removed");
    }

    @SubCommand("recipe")
    @Attribute("item")
    public void itemSetRecipe(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        int chance = args.nextInt();
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String title = "RPGItems - " + item.getDisplay();
            if (title.length() > 32) {
                title = title.substring(0, 32);
            }
            Inventory recipeInventory = Bukkit.createInventory(player, 27, title);
            if (item.hasRecipe) {
                ItemStack blank = new ItemStack(Material.GLASS_PANE);
                ItemMeta meta = blank.getItemMeta();
                meta.setDisplayName(I18n.format("message.recipe.1"));
                ArrayList<String> lore = new ArrayList<>();
                lore.add(I18n.format("message.recipe.2"));
                lore.add(I18n.format("message.recipe.3"));
                lore.add(I18n.format("message.recipe.4"));
                lore.add(I18n.format("message.recipe.5"));
                meta.setLore(lore);
                blank.setItemMeta(meta);
                for (int i = 0; i < 27; i++) {
                    recipeInventory.setItem(i, blank);
                }
                for (int x = 0; x < 3; x++) {
                    for (int y = 0; y < 3; y++) {
                        int i = x + y * 9;
                        ItemStack it = item.recipe.get(x + y * 3);
                        if (it != null)
                            recipeInventory.setItem(i, it);
                        else
                            recipeInventory.setItem(i, null);
                    }
                }
            }
            item.setRecipeChance(chance);
            player.openInventory(recipeInventory);
            Events.recipeWindows.put(player.getName(), item.getUID());
        } else {
            msg(sender, "message.error.only.player");
        }
    }

    @SubCommand("drop")
    @Attribute("item")
    public void getItemDropChance(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        EntityType type = args.nextEnum(EntityType.class);
        if (args.length() == 3) {
            msg(sender, "message.drop.get", item.getDisplay(), type.toString().toLowerCase(), item.dropChances.get(type.toString()));
        } else {
            double chance = args.nextDouble();
            chance = Math.min(chance, 100.0);
            String typeS = type.toString();
            if (chance > 0) {
                item.dropChances.put(typeS, chance);
                if (!Events.drops.containsKey(typeS)) {
                    Events.drops.put(typeS, new HashSet<>());
                }
                Set<Integer> set = Events.drops.get(typeS);
                set.add(item.getUID());
            } else {
                item.dropChances.remove(typeS);
                if (Events.drops.containsKey(typeS)) {
                    Set<Integer> set = Events.drops.get(typeS);
                    set.remove(item.getUID());
                }
            }
            ItemManager.save(item);
            msg(sender, "message.drop.set", item.getDisplay(), typeS.toLowerCase(), item.dropChances.get(typeS));
        }
    }

    @SubCommand("get")
    @Attribute("property")
    public void getItemPowerProperty(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String powerStr = args.nextString();
        int nth = args.nextInt();
        String property = args.next();
        Class<? extends Power> cls = getPowerClass(sender, powerStr);
        if (cls == null) {
            msg(sender, "message.power_property.power_notfound");
            return;
        }
        Optional<? extends Power> power = item.getPower(cls).stream().skip(nth - 1).findFirst();
        if (power.isPresent()) {
            Power pow = power.get();
            YamlConfiguration conf = new YamlConfiguration();
            if (property != null) {
                try {
                    Field field = cls.getField(property);
                    Utils.saveProperty(pow, conf, field.getName(), field);
                    msg(sender, "message.power_property.get", nth, pow.getName(), property, conf.getString(field.getName()));
                } catch (Exception e) {
                    msg(sender, "message.power_property.property_notfound", property);
                }
            } else {
                pow.save(conf);
                msg(sender, "message.power_property.all", nth, pow.getName(), conf.saveToString());
            }
        } else {
            msg(sender, "message.power_property.power_notfound");
        }
    }

    private Class<? extends Power> getPowerClass(CommandSender sender, String powerStr) {
        Class<? extends Power> cls;
        try {
            cls = PowerManager.getPower(powerStr);
        } catch (UnknownExtensionException e) {
            msg(sender, "message.error.unknown.extension", e.getName());
            return null;
        }
        if (cls == null) {
            msg(sender, "message.power.unknown", powerStr);
            return null;
        }
        return cls;
    }

    @SubCommand("set")
    @Attribute("property")
    public void setItemPowerProperty(CommandSender sender, Arguments args) throws IllegalAccessException {
        RPGItem item = getItemByName(args.nextString());
        String power = args.nextString();
        int nth = args.nextInt();
        String property = args.nextString();
        String val = args.nextString();
        try {
            Class<? extends Power> p = PowerManager.getPower(power);
            if (p == null) {
                msg(sender, "message.power.unknown", power);
                return;
            }
            Optional<Power> op = item.powers.stream().filter(pwr -> pwr.getClass().equals(p)).skip(nth - 1).findFirst();
            if (op.isPresent()) {
                Power pow = op.get();
                PowerManager.setPowerProperty(sender, pow, property, val);
            } else {
                msg(sender, "message.power_property.power_notfound");
                return;
            }
            item.rebuild();
            ItemManager.refreshItem();
            ItemManager.save(item);
            msg(sender, "message.power_property.change");
        } catch (UnknownExtensionException e) {
            msg(sender, "message.error.unknown.extension", e.getName());

        }
    }

    @SubCommand("cost")
    @Attribute("item:breaking,hitting,hit,toggle")
    public void itemCost(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String type = args.nextString();
        if (args.length() == 3) {
            switch (type) {
                case "breaking":
                    msg(sender, "message.cost.get", item.blockBreakingCost);
                    break;
                case "hitting":
                    msg(sender, "message.cost.get", item.hittingCost);
                    break;
                case "hit":
                    msg(sender, "message.cost.get", item.hitCost);
                    break;
                case "toggle":
                    item.hitCostByDamage = !item.hitCostByDamage;
                    ItemManager.save(item);
                    msg(sender, "message.cost.hit_toggle." + (item.hitCostByDamage ? "enable" : "disable"));
                    break;
                default:
                    throw new BadCommandException("message.error.invalid_option", type, "cost", "breaking,hitting,hit,toggle");
            }
        } else {
            int newValue = args.nextInt();
            switch (type) {
                case "breaking":
                    item.blockBreakingCost = newValue;
                    break;
                case "hitting":
                    item.hittingCost = newValue;
                    break;
                case "hit":
                    item.hitCost = newValue;
                    break;
                default:
                    throw new BadCommandException("message.error.invalid_option", type, "cost", "breaking,hitting,hit");
            }

            ItemManager.save(item);
            msg(sender, "message.cost.change");
        }
    }

    @SubCommand("durability")
    @Attribute("item:infinite,togglebar,default,bound")
    public void itemDurability(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (args.length() == 2) {
            msg(sender, "message.durability.info", item.getMaxDurability(), item.defaultDurability, item.durabilityLowerBound, item.durabilityUpperBound);
            return;
        }
        String arg = args.nextString();
        try {
            int durability = Integer.parseInt(arg);
            item.setMaxDurability(durability);
            ItemManager.refreshItem();
            ItemManager.save(item);
            msg(sender, "message.durability.change");
        } catch (Exception e) {
            switch (arg) {
                case "infinite": {
                    item.setMaxDurability(-1);
                    ItemManager.refreshItem();
                    ItemManager.save(item);
                    msg(sender, "message.durability.change");
                }
                break;
                case "togglebar": {
                    item.toggleBar();
                    ItemManager.refreshItem();
                    ItemManager.save(item);
                    msg(sender, "message.durability.toggle");
                }
                break;
                case "default": {
                    int durability = args.nextInt();
                    item.setDefaultDurability(durability);
                    ItemManager.refreshItem();
                    ItemManager.save(item);
                    msg(sender, "message.durability.change");
                }
                break;
                case "bound": {
                    int min = args.nextInt();
                    int max = args.nextInt();
                    item.setDurabilityBound(min, max);
                    ItemManager.refreshItem();
                    ItemManager.save(item);
                    msg(sender, "message.durability.change");
                }
                break;
                default:
                    throw new BadCommandException("message.error.invalid_option", arg, "durability", "value,infinite,togglebar,default,bound");
            }
        }
    }

    @SubCommand("permission")
    @Attribute("item")
    public void setPermission(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String permission = args.next();
        boolean enabled = args.nextBoolean();
        item.setPermission(permission);
        item.setHaspermission(enabled);
        ItemManager.save(item);
        msg(sender, "message.permission.success");
    }

    @SubCommand("togglepowerlore")
    @Attribute("item")
    public void togglePowerLore(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.showPowerLore = !item.showPowerLore;
        item.rebuild();
        ItemManager.refreshItem();
        ItemManager.save(item);
        msg(sender, "message.toggleLore." + (item.showPowerLore ? "show" : "hide"));
    }

    @SubCommand("togglearmorlore")
    @Attribute("item")
    public void toggleArmorLore(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.showArmourLore = !item.showArmourLore;
        item.rebuild();
        ItemManager.refreshItem();
        ItemManager.save(item);
        msg(sender, "message.toggleLore." + (item.showArmourLore ? "show" : "hide"));
    }

    @SubCommand("additemflag")
    @Attribute("item")
    public void addItemFlag(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        ItemFlag flag = args.nextEnum(ItemFlag.class);
        item.itemFlags.add(ItemFlag.valueOf(flag.name()));
        item.rebuild();
        ItemManager.refreshItem();
        ItemManager.save(item);
        msg(sender, "message.itemflag.add", flag.name());
    }

    @SubCommand("removeitemflag")
    @Attribute("item")
    public void removeItemFlag(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        ItemFlag flag = args.nextEnum(ItemFlag.class);
        ItemFlag itemFlag = ItemFlag.valueOf(flag.name());
        if (item.itemFlags.contains(itemFlag)) {
            item.itemFlags.remove(itemFlag);
            item.rebuild();
            ItemManager.refreshItem();
            ItemManager.save(item);
            msg(sender, "message.itemflag.remove", flag.name());
        } else {
            msg(sender, "message.itemflag.notfound", flag.name());
        }
    }

    @SubCommand("customitemmodel")
    @Attribute("item")
    public void toggleCustomItemModel(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.customItemModel = !item.customItemModel;
        item.rebuild();
        ItemManager.refreshItem();
        ItemManager.save(item);
        msg(sender, "message.customitemmodel." + (item.customItemModel ? "enable" : "disable"));
    }

    @SubCommand("numericBar")
    @Attribute("item")
    public void toggleNumericBar(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        item.numericBar = !item.numericBar;
        item.rebuild();
        ItemManager.refreshItem();
        ItemManager.save(item);
        msg(sender, "message.numericbar." + (item.numericBar ? "enable" : "disable"));
    }

    @SubCommand("version")
    @Attribute("command")
    public void printVersion(CommandSender sender, Arguments args) {
        msg(sender, "message.version", RPGItems.plugin.getDescription().getVersion());
    }

    @SubCommand("damagemode")
    @Attribute("item")
    public void toggleItemDamageMode(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        if (args.top() != null) {
            item.damageMode = args.nextEnum(RPGItem.DamageMode.class);
            item.rebuild();
            ItemManager.refreshItem();
            ItemManager.save(item);
        }
        msg(sender, "message.damagemode." + item.damageMode.name(), item.getName());
    }

    @SubCommand("power")
    @Attribute("power")
    public void itemAddPower(CommandSender sender, Arguments args) throws IllegalAccessException {
        String itemStr = args.next();
        String powerStr = args.next();
        if (itemStr == null || (itemStr.equals("help") && ItemManager.getItemByName(itemStr) == null)) {
            msg(sender, "manual.power.description");
            msg(sender, "manual.power.usage");
            for (NamespacedKey power : PowerManager.getPowers().keySet()) {
                msg(sender, "message.power.key", power.toString());
                msg(sender, "message.power.description", PowerManager.getDescription(power, null));
            }
            return;
        }
        if (ItemManager.getItemByName(itemStr) != null && (powerStr == null || powerStr.equals("list"))) {
            RPGItem item = getItemByName(itemStr);
            for (Power power : item.powers) {
                msg(sender, "message.item.power", power.getLocalizedName(plugin.cfg.language), power.getNamespacedKey().toString(), power.displayText() == null ? I18n.format("message.power.no_display") : power.displayText(), power.getTriggers().stream().map(Trigger::name).collect(Collectors.joining(",")));
            }
            return;
        }
        RPGItem item = getItemByName(itemStr);
        Class<? extends Power> cls = getPowerClass(sender, powerStr);
        if (cls == null) return;
        Power power;
        try {
            power = cls.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
            msg(sender, "internal.error.command_exception");
            return;
        }
        SortedMap<PowerProperty, Field> argMap = PowerManager.getProperties(cls);
        Set<Field> settled = new HashSet<>();
        Optional<PowerProperty> req = argMap.keySet()
                                            .stream()
                                            .filter(PowerProperty::required)
                                            .reduce((first, second) -> second); //findLast

        Set<Field> required = req.map(r -> argMap.entrySet()
                                                 .stream()
                                                 .filter(entry -> entry.getKey().order() <= r.order())
                                                 .map(Map.Entry::getValue)
                                                 .collect(Collectors.toSet())).orElse(new HashSet<>());

        for (Field field : argMap.values()) {
            String name = field.getName();
            String value = args.argString(name, null);
            if (value != null) {
                PowerManager.setPowerProperty(sender, power, field, value);
                required.remove(field);
                settled.add(field);
            }
        }
        for (Field field : argMap.entrySet()
                                 .stream()
                                 .filter(p -> p.getKey().order() != Integer.MAX_VALUE)
                                 .map(Map.Entry::getValue)
                                 .collect(Collectors.toList())) {
            if (settled.contains(field)) continue;
            String value = args.next();
            if (value == null) {
                if (!required.isEmpty()) {
                    throw new BadCommandException("message.power.required",
                            required.stream().map(Field::getName).collect(Collectors.joining(", "))
                    );
                } else {
                    break;
                }
            }
            PowerManager.setPowerProperty(sender, power, field, value);
            required.remove(field);
            settled.add(field);
        }
        power.setItem(item);
        item.addPower(power);
        ItemManager.save(item);
        msg(sender, "message.power.ok");
    }

    private RPGItem getItemByName(String name) {
        RPGItem item = ItemManager.getItemByName(name);
        if (item != null) {
            return item;
        } else {
            throw new BadCommandException("message.error.item", name);
        }
    }

    @SubCommand("clone")
    @Attribute("item")
    public void cloneItem(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String name = args.nextString();
        RPGItem i = ItemManager.cloneItem(item, name);
        ItemManager.save(item);
        if (i != null) {
            msg(sender, "message.cloneitem.success", item.getName(), i.getName());
        } else {
            msg(sender, "message.cloneitem.fail", item.getName(), name);
        }
    }

    @SubCommand("import")
    @Attribute("command:GIST")
    public void download(CommandSender sender, Arguments args) {
        NetworkUtils.Location location = args.nextEnum(NetworkUtils.Location.class);
        String id = args.nextString();
        switch (location) {
            case GIST:
                downloadGist(sender, args, id);
                break;
        }
    }

    @SubCommand("export")
    @Attribute("items:GIST")
    public void publish(CommandSender sender, Arguments args) {
        String itemsStr = args.nextString();
        NetworkUtils.Location location = args.top() == null ? GIST : args.nextEnum(NetworkUtils.Location.class);
        Set<String> items = Stream.of(itemsStr.split(",")).collect(Collectors.toSet());

        switch (location) {
            case GIST:
                publishGist(sender, args, items);
                break;
        }
    }

    @SubCommand("author")
    @Attribute("item")
    public void setAuthor(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String author = args.next();
        if (author != null) {
            BaseComponent authorComponent = new TextComponent(author);
            if (author.startsWith("@")) {
                String authorName = author.substring(1);
                Optional<OfflinePlayer> maybeAuthor = Arrays.stream(Bukkit.getOfflinePlayers()).filter(p -> p.getName().startsWith(authorName)).sorted(Comparator.comparing(OfflinePlayer::getLastPlayed).reversed()).findFirst();
                if (maybeAuthor.isPresent()) {
                    OfflinePlayer authorPlayer = maybeAuthor.get();
                    author = authorPlayer.getUniqueId().toString();
                    authorComponent = new TextComponent(authorPlayer.getName());
                    authorComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                            new ComponentBuilder(Message.getPlayerJson(authorPlayer)).create()
                    ));
                }
            }
            item.setAuthor(author);
            msg(sender, "message.item.author.set", Collections.singletonMap("{author}", authorComponent), item.getName());
            ItemManager.save(item);
        } else {
            String authorText = item.getAuthor();
            if (Strings.isNullOrEmpty(authorText)) {
                msg(sender, "message.item.author.na", item.getName());
            }
            BaseComponent authorComponent = new TextComponent(authorText);
            try {
                UUID uuid = UUID.fromString(authorText);
                OfflinePlayer authorPlayer = Bukkit.getOfflinePlayer(uuid);
                String authorName = authorPlayer.getName();
                if (authorName == null) {
                    authorName = uuid.toString();
                }
                authorComponent = new TextComponent(authorName);
                authorComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                        new ComponentBuilder(Message.getPlayerJson(authorPlayer)).create()
                ));
            } catch (IllegalArgumentException ignored) {
            }
            msg(sender, "message.item.author.get", Collections.singletonMap("{author}", authorComponent), item.getName());
        }
    }

    @SubCommand("note")
    @Attribute("item")
    public void setNote(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String note = args.next();
        if (note != null) {
            item.setNote(note);
            msg(sender, "message.item.note.set", item.getName(), note);
            ItemManager.save(item);
        } else {
            msg(sender, "message.item.note.get", item.getName(), item.getNote());
        }
    }

    @SubCommand("license")
    @Attribute("item")
    public void setLicense(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        String license = args.next();
        if (license != null) {
            item.setLicense(license);
            msg(sender, "message.item.license.set", item.getName(), license);
            ItemManager.save(item);
        } else {
            msg(sender, "message.item.license.get", item.getName(), item.getLicense());
        }
    }

    @SubCommand("dump")
    @Attribute("item")
    public void dumpItem(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        item.save(yamlConfiguration);
        String s = yamlConfiguration.saveToString();
        msg(sender, "message.item.dump", item.getName(), s.replace(ChatColor.COLOR_CHAR + "", "\\u00A7"));
    }

    @SubCommand("reorder")
    @Attribute("item")
    public void reorder(CommandSender sender, Arguments args) {
        RPGItem item = getItemByName(args.nextString());
        int origin = args.nextInt() - 1;
        int next = args.nextInt() - 1;
        Power remove = item.powers.remove(origin);
        item.powers.add(next, remove);
        msg(sender, "message.item.reorder", item.getName(), remove.getName());
    }

    private void publishGist(CommandSender sender, Arguments args, Set<String> itemNames) {
        List<Pair<String, RPGItem>> items = itemNames.stream().map(i -> Pair.of(i, ItemManager.getItemByName(i))).collect(Collectors.toList());
        Optional<Pair<String, RPGItem>> unknown = items.stream().filter(p -> p.getValue() == null).findFirst();
        if (unknown.isPresent()) {
            throw new BadCommandException("message.error.item", unknown.get().getKey());
        }
        String token = args.argString("token", plugin.cfg.githubToken);
        if (Strings.isNullOrEmpty(token)) {
            throw new BadCommandException("message.export.gist.token");
        }
        boolean isPublish = Boolean.parseBoolean(args.argString("publish", String.valueOf(plugin.cfg.publishGist)));
        String description = args.argString("description",
                "RPGItems exported item: " + String.join(",", itemNames)
        );
        Map<String, Map<String, String>> result = new HashMap<>(items.size());
        items.forEach(
                pair -> {
                    RPGItem item = pair.getValue();
                    String name = pair.getKey();
                    YamlConfiguration conf = new YamlConfiguration();
                    item.save(conf);
                    conf.set("id", null);
                    String itemConf = conf.saveToString();
                    String filename = ItemManager.getItemFilename(name) + ".yml";
                    Map<String, String> content = new HashMap<>();
                    content.put("content", itemConf);
                    result.put(filename, content);
                }
        );
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String id = NetworkUtils.publishGist(result, token, description, isPublish);
                Bukkit.getScheduler().runTask(plugin, () -> msg(sender, "message.export.gist.ed", id));
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> msg(sender, "message.export.gist.failed"));
            } catch (TimeoutException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> msg(sender, "message.export.gist.timeout"));
            }
        });
    }

    private void downloadGist(CommandSender sender, Arguments args, String id) {
        new Message(I18n.format("message.import.gist.ing")).send(sender);
        String token = args.argString("token", plugin.cfg.githubToken);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> gist;
            try {
                gist = NetworkUtils.downloadGist(id, token);
                Bukkit.getScheduler().runTask(plugin, () -> loadItems(sender, gist, args));
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> new Message(I18n.format("message.import.gist.failed")).send(sender));
            } catch (TimeoutException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> new Message(I18n.format("message.import.gist.timeout")).send(sender));
            }
        });
    }

    private void loadItems(CommandSender sender, Map<String, String> gist, Arguments args) {
        List<RPGItem> items = new ArrayList<>(gist.size());
        for (Map.Entry<String, String> entry : gist.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            YamlConfiguration itemStorage = new YamlConfiguration();
            try {
                itemStorage.set("id", null);
                itemStorage.loadFromString(v);
                String origin = itemStorage.getString("name");
                int uid = itemStorage.getInt("uid");

                if (uid >= 0 || origin == null) {
                    throw new InvalidConfigurationException();
                }

                String name = args.argString(origin, origin);

                if (ItemManager.itemById.containsKey(uid)) {
                    RPGItem current = ItemManager.getItemById(uid);
                    msg(sender, "message.import.conflict_uid", origin, current.getName(), uid);
                    return;
                }
                if (ItemManager.itemByName.containsKey(name)) {
                    msg(sender, "message.import.conflict_name", name);
                    return;
                }

                RPGItem item = new RPGItem(itemStorage, name, uid);
                items.add(item);
            } catch (InvalidConfigurationException e) {
                msg(sender, "message.import.invalid_conf", k);
                e.printStackTrace();
                return;
            } catch (UnknownPowerException e) {
                msg(sender, "message.power.unknown", e.getKey().toString());
                return;
            } catch (UnknownExtensionException e) {
                msg(sender, "message.error.unknown.extension", e.getName());
                return;
            }
        }
        for (RPGItem item : items) {
            ItemManager.addItem(item);
            msg(sender, "message.import.success", item.getName(), item.getUID());
        }
        ItemManager.save();
    }

    public static class CommandException extends BadCommandException {
        @LangKey
        private final String msg_internal;

        public CommandException(@LangKey String msg_internal, Object... args) {
            super(msg_internal, args);
            this.msg_internal = msg_internal;
        }

        public CommandException(@LangKey(varArgsPosition = 1) String msg_internal, Throwable cause, Object... args) {
            super(msg_internal, cause, args);
            this.msg_internal = msg_internal;
        }

        @Override
        public String toString() {
            StringBuilder keyBuilder = new StringBuilder("CommandException<" + msg_internal + ">");
            for (Object obj : objs) {
                keyBuilder.append("#<").append(obj.toString()).append(">");
            }
            return keyBuilder.toString();
        }

        @Override
        public String getLocalizedMessage() {
            return I18n.format(msg_internal, objs);
        }
    }
}
