package dev.livetag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.PermissionAttachment;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LiveTag extends JavaPlugin implements Listener, TabExecutor {

    private static final String TEAM_NAME = "lt_live";
    private static final int BUTTON_SLOT = 11;
    private static final int WEBCAM_SLOT = 15;
    private static final String WEBCAM_PERMISSION = "webcam.broadcast";

    private final Set<UUID> live = new HashSet<>();
    private final Set<UUID> webcamOptIn = new HashSet<>();
    private final Map<UUID, PermissionAttachment> webcamAttachments = new HashMap<>();
    private File dataFile;

    /** Marker so we can recognise our own menu. */
    private static class MenuHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("live").setExecutor(this);
        getCommand("live").setTabCompleter(this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (live.contains(p.getUniqueId())) apply(p, true);
            syncWebcamPermission(p);
        }
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) clearWebcamPermission(p);
        saveData();
    }

    // ------------------------------------------------------------ data

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : yml.getStringList("webcam-opt-in")) {
            try { webcamOptIn.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        for (String s : yml.getStringList("live")) {
            try {
                live.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

        
    private void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("live", live.stream().map(UUID::toString).toList());
        yml.set("webcam-opt-in", webcamOptIn.stream().map(UUID::toString).toList());
        try {
            getDataFolder().mkdirs();
            yml.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "");
    }

    // ------------------------------------------------------------ core

    private Component prefix() {
        return MiniMessage.miniMessage().deserialize(
                getConfig().getString("prefix", "<dark_purple><bold>[LIVE]</bold></dark_purple> "));
    }

    /**
     * A scoreboard team prefix shows the tag above the player's head.
     * The display name covers chat, and the player list name covers the tab list.
     */
    private Team team() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(TEAM_NAME);
        if (t == null) t = sb.registerNewTeam(TEAM_NAME);
        t.prefix(prefix());
        return t;
    }

    /** True if the TAB plugin is installed - it controls the tab list itself. */
    private boolean hasTabPlugin() {
        return Bukkit.getPluginManager().getPlugin("TAB") != null;
    }

    /** Asks the TAB plugin to show (or remove) the tag in the tab list. */
    private void syncTabPlugin(Player p, boolean on) {
        if (!hasTabPlugin()) return;
        String cmd = "tab player " + p.getName() + " tabprefix";
        if (on) {
            cmd += " " + getConfig().getString("tab-prefix", "&5&l[LIVE] &r");
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void apply(Player p, boolean on) {
        Team t = team();
        if (on) {
            t.addEntry(p.getName());
            p.displayName(prefix().append(Component.text(p.getName())));
            if (!hasTabPlugin()) {
                p.playerListName(prefix().append(Component.text(p.getName())));
            }
        } else {
            t.removeEntry(p.getName());
            p.displayName(null);
            if (!hasTabPlugin()) {
                p.playerListName(null);
            }
        }
    }

    /** Deny broadcasting by default, even if Webcam grants its permission by default. */
    private void syncWebcamPermission(Player p) {
        PermissionAttachment attachment = webcamAttachments.computeIfAbsent(p.getUniqueId(), id -> p.addAttachment(this));
        boolean allowed = live.contains(p.getUniqueId()) && webcamOptIn.contains(p.getUniqueId())
                && p.hasPermission("livetag.use");
        attachment.setPermission(WEBCAM_PERMISSION, allowed);
        p.recalculatePermissions();
    }

    private void clearWebcamPermission(Player p) {
        PermissionAttachment attachment = webcamAttachments.remove(p.getUniqueId());
        if (attachment != null) p.removeAttachment(attachment);
    }

    private void setWebcam(Player p, boolean on) {
        if (on && !live.contains(p.getUniqueId())) {
            p.sendMessage(Component.text("Enable LIVE first to allow your webcam.", NamedTextColor.RED));
            return;
        }
        if (on) webcamOptIn.add(p.getUniqueId());
        else webcamOptIn.remove(p.getUniqueId());
        syncWebcamPermission(p);
        saveData();
        p.sendMessage(Component.text(on
                ? "Webcam permission enabled. You must manually start your webcam in the client mod."
                : "Webcam permission disabled.", on ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private void setLive(Player p, boolean on) {
        if (on) live.add(p.getUniqueId());
        else live.remove(p.getUniqueId());
        apply(p, on);
        syncWebcamPermission(p);
        syncTabPlugin(p, on);
        saveData();
        p.sendMessage(Component.text(msg(on ? "enabled" : "disabled"),
                on ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        syncWebcamPermission(p);
        if (live.contains(p.getUniqueId())) {
            apply(p, true);
            // give the TAB plugin a moment to load the player first
            Bukkit.getScheduler().runTaskLater(this, () -> syncTabPlugin(p, true), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { clearWebcamPermission(e.getPlayer()); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("livetag.use")) return;
        if (e.getRawSlot() == BUTTON_SLOT) {
            setLive(p, !live.contains(p.getUniqueId()));
        } else if (e.getRawSlot() == WEBCAM_SLOT) {
            setWebcam(p, !webcamOptIn.contains(p.getUniqueId()));
        } else return;
        e.getView().getTopInventory().setItem(BUTTON_SLOT, button(p));
        e.getView().getTopInventory().setItem(WEBCAM_SLOT, webcamButton(p));
    }

    // ------------------------------------------------------------ GUI

    private ItemStack button(Player p) {
        boolean on = live.contains(p.getUniqueId());
        ItemStack item = new ItemStack(on ? Material.PURPLE_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("LIVE: " + (on ? "ON" : "OFF"),
                        on ? NamedTextColor.DARK_PURPLE : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
        meta.lore(List.of(Component.text("Click to turn it " + (on ? "off" : "on"),
                        NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack webcamButton(Player p) {
        boolean liveNow = live.contains(p.getUniqueId());
        boolean enabled = webcamOptIn.contains(p.getUniqueId()) && liveNow;
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("WEBCAM: " + (enabled ? "ALLOWED" : "DISABLED"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text(liveNow ? "Click to toggle webcam permission" : "Enable LIVE first", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Camera starts manually in the client mod.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(), 27,
                Component.text("Stream Tag", NamedTextColor.DARK_PURPLE));
        inv.setItem(BUTTON_SLOT, button(p));
        inv.setItem(WEBCAM_SLOT, webcamButton(p));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ command

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "toggle";

        if (sub.equals("reload")) {
            if (!sender.hasPermission("livetag.admin")) return true;
            reloadConfig();
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (live.contains(o.getUniqueId())) apply(o, true);
            }
            sender.sendMessage(Component.text("LiveTag reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("livetag.use")) {
            p.sendMessage(Component.text(msg("no-permission"), NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "menu", "gui" -> openMenu(p);
            case "on" -> setLive(p, true);
            case "off" -> setLive(p, false);
            default -> setLive(p, !live.contains(p.getUniqueId()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("menu", "on", "off") : List.of();
    }
}
