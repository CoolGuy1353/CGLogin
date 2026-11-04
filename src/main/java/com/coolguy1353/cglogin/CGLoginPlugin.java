package com.coolguy1353.cglogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CGLoginPlugin extends JavaPlugin {
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    private YamlConfiguration accounts;
    private File accountsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        accountsFile = new File(getDataFolder(), "accounts.yml");
        accounts = YamlConfiguration.loadConfiguration(accountsFile);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getLogger().info("CGLogin enabled.");
    }

    @Override
    public void onDisable() {
        authenticated.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("resetpw")) {
            return resetPassword(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        return switch (name) {
            case "register" -> register(player, args);
            case "login" -> login(player, args);
            case "logout" -> logout(player, args);
            default -> false;
        };
    }

    private boolean register(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /register <password>");
            return true;
        }
        if (hasAccount(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already have an account. Use /login <password>.");
            return true;
        }
        if (!validPassword(player, args[0])) {
            return true;
        }
        if (!storePassword(player.getUniqueId(), player.getName(), args[0])) {
            player.sendMessage(ChatColor.RED + "Could not save your account. Please contact an administrator.");
            return true;
        }
        authenticated.add(player.getUniqueId());
        failedAttempts.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Account registered and logged in.");
        return true;
    }

    private boolean login(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /login <password>");
            return true;
        }
        if (!hasAccount(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "No account found. Use /register <password>.");
            return true;
        }
        if (!PasswordHasher.verify(args[0], accounts.getString(player.getUniqueId() + ".salt"),
                accounts.getString(player.getUniqueId() + ".hash"))) {
            int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            if (attempts >= 5) {
                player.kickPlayer(ChatColor.RED + "Too many failed login attempts.");
            } else {
                player.sendMessage(ChatColor.RED + "Incorrect password. Attempts remaining: " + (5 - attempts));
            }
            return true;
        }
        authenticated.add(player.getUniqueId());
        failedAttempts.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "You are now logged in.");
        return true;
    }

    private boolean logout(Player player, String[] args) {
        if (args.length != 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /logout");
        } else if (authenticated.remove(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are now logged out.");
        } else {
            player.sendMessage(ChatColor.RED + "You are not logged in.");
        }
        return true;
    }

    private boolean resetPassword(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cglogin.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /resetpw <player> <new-password>");
            return true;
        }
        String targetId = findAccount(args[0]);
        if (targetId == null) {
            sender.sendMessage(ChatColor.RED + "No registered account found for that player.");
            return true;
        }
        if (!validPassword(sender, args[1])) {
            return true;
        }
        UUID targetUuid = UUID.fromString(targetId);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (!storePassword(targetUuid, target.getName() == null ? args[0] : target.getName(), args[1])) {
            sender.sendMessage(ChatColor.RED + "Could not save the password reset.");
            return true;
        }
        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null) {
            requireAuthentication(onlineTarget);
            onlineTarget.sendMessage(ChatColor.RED + "Your password was reset. Please log in again.");
        }
        sender.sendMessage(ChatColor.GREEN + "Password reset for " + args[0] + ".");
        return true;
    }

    private boolean validPassword(CommandSender sender, String password) {
        if (password.length() < 8) {
            sender.sendMessage(ChatColor.RED + "Passwords must be at least 8 characters long.");
            return false;
        }
        return true;
    }

    private boolean hasAccount(UUID playerId) {
        return accounts.contains(playerId.toString() + ".hash")
                && accounts.contains(playerId.toString() + ".salt");
    }

    private String findAccount(String playerName) {
        for (String id : accounts.getKeys(false)) {
            ConfigurationSection account = accounts.getConfigurationSection(id);
            if (account != null && account.getString("name", "").equalsIgnoreCase(playerName)) {
                return id;
            }
        }
        return null;
    }

    private boolean storePassword(UUID playerId, String playerName, String password) {
        PasswordHasher.PasswordRecord record = PasswordHasher.hash(password);
        String path = playerId.toString();
        accounts.set(path + ".name", playerName);
        accounts.set(path + ".salt", record.salt());
        accounts.set(path + ".hash", record.hash());
        try {
            accounts.save(accountsFile);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not save account data: " + exception.getMessage());
            return false;
        }
    }

    boolean hasAccountFor(UUID playerId) {
        return hasAccount(playerId);
    }

    boolean isAuthenticated(UUID playerId) {
        return authenticated.contains(playerId);
    }

    void requireAuthentication(Player player) {
        authenticated.remove(player.getUniqueId());
        failedAttempts.remove(player.getUniqueId());
        int timeout = Math.max(1, getConfig().getInt("authentication-timeout-seconds", 60));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !isAuthenticated(player.getUniqueId())) {
                player.kickPlayer(ChatColor.RED + "You did not authenticate in time.");
            }
        }, timeout * 20L);
    }
}