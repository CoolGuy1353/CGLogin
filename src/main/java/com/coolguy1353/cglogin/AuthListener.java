package com.coolguy1353.cglogin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.Cancellable;

final class AuthListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("login", "register", "logout", "resetpw");
    private final CGLoginPlugin plugin;

    AuthListener(CGLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.restoreRememberedSession(player)) {
            return;
        }
        plugin.requireAuthentication(player);
        boolean hasAccount = plugin.hasAccountFor(player.getName());
        plugin.showAuthenticationTitle(player, !hasAccount);
        if (hasAccount) {
            plugin.sendMessage(player, "prompt-login");
        } else {
            plugin.sendMessage(player, "prompt-register");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer().getUniqueId()) && hasMoved(event.getFrom(), event.getTo())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.isAuthenticated(event.getPlayer().getUniqueId())) {
            return;
        }
        String[] parts = event.getMessage().substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (command.startsWith("minecraft:")) {
            command = command.substring("minecraft:".length());
        }
        if (!ALLOWED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            plugin.sendMessage(event.getPlayer(), "login-required-command");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        cancelUnlessAuthenticated(event.getPlayer(), event);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        cancelUnlessAuthenticated(event.getPlayer(), event);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        cancelUnlessAuthenticated(event.getPlayer(), event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        cancelUnlessAuthenticated(event.getPlayer(), event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !plugin.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !plugin.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !plugin.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !plugin.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        cancelUnlessAuthenticated(event.getPlayer(), event);
    }

    private boolean hasMoved(Location from, Location to) {
        return to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ());
    }

    private void cancelUnlessAuthenticated(Player player, Cancellable event) {
        if (!plugin.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}