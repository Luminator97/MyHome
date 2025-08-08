package dev.treehouse.myhome;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Text {
    private final String rawPrefix;   // e.g. "&a&lHome&r  &8≫ &7"
    private final String prefix;      // translated to section-colors

    public Text(String prefixFromConfig) {
        this.rawPrefix = prefixFromConfig == null ? "" : prefixFromConfig;
        this.prefix = ChatColor.translateAlternateColorCodes('&', this.rawPrefix);
    }

    /** Send with prefix. Supports & color codes in message. */
    public void send(CommandSender s, String msgWithAmpColors) {
        String body = ChatColor.translateAlternateColorCodes('&', msgWithAmpColors);
        s.sendMessage(prefix + body);
    }

    /** Translate & codes without prefix (for building lines). */
    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String prefixRaw() { return rawPrefix; }
    public String prefixColored() { return prefix; }
}
