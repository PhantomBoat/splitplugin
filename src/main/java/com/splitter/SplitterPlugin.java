package com.splitter;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.util.QuantityFormatter;

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

    @Override
    protected void startUp() throws Exception {
        log.info("SPLITTER SIZE:" + config.splitSize());
        chatCommandManager.registerCommandAsync(SPLIT_COMMAND_STRING, this::computeSplit);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Splitter stopped!");
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
        String coinSplit = "Coin";
        String platinumSplit = "Platinum Token";
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);

        MessageNode messageNode = chatMessage.getMessageNode();
        if (container == null) {
            log.info("Inventory is empty and hasn't loaded.");
            final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT)
                    .append("No Coin(s) or Platinum token(s) in the inventory.");
            final String response = chatMessageBuilder.build();
            messageNode.setRuneLiteFormatMessage(response);
            client.refreshChat();
            return;
        }

        int splitSize = config.splitSize();
        boolean hasPlatinum = container.contains(PlatTokenID);
        int valueToSplit = (hasPlatinum) ? container.count(PlatTokenID) : container.count(CoinID);

        if (message.length() > SPLIT_COMMAND_STRING.length()) {
            String msgSplitSize = message.substring(SPLIT_COMMAND_STRING.length() + 1);
            try {
                splitSize = parseInt(msgSplitSize);
            } catch (NumberFormatException e) {
                log.debug("Invalid split input");
                return;
            }
            if (splitSize <= 0) {
                log.debug("Invalid split size");
                return;
            }
        }

        int splitValue = valueToSplit / splitSize;

        if (!hasPlatinum && !container.contains(CoinID)) {
            final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT)
                    .append("No Coin(s) or Platinum token(s) in the inventory.");
            final String response = chatMessageBuilder.build();
            log.debug("No money in the inventory.");
            messageNode.setRuneLiteFormatMessage(response);
            client.refreshChat();
            return;
        }

        String splitType = (hasPlatinum) ? platinumSplit : coinSplit;
        final ChatMessageBuilder chatMessageBuilder = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(QuantityFormatter.formatNumber(splitValue))
                .append(ChatColorType.NORMAL)
                .append(" ")
                .append(splitType)
                .append(" , ( ")
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
