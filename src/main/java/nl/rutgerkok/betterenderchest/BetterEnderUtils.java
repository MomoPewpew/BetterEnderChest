package nl.rutgerkok.betterenderchest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;

import com.google.common.collect.ImmutableList;

import nl.rutgerkok.betterenderchest.chestowner.ChestOwner;

/**
 * Various utilities used in the BetterEnderChest plugin.
 * 
 */
public class BetterEnderUtils {
    // Constants for the metadata key names
    private static final String LAST_CHEST_X = "BECLastChestX";
    private static final String LAST_CHEST_Y = "BECLastChestY";
    private static final String LAST_CHEST_Z = "BECLastChestZ";

    /**
     * Closes the inventory for all the viewers. Always call this before
     * deleting it!
     * 
     * @param inventory
     *            The inventory to close.
     * @param message
     *            Shown to the victims
     */
    public static void closeInventory(Inventory inventory, String message) {
        List<HumanEntity> viewers = ImmutableList.copyOf(inventory.getViewers());
        for (HumanEntity player : viewers) {
            player.closeInventory(); // Removes them from inventory.getViewers()
            if (player instanceof Player) {
                //((Player) player).sendMessage(message);
            }
        }
    }

    /**
     * Copies all items to the new inventory. The new inventory must be empty,
     * otherwise items in the new inventory will be lost.
     * 
     * @param oldInventory
     *            The old inventory to copy the items from.
     * @param newInventory
     *            The new inventory to copy the items to.
     * @param dropLocation
     *            The location to drop all the items that don't fit. Set it to
     *            null to destroy the items that don't fit.
     */
    public static void copyContents(Inventory oldInventory, Inventory newInventory, Location dropLocation) {
        int sizeNew = newInventory.getSize();
        List<ItemStack> overflowing = new ArrayList<>();
        if (oldInventory.getHolder() instanceof BetterEnderInventoryHolder) {
            overflowing.addAll(((BetterEnderInventoryHolder) oldInventory.getHolder()).handleOverflowingItems());
        }

        ListIterator<ItemStack> it = oldInventory.iterator();
        while (it.hasNext()) {
            int slot = it.nextIndex();
            ItemStack stack = it.next();
            if (stack != null) {
                if (slot < sizeNew) {
                    // It fits in the chest, add it
                    newInventory.setItem(slot, stack);
                } else {
                    // Else, we will look for free slots afterwards
                    overflowing.add(stack);
                }
            }
        }

        // Rearrange or drop everything else
        for (ItemStack stack : overflowing) {
            // It doesn't fit, try to add it to another slot
            HashMap<Integer, ItemStack> excess = newInventory.addItem(stack);
            // Drop everything that doesn't fit
            for (ItemStack excessStack : excess.values()) {
                if (dropLocation != null) {
                    dropLocation.getWorld().dropItem(dropLocation, excessStack);
                }
            }
        }
    }

    /**
     * Drops all items on the ground that are in disabled slots. Just a
     * safeguard against glitches and hacked clients, as normally the
     * InventoryClickEvent should prevent items in disabled slots. This doesn't
     * do anything if the player is not the owner of the chest.
     * 
     * @param inventory
     *            The inventory in question.
     * @param player
     *            The player that is opening the inventory.
     * @param plugin
     *            The plugin, for chest size calculations.
     */
    public static void dropItemsInDisabledSlots(Inventory inventory, Player player, BetterEnderChest plugin) {
        BetterEnderInventoryHolder holder = (BetterEnderInventoryHolder) inventory.getHolder();
        ChestOwner chestOwner = holder.getChestOwner();
        int disabledSlots = holder.getChestRestrictions().getDisabledSlots();

        if (disabledSlots > 0) {
            Location playerLocation = player.getLocation();
            int droppedCount = 0;
            for (int i = 1; i <= disabledSlots; i++) {
                ItemStack stackInDisabledSlot = inventory.getItem(inventory.getSize() - i);
                if (stackInDisabledSlot != null && !stackInDisabledSlot.getType().equals(Material.AIR)) {
                    inventory.setItem(inventory.getSize() - i, new ItemStack(Material.AIR));
                    player.getWorld().dropItemNaturally(playerLocation, stackInDisabledSlot);
                    droppedCount++;
                }
            }
            if (droppedCount > 0) {
                //player.sendMessage(ChatColor.YELLOW + Translations.OVERFLOWING_CHEST_CLOSE.toString());
                plugin.log("There were items in disabled slots in the Ender Chest of " + chestOwner.getDisplayName() + ". Demoted? Glitch? Hacking? " + droppedCount + " stacks are ejected.");
            }
        }
    }

    public static Inventory getCorrectlyResizedInventory(Player player, Inventory inventory, BetterEnderChest plugin) {
        Inventory resizedInventory = getResizedEmptyInventory(player, inventory, plugin);
        if (resizedInventory != null) {
            // It has resized

            // Kick all players from old inventory
            closeInventory(inventory, ChatColor.YELLOW + "The owner got a different rank, and the inventory had to be resized.");

            // Move all items (and drop the excess)
            copyContents(inventory, resizedInventory, player.getLocation());

            // Goodbye to old inventory!
            plugin.getChestCache().setInventory(resizedInventory);
            inventory = resizedInventory;
        }
        return inventory;
    }

    /**
     * Returns the last location where the player opened an Ender Chest, or null
     * if it wasn't found.
     * 
     * @param player
     *            The player.
     * @return The last location where the player opened an Ender Chest.
     */
    public static Location getLastEnderChestOpeningLocation(Player player) {
        MetadataValue x = getMetadataValue(LAST_CHEST_X, player);
        MetadataValue y = getMetadataValue(LAST_CHEST_Y, player);
        MetadataValue z = getMetadataValue(LAST_CHEST_Z, player);
        if (x != null && y != null && z != null) {
            return new Location(player.getWorld(), x.asInt(), y.asInt(), z.asInt());
        }
        return null;
    }

    private static MetadataValue getMetadataValue(String key, Metadatable lookup) {
        if (lookup == null) {
            return null;
        }
        List<MetadataValue> values = lookup.getMetadata(key);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }

    /**
     * Returns a resized inventory. Returns null if nothing had to be resized.
     * 
     * @param player
     *            Player currently opening the inventory.
     * @param inventory
     *            Inventory. BetterEnderInventoryHolder must be the holder.
     * @param plugin
     *            The plugin instance.
     * @return The resized inventory.
     */
    private static Inventory getResizedEmptyInventory(Player player, Inventory inventory, BetterEnderChest plugin) {
        BetterEnderInventoryHolder inventoryHolder = BetterEnderInventoryHolder.of(inventory);
        ChestOwner chestOwner = inventoryHolder.getChestOwner();
        WorldGroup worldGroup = inventoryHolder.getWorldGroup();
        ChestRestrictions chestRestrictions = inventoryHolder.getChestRestrictions();
        BetterEnderChestSizes chestSizes = plugin.getChestSizes();
        EmptyInventoryProvider emptyChests = plugin.getEmptyInventoryProvider();

        ChestRestrictions desiredRestrictions = null;
        if (chestOwner.isPublicChest()) {
            // It's the public chest
            desiredRestrictions = chestSizes.getPublicChestRestrictions();

        } else if (chestOwner.isDefaultChest()) {
            // It's the default chest
            desiredRestrictions = chestSizes.getDefaultChestRestrictions();
        } else {
            // It's a private chest
            if (chestOwner.isPlayer(player)) {
                // Player is the owner
                desiredRestrictions = chestSizes.getChestRestrictions(player);
            }
        }

        if (desiredRestrictions == null || desiredRestrictions.equals(chestRestrictions)) {
            if (!inventoryHolder.hasOverflowingItems()) {
                // Don't resize
                return null;
            }
        }

        // Resize
        return emptyChests.loadEmptyInventory(chestOwner, worldGroup, desiredRestrictions);
    }

    /**
     * Gets whether the given inventory is empty.
     * 
     * @param inventory
     *            The inventory.
     * @return True if the inventory is empty, otherwise false.
     */
    public static boolean isInventoryEmpty(Inventory inventory) {
        boolean empty = true;
        ListIterator<ItemStack> iterator = inventory.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack != null && stack.getType() != Material.AIR) {
                // Found an item
                empty = false;
            }
        }
        return empty;
    }

    /**
     * Sets the location of the player where he/she last opened an Ender Chest.
     * Use null as the location to clear the stored chest location.
     * 
     * @param player
     *            The player that opened the Ender Chest.
     * @param location
     *            Where the Ender Chest was.
     * @param plugin
     *            The BetterEnderChest interface.
     */
    public static void setLastEnderChestOpeningLocation(Player player, Location location, BetterEnderChest plugin) {
        player.removeMetadata(LAST_CHEST_X, plugin.getPlugin());
        player.removeMetadata(LAST_CHEST_Y, plugin.getPlugin());
        player.removeMetadata(LAST_CHEST_Z, plugin.getPlugin());
        if (location != null) {
            player.setMetadata(LAST_CHEST_X, new FixedMetadataValue(plugin.getPlugin(), location.getBlockX()));
            player.setMetadata(LAST_CHEST_Y, new FixedMetadataValue(plugin.getPlugin(), location.getBlockY()));
            player.setMetadata(LAST_CHEST_Z, new FixedMetadataValue(plugin.getPlugin(), location.getBlockZ()));
        }
    }

    /**
     * Gets the hashcode of the inventory. This hashcode is based on the hashcode
     * of the items in the inventory. We use this to detect changes in the
     * inventory.
     * 
     * This method also works for shulker boxes (normally the item count of items in
     * the shulker box is not taken into account of the hashcode).
     * 
     * @param contents
     *                 The contents of the inventory.
     * @return The hashcode of the inventory.
     */
    public static int inventoryHashCode(ItemStack[] contents) {
        int result = 1;

        for (ItemStack item : contents) {
            int itemHashCode = 0;
            if (item != null) {
                // For shulker boxes, normally the item count is not taken into account of the
                // hashcode
                // We work around this by taking the normal hashcode of the inventory contents
                if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof BlockStateMeta) {
                        BlockStateMeta blockStateMeta = (BlockStateMeta) meta;
                        BlockState blockState = blockStateMeta.getBlockState();
                        if (blockState instanceof ShulkerBox) {
                            ShulkerBox shulkerBox = (ShulkerBox) blockState;
                            itemHashCode = inventoryHashCode(shulkerBox.getInventory().getContents());
                        }
                    }
                }

                // In case we didn't find a hashcode yet, we just use the normal hashcode
                if (itemHashCode == 0) {
                    itemHashCode = item.hashCode();
                }
            }

            result = 31 * result + itemHashCode;
        }

        return result;
    }

}
