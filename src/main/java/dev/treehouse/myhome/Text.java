package dev.treehouse.myhome;

import org.bukkit.ChatColor;

public class Text {

    public static String format(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String prefix() {
        return format("&a&lHome &7");
    }

    public static String noHomes() {
        return prefix() + format("&eNo my homes available!");
    }

    public static String setHome(String homeName) {
        return prefix() + format("&7Set home &e" + homeName + " &7to your current location!");
    }

    public static String teleported() {
        return prefix() + format("&7You have been teleported!");
    }

    public static String noPublicHomes() {
        return prefix() + format("&eNo public homes available!");
    }
}
