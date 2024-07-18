package nl.rutgerkok.betterenderchest.command;

import nl.rutgerkok.betterenderchest.BetterEnderChest;
import nl.rutgerkok.betterenderchest.BetterEnderInventoryHolder;
import nl.rutgerkok.betterenderchest.BetterEnderUtils;
import nl.rutgerkok.betterenderchest.WorldGroup;
import nl.rutgerkok.betterenderchest.io.Consumer;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import nl.rutgerkok.betterenderchest.Translations;

public class SwapInvCommand extends BaseCommand {

    public SwapInvCommand(BetterEnderChest plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(final CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }

        final String inventoryName1 = getInventoryName(args[0]);
        final WorldGroup group1 = getGroup(args[0], sender);
        final String inventoryName2 = getInventoryName(args[1]);
        final WorldGroup group2 = getGroup(args[1], sender);

        // Check groups
        if (group1 == null) {
            //sender.sendMessager(ChatColor.RED + Translations.SWAP_INV_GROUP_NOT_FOUND.toString(args[0]));
            return true;
        }
        if (group2 == null) {
            //sender.sendMessager(ChatColor.RED + Translations.SWAP_INV_GROUP_NOT_FOUND.toString(args[1]));
            return true;
        }

        // Get both inventories
        getInventory(sender, inventoryName1, group1, new Consumer<Inventory>() {
            @Override
            public void consume(final Inventory inventory1) {
                getInventory(sender, inventoryName2, group2, new Consumer<Inventory>() {
                    @Override
                    public void consume(Inventory inventory2) {
                        swap(sender, inventory1, inventory2);
                    }
                });
            }
        });
        return true;
    }

    @Override
    public String getHelpText() {
        return Translations.SWAP_INV_HELP_TEXT.toString();
    }

    @Override
    public String getName() {
        return Translations.SWAP_INV_COMMAND.toString();
    }

    @Override
    public String getUsage() {
        return Translations.SWAP_INV_USAGE.toString();
    }

    private void swap(CommandSender sender, Inventory inventory1, Inventory inventory2) {
        BetterEnderInventoryHolder holder1 = BetterEnderInventoryHolder.of(inventory1);
        BetterEnderInventoryHolder holder2 = BetterEnderInventoryHolder.of(inventory2);

        // Get rid of the viewers
        BetterEnderUtils.closeInventory(inventory1, ChatColor.YELLOW + Translations.SWAP_INV_CLOSE_MESSAGE.toString());
        BetterEnderUtils.closeInventory(inventory2, ChatColor.YELLOW + Translations.SWAP_INV_CLOSE_MESSAGE.toString());

        // Create new inventory 1 with size and contents of inventory 2
        Inventory newInv1 = plugin.getEmptyInventoryProvider().loadEmptyInventory(
                holder1.getChestOwner(), holder1.getWorldGroup(), holder2.getChestRestrictions());
        BetterEnderUtils.copyContents(inventory2, newInv1, null);

        // Create new inventory 2 with size and contents of inventory 1
        Inventory newInv2 = plugin.getEmptyInventoryProvider().loadEmptyInventory(
                holder2.getChestOwner(), holder2.getWorldGroup(), holder1.getChestRestrictions());
        BetterEnderUtils.copyContents(inventory1, newInv2, null);

        // Let new inventories replace old ones
        plugin.getChestCache().setInventory(newInv1);
        plugin.getChestCache().setInventory(newInv2);

        // Show a message
        //sender.sendMessager(ChatColor.GREEN + Translations.SWAP_INV_SUCCESS_MESSAGE.toString());
    }
}

