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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CGLoginPlugin extends JavaPlugin {
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    private YamlConfiguration accounts;
    private File accountsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        accountsFile = new File(getDataFolder(), "accounts.yml");
        accounts = YamlConfiguration.loadConfiguration(accountsFile);
        migrateUuidAccounts();
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
            sendMessage(player, "usage-register");
            return true;
        }
        String username = player.getName();
        if (hasAccount(username)) {
            sendMessage(player, "account-exists");
            return true;
        }
        if (!validPassword(player, args[0])) {
            return true;
        }
        if (!storePassword(username, username, args[0])) {
            sendMessage(player, "account-save-failed");
            return true;
        }
        startSession(player, username);
        failedAttempts.remove(player.getUniqueId());
        sendMessage(player, "account-created");
        return true;
    }

    private boolean login(Player player, String[] args) {
        if (args.length != 1) {
            sendMessage(player, "usage-login");
            return true;
        }
        String username = player.getName();
        if (!hasAccount(username)) {
            sendMessage(player, "account-missing");
            return true;
        }
        String path = accountPath(username);
        if (!PasswordHasher.verify(args[0], accounts.getString(path + ".salt"),
                accounts.getString(path + ".hash"))) {
            int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            if (attempts >= 5) {
                player.kickPlayer(message("too-many-attempts"));
            } else {
                sendMessage(player, "wrong-password", "%attempts%", Integer.toString(5 - attempts));
            }
            return true;
        }
        startSession(player, username);
        failedAttempts.remove(player.getUniqueId());
        sendMessage(player, "logged-in");
        return true;
    }

    private boolean logout(Player player, String[] args) {
        if (args.length != 0) {
            sendMessage(player, "usage-logout");
        } else if (authenticated.remove(player.getUniqueId())) {
            clearRememberedSession(player.getName());
            requireAuthentication(player);
            showAuthenticationTitle(player, false);
            sendMessage(player, "logged-out");
        } else {
            sendMessage(player, "not-logged-in");
        }
        return true;
    }

    private boolean resetPassword(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cglogin.admin")) {
            sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length != 2) {
            sendMessage(sender, "usage-resetpw");
            return true;
        }
        if (!validUsername(args[0]) || !hasAccount(args[0])) {
            sendMessage(sender, "account-missing");
            return true;
        }
        if (!validPassword(sender, args[1])) {
            return true;
        }
        String username = accounts.getString(accountPath(args[0]) + ".name", args[0]);
        if (!storePassword(username, username, args[1])) {
            sendMessage(sender, "password-reset-save-failed");
            return true;
        }
        Player onlineTarget = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(username))
                .findFirst().orElse(null);
        if (onlineTarget != null) {
            requireAuthentication(onlineTarget);
            showAuthenticationTitle(onlineTarget, false);
            sendMessage(onlineTarget, "password-reset-notice");
        }
        sendMessage(sender, "password-reset", "%player%", username);
        return true;
    }

    private boolean validPassword(CommandSender sender, String password) {
        int minimumLength = Math.max(1, getConfig().getInt("minimum-password-length", 8));
        if (password.length() < minimumLength) {
            sendMessage(sender, "password-too-short", "%minimum%", Integer.toString(minimumLength));
            return false;
        }
        return true;
    }

    private boolean hasAccount(String username) {
        String path = accountPath(username);
        return accounts.contains(path + ".hash") && accounts.contains(path + ".salt");
    }

    private String accountPath(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private boolean validUsername(String username) {
        return username.matches("[A-Za-z0-9_]{1,16}");
    }

    private boolean storePassword(String username, String displayName, String password) {
        PasswordHasher.PasswordRecord record = PasswordHasher.hash(password);
        String path = accountPath(username);
        accounts.set(path + ".name", displayName);
        accounts.set(path + ".salt", record.salt());
        accounts.set(path + ".hash", record.hash());
        accounts.set(path + ".session-expires-at", 0L);
        return saveAccounts();
    }

    private void startSession(Player player, String username) {
        authenticated.add(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        long sessionHours = Math.max(0, Math.min(87_600, getConfig().getLong("remember-session-hours", 168)));
        long expiresAt = sessionHours == 0 ? 0 : System.currentTimeMillis() + sessionHours * 3_600_000L;
        accounts.set(accountPath(username) + ".session-expires-at", expiresAt);
        saveAccounts();
    }

    private void clearRememberedSession(String username) {
        accounts.set(accountPath(username) + ".session-expires-at", 0L);
        saveAccounts();
    }

    private boolean saveAccounts() {
        try {
            accounts.save(accountsFile);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not save account data: " + exception.getMessage());
            return false;
        }
    }

    private void migrateUuidAccounts() {
        boolean changed = false;
        for (String oldKey : new HashSet<>(accounts.getKeys(false))) {
            try {
                UUID.fromString(oldKey);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            String username = accounts.getString(oldKey + ".name", "");
            if (username.isBlank()) {
                getLogger().warning("Skipping account migration for " + oldKey + ": no saved username.");
                continue;
            }
            String newPath = accountPath(username);
            if (!accounts.contains(newPath + ".hash")) {
                for (String key : new String[] {"name", "salt", "hash"}) {
                    accounts.set(newPath + "." + key, accounts.get(oldKey + "." + key));
                }
                accounts.set(newPath + ".session-expires-at", 0L);
            } else {
                getLogger().warning("Duplicate account username while migrating " + username + "; keeping the existing username record.");
            }
            accounts.set(oldKey, null);
            changed = true;
        }
        if (changed) {
            saveAccounts();
        }
    }

    boolean hasAccountFor(String username) {
        return hasAccount(username);
    }

    boolean isAuthenticated(UUID playerId) {
        return authenticated.contains(playerId);
    }

    void requireAuthentication(Player player) {
        authenticated.remove(player.getUniqueId());
        failedAttempts.remove(player.getUniqueId());
        int timeout = Math.max(1, getConfig().getInt("authentication-timeout-seconds", 60));
        int blindnessDuration = (int) Math.min(Integer.MAX_VALUE, timeout * 20L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 0, false, false, false));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !isAuthenticated(player.getUniqueId())) {
                player.kickPlayer(message("authentication-timeout"));
            }
        }, timeout * 20L);
    }

    void showAuthenticationTitle(Player player, boolean needsRegistration) {
        String path = needsRegistration ? "titles.register" : "titles.login";
        String title = getConfig().getString(path + ".title", needsRegistration ? "&aREGISTER" : "&eLOGIN");
        String subtitle = getConfig().getString(path + ".subtitle",
                needsRegistration ? "&7Use /register <password>" : "&7Use /login <password>");
        int fadeIn = Math.max(0, getConfig().getInt("title-fade-in-ticks", 10));
        int stay = Math.max(0, getConfig().getInt("title-stay-ticks", 60));
        int fadeOut = Math.max(0, getConfig().getInt("title-fade-out-ticks", 10));
        player.sendTitle(ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle), fadeIn, stay, fadeOut);
    }

    boolean restoreRememberedSession(Player player) {
        String username = player.getName();
        if (!hasAccount(username)) {
            return false;
        }
        long expiresAt = accounts.getLong(accountPath(username) + ".session-expires-at", 0L);
        if (expiresAt <= System.currentTimeMillis()) {
            return false;
        }
        startSession(player, username);
        sendMessage(player, "session-restored");
        return true;
    }

    void sendMessage(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(message(key, replacements));
    }

    private String message(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}