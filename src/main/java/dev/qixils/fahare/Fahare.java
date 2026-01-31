package dev.qixils.fahare;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import dev.qixils.fahare.events.FahareResetEvent;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public final class Fahare extends JavaPlugin implements Listener {

    private static final NamespacedKey REAL_OVERWORLD_KEY = NamespacedKey.minecraft("overworld");
    private static final Random RANDOM = new Random();
    // todo: i18n?
    private final Permission resetPerm = new Permission("fahare.reset", "Whether to allow manually resetting the world", PermissionDefault.OP);
    private final NamespacedKey fakeOverworldKey = new NamespacedKey(this, "overworld");
    private final NamespacedKey limboWorldKey = new NamespacedKey(this, "limbo");
    private final NamespacedKey lastResetIdKey = new NamespacedKey(this, "last_reset_id");
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Set<UUID> playersPendingMountCancel = new HashSet<>();
    private int currentResetId = 0;
    private World limboWorld;
    private Path worldContainer;
    private @Nullable Path backupContainer;
    private @Nullable Path resetIdPath;
    private boolean resetting = false;
    // config
    private boolean backup = true;
    private boolean autoReset = true;
    private boolean anyDeath = false;
    private int lives = 1;
    private Difficulty forceDifficulty = null;
    private long forceSeed = 0L;

    private static @NotNull World overworld() {
        return Objects.requireNonNull(Bukkit.getWorld(REAL_OVERWORLD_KEY), "Overworld not found");
    }

    private @NotNull World fakeOverworld() {
        return Objects.requireNonNullElseGet(Bukkit.getWorld(fakeOverworldKey), this::createFakeOverworld);
    }

    private long getNewSeed() {
        if (forceSeed != 0L) return forceSeed;
        return RANDOM.nextLong();
    }

    private Difficulty getNewDifficulty() {
        if (forceDifficulty != null) return forceDifficulty;
        return overworld().getDifficulty();
    }

    private boolean getNewHardcore(Difficulty difficulty) {
        return autoReset && lives <= 1 && difficulty == Difficulty.HARD;
    }

    private @NotNull World createFakeOverworld() {
        // Create fake overworld
        Difficulty difficulty = getNewDifficulty();
        long seed = getNewSeed();
        getComponentLogger().info(translatable("fhr.log.overworld-seed", text(seed)));
        WorldCreator creator = new WorldCreator(fakeOverworldKey)
                .copy(overworld())
                .seed(seed)
                .hardcore(getNewHardcore(difficulty));

        World world = Objects.requireNonNull(creator.createWorld(), "Could not load fake overworld");
        world.setDifficulty(difficulty);

        return world;
    }

    private void saveResetId() {
        // todo: if we have to expand any further than 1 saved value then we should get a json but for now this is fine
        if (resetIdPath == null) return;

        try {
            Files.createDirectories(resetIdPath.getParent());
            Files.writeString(resetIdPath, String.valueOf(currentResetId));
        } catch (IOException e) {
            getComponentLogger().warn(translatable("fhr.log.error.save-reset-id"), e);
        }
    }

    @Override
    public void onEnable() {
        // Load config
        loadConfig();
        resetIdPath = getDataPath().resolve("current-reset-id.txt");
        try {
            if (Files.exists(resetIdPath)) {
                String idString = Files.readString(resetIdPath).trim();
                currentResetId = Integer.parseInt(idString);
            }
        } catch (Exception ignored) {
            // file probably doesn't exist yet
        }

        // Create backup folder
        worldContainer = Bukkit.getWorldContainer().toPath();
        backupContainer = worldContainer.resolve("fahare-backups");

        if (!Files.exists(backupContainer)) {
            try {
                Files.createDirectory(backupContainer);
            } catch (Exception e) {
                getComponentLogger().error(translatable("fhr.log.error.backup-folder"), e);
                backupContainer = null;
            }
        }

        // Load server properties to extract level-seed & difficulty
        Properties properties = new Properties();
        Path serverProperties = worldContainer.resolve("server.properties");
        if (Files.exists(serverProperties)) {
            try (InputStream contents = Files.newInputStream(serverProperties)) {
                properties.load(contents);
                try {
                    String levelSeed = properties.getProperty("level-seed", "");
                    forceSeed = Long.parseLong(levelSeed);
                    getComponentLogger().info(translatable("fhr.log.info.found-seed", text(forceSeed)));
                } catch (Exception e) {
                    forceSeed = 0;
                    getComponentLogger().info(translatable("fhr.log.info.missing-seed"));
                }

                String difficulty = properties.getProperty("difficulty", "");
                try {
                    forceDifficulty = Difficulty.valueOf(difficulty.toUpperCase(Locale.US));
                    getComponentLogger().warn(translatable("fhr.log.info.difficulty", text(String.valueOf(forceDifficulty))));
                } catch (Exception e) {
                    getComponentLogger().warn(translatable("fhr.log.error.difficulty", text(difficulty)));
                }
            } catch (Exception e) {
                getComponentLogger().warn(translatable("fhr.log.error.properties"), e);
            }
        }

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
        World fakeOverworld = createFakeOverworld();

        // Register commands
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            LiteralArgumentBuilder<CommandSourceStack> resetCommand = Commands.literal("fahare")
                            .then(Commands.literal("reset")
                                    .requires(Commands.restricted(source -> source.getSender().hasPermission(resetPerm)))
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendMessage(translatable("fhr.chat.resetting"));
                                        reset();
                                        return Command.SINGLE_SUCCESS;
                                    })
                            );
            commands.registrar().register(resetCommand.build());
        });

        // Register events and tasks
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, $ -> {
            // Teleport players from real overworld
            Location destination = fakeOverworld.getSpawnLocation();
            for (Player player : overworld().getPlayers()) {
                player.teleport(destination);
            }
        }, 1, 1);
    }

    private void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        var config = getConfig();
        backup = config.getBoolean("backup", backup);
        autoReset = config.getBoolean("auto-reset", autoReset);
        anyDeath = config.getBoolean("any-death", anyDeath);
        lives = Math.max(1, config.getInt("lives", lives));
    }

    public int getDeathsFor(UUID player) {
        return deaths.getOrDefault(player, 0);
    }

    public void addDeathTo(UUID player) {
        deaths.put(player, getDeathsFor(player)+1);
    }

    public boolean isDead(UUID player) {
        return getDeathsFor(player) >= lives;
    }

    public boolean isAlive(UUID player) {
        return !isDead(player);
    }

    private int getPlayerLastResetId(Player player) {
        return player.getPersistentDataContainer().getOrDefault(lastResetIdKey, PersistentDataType.INTEGER, 0);
    }

    private void setPlayerLastResetId(Player player, int resetId) {
        player.getPersistentDataContainer().set(lastResetIdKey, PersistentDataType.INTEGER, resetId);
    }

    private void resetPlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.closeInventory();
        player.clearActiveItem();
        player.setItemOnCursor(ItemStack.empty());
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.updateInventory();
        player.setLevel(0);
        player.setExp(0);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.clearActivePotionEffects();

        try {
            Iterator<Advancement> advancements = Bukkit.advancementIterator();
            while (advancements.hasNext()) {
                Advancement advancement = advancements.next();
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                // list is an unmodifiable copy so iterating is safe
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
        } catch (Exception e) {
            // idk it just feels like it would error doesn't it
            getComponentLogger().warn(translatable("fhr.log.error.advancements"), e);
        }

        setPlayerLastResetId(player, currentResetId);
    }

    private void deleteNextWorld(List<World> worlds, @Nullable Path backupDestination) {
        // check if all worlds are deleted
        if (worlds.isEmpty()) {
            World overworld = fakeOverworld();
            Location spawn = overworld.getSpawnLocation();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(spawn);
            }

            try {
                new FahareResetEvent(overworld).callEvent();
            } catch (Exception e) {
                getComponentLogger().error(translatable("fhr.log.error.reset-event"), e);
            }

            resetting = false;
            return;
        }

        // check if worlds are ticking
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(this, $ -> deleteNextWorld(worlds, backupDestination), 1);
            return;
        }

        // get world data
        World world = worlds.remove(0);
        String worldName = world.getName();
        Component worldKey = text(worldName);
        WorldCreator creator = new WorldCreator(worldName, world.getKey());

        Difficulty difficulty = getNewDifficulty();
        long seed = getNewSeed();
        getComponentLogger().info(translatable("fhr.log.seed", worldKey, text(seed)));

        creator
                .copy(world)
                .seed(seed)
                .hardcore(getNewHardcore(difficulty));

        // unload world
        if (Bukkit.unloadWorld(world, backup)) {
            try {
                Path worldFolder = worldContainer.resolve(worldName);
                Component arg = text(worldFolder.toString());
                if (backupDestination != null) {
                    // Backup world
                    getComponentLogger().info(translatable("fhr.log.info.backup", arg));
                    Files.move(worldFolder, backupDestination.resolve(worldName));
                } else {
                    // Delete world
                    getComponentLogger().info(translatable("fhr.log.info.delete", arg));
                    IOUtils.deleteDirectory(worldFolder);
                }

                // create new world
                World newWorld = creator.createWorld();
                if (newWorld == null) throw new IllegalStateException("World was null");

                world.setDifficulty(difficulty);

                Bukkit.getServer().sendMessage(translatable("fhr.chat.success", worldKey));
            } catch (Exception e) {
                Component error = translatable("fhr.chat.error", NamedTextColor.RED, worldKey);
                Audience.audience(Bukkit.getOnlinePlayers()).sendMessage(error);
                getComponentLogger().warn(error, e);
            }
        } else {
            Bukkit.getServer().sendMessage(translatable("fhr.chat.error", NamedTextColor.RED, worldKey));
        }

        Bukkit.getGlobalRegionScheduler().runDelayed(this, $ -> deleteNextWorld(worlds, backupDestination), 1);
    }

    public synchronized void reset() {
        if (resetting)
            return;
        if (limboWorld == null)
            return;
        deaths.clear();

        // Update and save current reset ID to keep track of players that were offline during the reset
        currentResetId++;
        saveResetId();

        // teleport all players to limbo
        Location destination = new Location(limboWorld, 0, 100, 0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(destination);
        }
        // check if worlds are ticking
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(this, $ -> reset(), 1);
            return;
        }
        resetting = true;
        // calculate backup folder
        Path backupDestination = null;
        if (backup && backupContainer != null) {
            String baseName = ISO_LOCAL_DATE.format(LocalDate.now());
            int attempt = 1;
            do {
                String name = baseName + '-' + attempt++;
                backupDestination = backupContainer.resolve(name);
            } while (Files.exists(backupDestination));
            try {
                Files.createDirectory(backupDestination);
            } catch (Exception e) {
                getComponentLogger().error(translatable("fhr.log.error.backup-subfolder", text(backupDestination.toString())), e);
                backupDestination = null;
            }
        }
        // unload and delete worlds
        List<World> worlds = Bukkit.getWorlds().stream()
                .filter(w -> !w.getKey().equals(limboWorldKey) && !w.getKey().equals(REAL_OVERWORLD_KEY))
                .collect(Collectors.toList());
        deleteNextWorld(worlds, backupDestination);
    }

    public void resetCheck(boolean death) {
        if (!autoReset)
            return;
        if (anyDeath && death) {
            reset();
            return;
        }
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty())
            return;
        for (Player player : players) {
            if (isAlive(player.getUniqueId()))
                return;
        }
        reset();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        addDeathTo(player.getUniqueId());
        if (isAlive(player.getUniqueId()))
            return;
        Bukkit.getRegionScheduler().runDelayed(this, player.getLocation(), $ -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.spigot().respawn();
            resetCheck(true);
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

        // Check if player missed a reset
        int playerLastReset = getPlayerLastResetId(player);
        if (playerLastReset < currentResetId) {
            // Player was offline during reset, apply reset procedure
            resetPlayer(player);
            player.teleport(fakeOverworld().getSpawnLocation());

            // Store the player's UID for one tick to stop the potential EntityMountEvent
            playersPendingMountCancel.add(player.getUniqueId());
            Bukkit.getGlobalRegionScheduler().runDelayed(
                    this,
                    $ -> playersPendingMountCancel.remove(player.getUniqueId()),
                    1L
            );
        } else if (player.getWorld().getKey().equals(REAL_OVERWORLD_KEY)) {
            // Normal case: redirect from real overworld to fake overworld
            player.teleport(fakeOverworld().getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityMount(EntityMountEvent event) {
        if (playersPendingMountCancel.contains(event.getEntity().getUniqueId())) {
            // Player is re-mounting a previously mounted entity from a reset world, cancel and remove it
            // This causes a vanilla log message "Couldn't reattach entity to player", but it is harmless
            event.setCancelled(true);
            event.getMount().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location destination = event.getRespawnLocation();
        if (destination.getWorld().getKey().equals(REAL_OVERWORLD_KEY))
            event.setRespawnLocation(fakeOverworld().getSpawnLocation());
    }
}
