package dev.qixils.fahare;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

// TODO: manual reset command

public final class Fahare extends JavaPlugin implements Listener {

    private static final NamespacedKey REAL_OVERWORLD_KEY = NamespacedKey.minecraft("overworld");
    private static final Random RANDOM = new Random();
    private final NamespacedKey fakeOverworldKey = new NamespacedKey(this, "overworld");
    private final NamespacedKey limboWorldKey = new NamespacedKey(this, "limbo");
    private World limboWorld;

    private static @NotNull World overworld() {
        return Objects.requireNonNull(Bukkit.getWorld(REAL_OVERWORLD_KEY), "Overworld not found");
    }

    private @NotNull World fakeOverworld() {
        return Objects.requireNonNull(Bukkit.getWorld(fakeOverworldKey), "Fake overworld not found");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register i18n
        TranslationRegistry registry = TranslationRegistry.create(new NamespacedKey(this, "translations"));
        registry.defaultLocale(Locale.US);
        for (Locale locale : List.of(Locale.US)) { // TODO: reflection
            ResourceBundle bundle = ResourceBundle.getBundle("Fahare", locale, UTF8ResourceBundleControl.get());
            registry.registerAll(locale, bundle, false);
        }
        GlobalTranslator.translator().addSource(registry);

        // Create limbo world
        WorldCreator creator = new WorldCreator(limboWorldKey)
                .type(WorldType.FLAT)
                .generateStructures(false)
                .generatorSettings("{\"biome\":\"minecraft:the_end\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}]}");
        limboWorld = creator.createWorld();

        // Create fake overworld
        creator = new WorldCreator(fakeOverworldKey).copy(overworld()).seed(RANDOM.nextLong());
        creator.createWorld();

        // Teleport players to real overworld
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Location destination = fakeOverworld().getSpawnLocation();
            for (Player player : overworld().getPlayers()) {
                player.teleport(destination);
            }
        }, 1, 1);
    }

    private void deleteNextWorld(List<World> worlds) {
        // check if all worlds are deleted
        if (worlds.isEmpty()) {
            World overworld = fakeOverworld();
            Location spawn = overworld.getSpawnLocation();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(spawn);
            }
            return;
        }

        // check if worlds are ticking
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getScheduler().runTaskLater(this, () -> deleteNextWorld(worlds), 1);
            return;
        }

        // get world data
        World world = worlds.remove(0);
        String worldName = world.getName();
        Component worldKey = text(worldName);
        WorldCreator creator = new WorldCreator(worldName, world.getKey());
        creator.copy(world).seed(RANDOM.nextLong());

        // unload world
        if (Bukkit.unloadWorld(world, false)) {
            try {
                // delete world
                Path worldFolder = new File(Bukkit.getWorldContainer(), worldName).toPath();
                getComponentLogger().info(translatable("fhr.log.delete", text(worldFolder.toString())));
                IOUtils.deleteDirectory(worldFolder);

                // create new world
                creator.createWorld();
                Bukkit.getServer().sendMessage(translatable("fhr.success", worldKey));
            } catch (Exception e) {
                Component error = translatable("fhr.error", NamedTextColor.RED, worldKey);
                Audience.audience(Bukkit.getOnlinePlayers()).sendMessage(error);
                getComponentLogger().warn(error, e);
            }
        } else {
            Bukkit.getServer().sendMessage(translatable("fhr.error", NamedTextColor.RED, worldKey));
        }

        Bukkit.getScheduler().runTaskLater(this, () -> deleteNextWorld(worlds), 1);
    }

    public void reset() {
        if (limboWorld == null)
            return;
        // teleport all players to limbo
        Location destination = new Location(limboWorld, 0, 100, 0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(destination);
        }
        // check if worlds are ticking
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getScheduler().runTaskLater(this, this::reset, 1);
            return;
        }
        // unload and delete worlds
        List<World> worlds = Bukkit.getWorlds().stream()
                .filter(w -> !w.getKey().equals(limboWorldKey) && !w.getKey().equals(REAL_OVERWORLD_KEY))
                .collect(Collectors.toList());
        deleteNextWorld(worlds);
    }

    public void resetCheck() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty())
            return;
        for (Player player : players) {
            if (player.getGameMode() != GameMode.SPECTATOR)
                return;
        }
        reset();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.isCancelled())
            return;
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.spigot().respawn();
            resetCheck();
        }, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        World toWorld = to.getWorld();
        if (toWorld == null) return;
        if (!toWorld.getKey().equals(REAL_OVERWORLD_KEY)) return;

        // check if player is coming from the end, and if so just send them to spawn
        if (event.getPortalType() == PortalType.ENDER)
            event.setTo(fakeOverworld().getSpawnLocation());
        // else just update the world
        else
            to.setWorld(fakeOverworld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location to = event.getTo();
        World toWorld = to.getWorld();
        if (toWorld == null) return;
        if (!toWorld.getKey().equals(REAL_OVERWORLD_KEY)) return;

        // check if player is coming from the end, and if so just send them to spawn
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL)
            event.setTo(fakeOverworld().getSpawnLocation());
        // else just update the world
        else
            to.setWorld(fakeOverworld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getKey().equals(REAL_OVERWORLD_KEY))
            player.teleport(fakeOverworld().getSpawnLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location destination = event.getRespawnLocation();
        if (destination.getWorld().getKey().equals(REAL_OVERWORLD_KEY))
            event.setRespawnLocation(fakeOverworld().getSpawnLocation());
    }
}
