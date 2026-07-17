package dev.lorenzz.frankie.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.util.ConfigProvider;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Handles player chat: global formatting, profanity filter, and command passthrough.
 */
public class ChatListener {

    private final Messages messages;
    private final ProxyServer proxy;

    public ChatListener(Messages messages, ProxyServer proxy) {
        this.messages = messages;
        this.proxy = proxy;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String message = event.getMessage();

        // Ignore commands (starting with '/')
        if (message.startsWith("/")) {
            return;
        }

        // Profanity filter (only if antiswear module enabled)
        String filtered = profanityFilter(message);

        // Replace message with filtered version; chat stays server-local
        event.setResult(PlayerChatEvent.ChatResult.message(filtered));
    }

    /**
     * Replace profanity with asterisks if antiswear module is enabled.
     */
    private String profanityFilter(String input) {
        if (!ConfigProvider.getBoolean("modules.antiswear", true)) {
            return input;
        }
        List<String> badWords = messages.getStringList("antiswear.words");
        if (badWords == null || badWords.isEmpty()) {
            return input;
        }
        String lower = input.toLowerCase();
        StringBuilder result = new StringBuilder(input);
        for (String word : badWords) {
            if (word == null || word.isEmpty()) continue;
            int idx = 0;
            while ((idx = lower.indexOf(word.toLowerCase(), idx)) != -1) {
                // Replace with asterisks of same length
                StringBuilder stars = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    stars.append('*');
                }
                // Replace in original string (preserve case)
                result.replace(idx, idx + word.length(), stars.toString());
                // Update lower string for next search
                lower = lower.substring(0, idx) + stars + lower.substring(idx + word.length());
                idx += stars.length();
            }
        }
        return result.toString();
    }
}