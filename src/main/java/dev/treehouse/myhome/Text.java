package dev.treehouse.myhome;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Text {
    private final String rawPrefix;   // e.g. "&a&lHome&r &8≫ &7"
    private final String prefix;      // translated (§ codes)

    public Text(String prefixFromConfig) {
        this.rawPrefix = prefixFromConfig == null ? "" : prefixFromConfig;
        this.prefix   = ChatColor.translateAlternateColorCodes('&', this.rawPrefix);
    }

    /** Send with prefix. Message may also contain & color codes. */
    public void send(CommandSender s, String msgWithAmpColors) {
        String body = ChatColor.translateAlternateColorCodes('&', msgWithAmpColors);
        s.sendMessage(prefix + body);
    }

    /** If you ever need the colored prefix alone. */
    public String prefixColored() { return prefix; }
    public String prefixRaw()     { return rawPrefix; }
}
