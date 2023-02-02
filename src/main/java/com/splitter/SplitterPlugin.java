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
    private static final String SPLIT_COMMAND_STRING = "!split";
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
        if (commandExecuted.getCommand().equals("split")) {
            computeSplit(commandExecuted.getArguments());
        }
    }

    @Override
    protected void startUp() throws Exception {
        log.info("SPLITTER SIZE:" + config.splitSize());
        chatCommandManager.registerCommandAsync(SPLIT_COMMAND_STRING, this::computeSplit);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Splitter stopped!");
    }

    void computeSplit(String[] args) {
        int CoinID = 995;
        int PlatTokenID = 13204;
        ItemPrice item = null;
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);

        if (container == null && args.length == 0) {
            inventoryHasNotLoaded();
            return;
        }

        int splitSize = config.splitSize();
        boolean hasPlatinum = false;
        int valueToSplit = -1;
        if (container != null)
        {
            hasPlatinum = container.contains(PlatTokenID);
            valueToSplit = (hasPlatinum) ? container.count(PlatTokenID) : container.count(CoinID);
        }


        if (args.length == 0) {
            int splitValue = valueToSplit / splitSize;
            if (valueToSplit == 0) {
                noCashMessage();
                return;
            }

            printSplit(hasPlatinum, splitValue, valueToSplit, splitSize);
            return;
        } else if (args.length == 1) {
            try {
                splitSize = parseInt(args[0]);
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
                splitSize = parseInt(args[args.length - 1]);
                argElements = Arrays.copyOf(args, args.length - 1);
            } catch (NumberFormatException e) {
                argElements = args;
            }
            if (splitSize <= 0) {
                log.debug("Invalid split size");
                return;
            }

            item = itemPriceLookup(String.join("", argElements));
            if (item != null) {
                valueToSplit = runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
            } else {
                printUnableToFindItem(String.join("", argElements));
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

    private void inventoryHasNotLoaded() {
        log.debug("Inventory is empty and hasn't loaded.");
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .value("No Coin(s) or Platinum token(s) in the inventory.")
                .build());
    }

    private void noCashMessage() {
        log.debug("No money in the inventory.");
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .value("No Coin(s) or Platinum token(s) in the inventory.")
                .build());
    }

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
     * Computes the split of gold or platinum tokens and replaces the message in chat with the number. (floor)
     *
     * @param chatMessage The chat message containing the command.
     * @param message     The chat message
     */
    @VisibleForTesting
    void computeSplit(ChatMessage chatMessage, String message) {
        int CoinID = 995;
        int PlatTokenID = 13204;
        ItemPrice item = null;
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);

        MessageNode messageNode = chatMessage.getMessageNode();
        if (container == null) {
            inventoryHasNotLoaded(messageNode);
            return;
        }

        int splitSize = config.splitSize();
        boolean hasPlatinum = container.contains(PlatTokenID);
        int valueToSplit = (hasPlatinum) ? container.count(PlatTokenID) : container.count(CoinID);

        if (message.length() > SPLIT_COMMAND_STRING.length()) {
            String msgSplitSize = message.substring(SPLIT_COMMAND_STRING.length() + 1);
            String[] msgSplitSizeParts = msgSplitSize.split(" ");
            if (msgSplitSizeParts.length == 1) {
                try {
                    splitSize = parseInt(msgSplitSize);
                } catch (NumberFormatException e) {
                    item = itemPriceLookup(msgSplitSize);
                    if (item == null) {
                        log.debug("Invalid split input");
                        return;
                    }

                    valueToSplit = runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
                }
                if (splitSize <= 0) {
                    log.debug("Invalid split size");
                    return;
                }
            } else if (msgSplitSizeParts.length > 1) {
                try {
                    splitSize = parseInt(msgSplitSizeParts[msgSplitSizeParts.length - 1]);
                } catch (NumberFormatException e) {
                    log.debug("Invalid split input");
                    return;
                }
                if (splitSize <= 0) {
                    log.debug("Invalid split size");
                    return;
                }
                item = itemPriceLookup(msgSplitSize);
                if (item != null) {
                    valueToSplit = runeLiteConfig.useWikiItemPrices() ? itemManager.getWikiPrice(item) : item.getPrice();
                }
            } else {
                return;
            }
        }

        int splitValue = valueToSplit / splitSize;

        if (!hasPlatinum && !container.contains(CoinID)) {
            noCashMessage(messageNode);
            return;
        }

        if (item != null) {
            printSplitItem(item.getName(), splitValue, valueToSplit, splitSize, messageNode);
        } else {
            printSplit(hasPlatinum, splitValue, valueToSplit, splitSize, messageNode);
        }
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

    private void noCashMessage(MessageNode messageNode) {
        final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("No Coin(s) or Platinum token(s) in the inventory.");
        final String response = chatMessageBuilder.build();
        log.debug("No money in the inventory.");
        messageNode.setRuneLiteFormatMessage(response);
        client.refreshChat();
    }

    private void inventoryHasNotLoaded(MessageNode messageNode) {
        log.info("Inventory is empty and hasn't loaded.");
        final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("No Coin(s) or Platinum token(s) in the inventory.");
        final String response = chatMessageBuilder.build();
        messageNode.setRuneLiteFormatMessage(response);
        client.refreshChat();
    }

    private void printSplit(boolean hasPlatinum, int splitValue, int valueToSplit, int splitSize, MessageNode messageNode) {
        String coinSplit = "Coin";
        String platinumSplit = "Platinum Token";

        String splitType = (hasPlatinum) ? platinumSplit : coinSplit;

        final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
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
                .append(" )");
        final String response = chatMessageBuilder.build();
        log.debug("Split:" + splitValue);
        messageNode.setRuneLiteFormatMessage(response);
        client.refreshChat();
    }

    private void printSplitItem(String itemName, int splitValue, int valueToSplit, int splitSize, MessageNode messageNode) {
        final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
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
                .append(" )");
        final String response = chatMessageBuilder.build();
        log.debug("Split:" + splitValue);
        messageNode.setRuneLiteFormatMessage(response);
        client.refreshChat();
    }

    @Provides
    SplitterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SplitterConfig.class);
    }
}
