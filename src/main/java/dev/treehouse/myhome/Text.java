package dev.treehouse.myhome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class Text {
    private final String prefix;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public Text(String prefix) { this.prefix = prefix == null ? "" : prefix; }
    public void send(CommandSender s, String msg) {
        s.sendMessage(mm.deserialize(prefix + msg));
    }
    public Component comp(String s) { return mm.deserialize(s); }
    public String prefixRaw() { return prefix; }
}
