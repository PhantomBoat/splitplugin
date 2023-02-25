package com.splitter;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.game.ItemManager;

import java.util.Arrays;
import java.util.List;

import static java.lang.Integer.parseInt;


@Slf4j
@PluginDescriptor(
        name = "Splitter"
)
public class SplitterPlugin extends Plugin {
    private static final String SPLIT_COMMAND_STRING = "split";
    @Inject
    private Client client;

    @Inject
    private SplitterConfig config;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private RuneLiteConfig runeLiteConfig;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Subscribe
    public void onCommandExecuted(CommandExecuted commandExecuted) {
        if (commandExecuted.getCommand().equals(SPLIT_COMMAND_STRING)) {
            computeSplit(commandExecuted.getArguments());
        }
    }

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
    }

    /**
     * Calculates the appropriate split based on either gold (coins or platinum tokens) in inventory or items based on args.
     * Then calls subroutines which prints the appropriate message in chat. The item lookup uses the same logic (copied) from
     * !price command.
     *
     * @param args args from the ::command call. If length == 0, splits gold in inventory.
     *             If length == 1, checks if args[0] is an integer, if so changes the number to split by to this integer.
     *             If args[0] is not an integer, presumes it's a search term for item to split by default split size.
     *             If length > 1, checks if the last element in args is an integer, if so sets that as split size. It then
     *             uses the rest of the elements as search term for an item. If last element isn't an integer all elements
     *             in args is used as the search term.
     */
    void computeSplit(String[] args) {
        int CoinID = 995;
        int PlatTokenID = 13204;
        ItemPrice item = null;
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);

        // container == null if since logged in there has been no items in the inventory.
        if (container == null && args.length == 0) {
            inventoryHasNotLoaded();
            return;
        }

        int splitSize = config.splitSize();
        boolean hasPlatinum = false;
        int valueToSplit = -1;

        // Checks if the inventory contains gold or platinum tokens and then updates hasPlatinum and valueToSplit
        if (container != null) {
            hasPlatinum = container.contains(PlatTokenID);
            valueToSplit = (hasPlatinum) ? container.count(PlatTokenID) : container.count(CoinID);
        }

        // If command was "split::" and nothing more
        if (args.length == 0) {
            int splitValue = valueToSplit / splitSize;
            if (valueToSplit == 0) {
                noCashMessage();
                return;
            }

            printSplit(hasPlatinum, splitValue, valueToSplit, splitSize);
            return;
            // If there was exactly 1 argument, check if argument was a split size or an item to look up the price for
        } else if (args.length == 1) {
            try {
                // If parseInt throws an exception it is assumed that the argument was an item to look up
                splitSize = parseInt(args[0]);

                // If argument was a split size but there was no money in the inventory.
                if (valueToSplit <= 0) {
                    noCashMessage();
                    return;
                }
            } catch (NumberFormatException e) {
                item = itemPriceLookup(args[0]);
                if (item == null) {
                    log.debug("Invalid split input");
                    printUnableToFindItem(args[0]);
                    return;
                }

                valueToSplit = runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
            }
        } else {
            String[] argElements;
            try {
                // Last argument is assumed to the split size
                splitSize = parseInt(args[args.length - 1]);
                argElements = Arrays.copyOf(args, args.length - 1);
            } catch (NumberFormatException e) {
                argElements = args;
            }
            // If the last argument was a split size but less than or equal to 0
            if (splitSize <= 0) {
                log.debug("Invalid split size");
                return;
            }

            item = itemPriceLookup(String.join(" ", argElements));
            if (item != null) {
                valueToSplit = runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
            } else {
                printUnableToFindItem(String.join(" ", argElements));
                return;
            }
        }

        int splitValue = valueToSplit / splitSize;

        if (item != null) {
            printSplitItem(item.getName(), splitValue, valueToSplit, splitSize);
        } else {
            printSplit(hasPlatinum, splitValue, valueToSplit, splitSize);
        }
    }

    /**
     * Informs the user there is no gold (coins or platinum tokens) in the inventory. Logs in debug that this is because
     * inventory wasn't loaded. This function doesn't check if this is actually the case.
     */
    private void inventoryHasNotLoaded() {
        log.debug("Inventory is empty and hasn't loaded.");
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .value("No Coin(s) or Platinum token(s) in the inventory.")
                .build());
    }

    /**
     * Informs the user there is no gold (coins or platinum tokens) in the inventory. Logs in debug that this is because
     * inventory didn't contain any gold. This function doesn't check if this is actually the case.
     */
    private void noCashMessage() {
        log.debug("No money in the inventory.");
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .value("No Coin(s) or Platinum token(s) in the inventory.")
                .build());
    }

    /**
     * Prints split in chat with formatted text. Intended for when splitting coins or platinum tokens, not for splitting items.
     *
     * @param hasPlatinum true if splitting platinum tokens, false if splitting coins
     * @param splitValue the value of each portion of the split
     * @param valueToSplit the total value to split
     * @param splitSize the number of portions
     */
    private void printSplit(boolean hasPlatinum, int splitValue, int valueToSplit, int splitSize) {
        String coinSplit = "Coin";
        String platinumSplit = "Platinum token";

        String splitType = (hasPlatinum) ? platinumSplit : coinSplit;

        final String response = new ChatMessageBuilder()
                .append("Splitting " + splitType + "s ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(splitValue))
                .append(ChatColorType.NORMAL)
                .append(", ( ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(valueToSplit))
                .append(ChatColorType.NORMAL)
                .append(" / ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(splitSize))
                .append(ChatColorType.NORMAL)
                .append(" )")
                .build();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(response)
                .build());
        log.debug("Split:" + splitValue);
    }

    /**
     * Prints split in chat with formatted text. Intended for when splitting items, not coins or platinum tokens.
     *
     * @param itemName name of the item being split
     * @param splitValue the value of each portion of the split
     * @param valueToSplit the total value to split
     * @param splitSize the number of portions
     */
    private void printSplitItem(String itemName, int splitValue, int valueToSplit, int splitSize) {
        final String response = new ChatMessageBuilder()
                .append("Splitting ")
                .append(ChatColorType.HIGHLIGHT)
                .append(itemName)
                .append(ChatColorType.NORMAL)
                .append(": ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(splitValue))
                .append(ChatColorType.NORMAL)
                .append(", ( ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(valueToSplit))
                .append(ChatColorType.NORMAL)
                .append(" / ")
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(splitSize))
                .append(ChatColorType.NORMAL)
                .append(" )")
                .build();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(response)
                .build());
        log.debug("Split:" + splitValue);
    }

    /**
     * Prints that item could not be found. This function doesn't check if this is actually the case.
     *
     * @param searchString the search term used which did not find an item
     */
    private void printUnableToFindItem(String searchString) {
        final String response = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("Unable to find item '")
                .append(ChatColorType.NORMAL)
                .append(searchString)
                .append(ChatColorType.HIGHLIGHT)
                .append("'.")
                .build();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(response)
                .build());
        log.debug("Unable to find item");
    }

    /**
     * Returns item GE price if found in substring. Returns 0 if
     *
     * @param substring the message with "!split " removed
     * @return if found returns ItemPrice otherwise null
     */

    private ItemPrice itemPriceLookup(String substring) {
        String[] substringParts = substring.split(" ");

        // substring is either the splitSize or item name to split by default
        if (substringParts.length < 2) {
            // if the substring can be parsed then it's presumed to be splitSize and not an item
            try {
                parseInt(substring);
                return null;
            } catch (NumberFormatException e) {
                return itemPrice(substring);
            }
        } else {
            try {
                parseInt(substringParts[substringParts.length - 1]);
                String[] allButLastElement = Arrays.copyOf(substringParts, substringParts.length - 1);
                return itemPrice(String.join("", allButLastElement));

            } catch (NumberFormatException e) {
                return itemPrice(substring);
            }
        }
    }

    /**
     * COPIED FROM ChatCommandsPlugin.java with some modifications
     * Copyright (c) 2017, Adam <Adam@sigterm.info>
     *
     * @param substring the message with "!split " removed
     * @return if found returns ItemPrice otherwise null
     */
    private ItemPrice itemPrice(String substring) {

        List<ItemPrice> results = itemManager.search(substring);

        if (!results.isEmpty()) {
            return retrieveFromList(results, substring);
        }
        return null;
    }

    /**
     * COPIED FROM ChatCommandsPlugin.java
     * Copyright (c) 2017, Adam <Adam@sigterm.info>
     * <p>
     * Compares the names of the items in the list with the original input.
     * Returns the item if its name is equal to the original input or the
     * shortest match if no exact match is found.
     *
     * @param items         List of items.
     * @param originalInput String with the original input.
     * @return Item which has a name equal to the original input.
     */
    private ItemPrice retrieveFromList(List<ItemPrice> items, String originalInput) {
        ItemPrice shortest = null;
        for (ItemPrice item : items) {
            // if statement modified based on IntelliJ suggestion
            if (item.getName().equalsIgnoreCase(originalInput)) {
                return item;
            }

            if (shortest == null || item.getName().length() < shortest.getName().length()) {
                shortest = item;
            }
        }

        // Take a guess
        return shortest;
    }

    @Provides
    SplitterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SplitterConfig.class);
    }
}
