package com.splitter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
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
        int valueToSplit = 0;
        String itemName = null;

        // Checks if the inventory contains gold or platinum tokens and then updates hasPlatinum and valueToSplit
        if (container != null) {
            hasPlatinum = container.contains(PlatTokenID);
            valueToSplit = (hasPlatinum) ? container.count(PlatTokenID) : container.count(CoinID);
        }

        // If command was "split::" and nothing more and there was no money in the inventory
        if (args.length == 0 && valueToSplit == 0) {
            noCashMessage();
            return;

            // If there was exactly 1 argument, check if argument was a split size or an item to look up the price for
        } else if (args.length == 1) {
            int[] splitReturn = extractSplitSize(args);
            splitSize = splitReturn[0] == -1 ? splitSize : splitReturn[1];

            // A split size was extracted but the player has NO money in the inventory
            if (splitReturn[0] >= 0 && valueToSplit == 0) {
                noCashMessage();
                return;

                // Split size was not found
            } else if (splitReturn[0] == -1) {
                itemName = itemNameConcat(args, splitReturn[0]);
                valueToSplit = getValueOfItem(itemName);

                // An item was not found
                if (valueToSplit == -1) {
                    printUnableToFindItem(itemName);
                    return;
                } else {
                    item = itemPrice(itemName);
                }
            }
        } else if (args.length > 1) {
            int[] splitReturn = extractSplitSize(args);
            splitSize = splitReturn[0] == -1 ? splitSize : splitReturn[1];
            itemName = itemNameConcat(args, splitReturn[0]);
            valueToSplit = getValueOfItem(itemName);

            if (valueToSplit == -1) {
                printUnableToFindItem(itemName);
                return;
            }

            item = itemPrice(itemName);
        }

        if (splitSize == 0) {
            cannotSplitByZero();
            return;
        }
        int splitValue = valueToSplit / splitSize;

        if (item != null) {
            printSplitItem(item.getName(), splitValue, valueToSplit, splitSize);
        } else {
            printSplit(hasPlatinum, splitValue, valueToSplit, splitSize);
        }
    }

    /**
     * Concatenates the appropriate args based on where the split size was found.
     *
     * @param args           all arguments given to ::split
     * @param splitSizeIndex 0 if first elem in args is split size, 1 if last elem in split size, -1 if neither was split size
     * @return concatenated String from args based on splitSizeIndex
     */
    private String itemNameConcat(String[] args, int splitSizeIndex) {
        String[] argsExtracted;

        // Last element was split size
        if (splitSizeIndex == 1) {
            argsExtracted = Arrays.copyOf(args, args.length - 1);

            // First element was split size
        } else if (splitSizeIndex == 0) {
            argsExtracted = Arrays.copyOfRange(args, 1, args.length);

            // None of the elements was split size
        } else {
            argsExtracted = args;
        }

        return String.join(" ", argsExtracted);
    }

    /**
     * Returns the value of the item to split or -1 if no item was found.
     *
     * @param itemName looked up for item match
     * @return returns the value of item match, -1 if no match was found
     */
    private int getValueOfItem(String itemName) {
        ItemPrice item = itemPrice(itemName);
        if (item != null) {
            return runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
        } else {
            return -1;
        }
    }

    /**
     * Returns a 2-size array with the first element being 1 if the last element of args is found as a split size, or
     * 0 if the first element is found as a split size, or -1 if neither first nor last element found as a split size.
     * Second element of the array contains the split size.
     *
     * @param args all arguments given to ::split
     * @return a two element array with the first element being status and the last element being split size
     */
    private int[] extractSplitSize(String[] args) {
        int splitSize;

        if (args.length == 0) {
            log.debug("Bad code, don't run extractSplitSize with an empty array");
            return new int[]{-1, 0};
        }

        try {
            // Last argument is assumed to the split size
            splitSize = parseInt(args[args.length - 1]);
            return new int[]{1, splitSize};

            // If exception was raised by parseInt, try if the first element is a split size
        } catch (NumberFormatException e) {
            try {
                splitSize = parseInt(args[0]);
                return new int[]{0, splitSize};
            } catch (NumberFormatException f) {
                return new int[]{-1, 0};
            }
        }
    }

    /**
     * Informs the user that the plugin read the input as if the user set split size to 0.
     */
    private void cannotSplitByZero()
    {
        log.debug("Split size was 0");
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .value("Unable to split by 0.")
                .build());
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
     * @param hasPlatinum  true if splitting platinum tokens, false if splitting coins
     * @param splitValue   the value of each portion of the split
     * @param valueToSplit the total value to split
     * @param splitSize    the number of portions
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
     * @param itemName     name of the item being split
     * @param splitValue   the value of each portion of the split
     * @param valueToSplit the total value to split
     * @param splitSize    the number of portions
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
