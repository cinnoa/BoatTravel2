package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.model.Dock;
import dev.bekololek.boattravel.model.Route;
import dev.bekololek.boattravel.model.Voyage;
import dev.bekololek.boattravel.utils.MessageConfig;
import dev.bekololek.boattravel.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The immersive same-world voyage engine.
 *
 * MOVEMENT MODEL (the part previous versions struggled with):
 *
 *  The player is mounted on an INVISIBLE MARKER ARMOR STAND that we move along
 *  the path. Each tick we advance an internal path cursor by a fixed distance
 *  derived from the configured speed, then teleport the armor stand to that
 *  point. Minecraft automatically carries any passenger entity along with the
 *  armor stand, so the player rides smoothly without any client-side input.
 *
 *  Crucially:
 *   - The seat is a MARKER armor stand (no hitbox, no rendering). It cannot be
 *     interacted with, blocked, or pushed.
 *   - Teleporting an armor stand with a passenger does NOT overwrite the
 *     passenger's yaw/pitch, so the rider keeps full mouse-look freedom.
 *   - Because the player is "in a vehicle", Paper's flight-kick logic ignores
 *     them while they ride.
 *   - The cosmetic boat is a separate entity that is teleport-followed in
 *     parallel — purely visual, never the source of motion. Velocity is set to
 *     zero each tick to prevent any drift.
 *
 *  Why this works where v8-v11 didn't:
 *   - v8 mounted the player on the boat. Boats receive movement input from the
 *     riding client every tick, overwriting server-side velocity. → boat sat
 *     still while progress bar advanced.
 *   - v10 force-teleported the PLAYER every tick, which also overwrote the
 *     player's yaw/pitch → locked camera, motion sickness.
 *   - v11 mounted the player on an armor stand but tried setVelocity() to move
 *     it. ArmorStands ignore velocity unless gravity is enabled and they have
 *     no driver — they only move reliably via teleport.
 *
 *  Sounds and particles run on their own scheduled tasks at much lower
 *  frequencies than the movement loop, so they cannot spam regardless of how
 *  fast the boat is moving.
 */
public class VoyageManager {

    private final Main plugin;
    private final RouteManager routeManager;
    private final DockManager dockManager;
    private final TravelSignManager travelSignManager;
    private final EconomyManager economyManager;
    private final StatsManager statsManager;

    private final Map<UUID, Voyage> active = new HashMap<>();
    private final Set<UUID> bypassTeleport = new HashSet<>();
    private final Map<UUID, PendingReturn> pendingReturns = new HashMap<>();
    private final Map<UUID, Confirmation> confirmations = new HashMap<>();
    private final Set<UUID> musicDisabled = ConcurrentHashMap.newKeySet();

    /** Bukkit's walking speed in blocks/sec — the base for travel-speed-multiplier. */
    private static final double WALK_SPEED_BPS = 4.317;

    public record PendingReturn(Location location, double refundAmount, String message) {}
    private record Confirmation(String routeName, long expiresAt) {}

    public VoyageManager(Main plugin, RouteManager routeManager, DockManager dockManager,
                         TravelSignManager travelSignManager, EconomyManager economyManager,
                         StatsManager statsManager) {
        this.plugin = plugin;
        this.routeManager = routeManager;
        this.dockManager = dockManager;
        this.travelSignManager = travelSignManager;
        this.economyManager = economyManager;
        this.statsManager = statsManager;
    }

    // ── Query ────────────────────────────────────────────────────────────────

    public boolean isOnVoyage(UUID uuid) { return active.containsKey(uuid); }
    public Voyage getVoyage(UUID uuid) { return active.get(uuid); }
    public boolean isBypassing(UUID uuid) { return bypassTeleport.contains(uuid); }
    public boolean hasPendingReturn(UUID uuid) { return pendingReturns.containsKey(uuid); }
    public PendingReturn consumePendingReturn(UUID uuid) { return pendingReturns.remove(uuid); }
    public Collection<Voyage> allVoyages() { return Collections.unmodifiableCollection(active.values()); }

    // ── Confirmation flow ────────────────────────────────────────────────────

    public boolean needsConfirmation(Player player, String routeName) {
        Confirmation confirmation = confirmations.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (confirmation == null || confirmation.expiresAt() < now) return true;
        return !confirmation.routeName().equalsIgnoreCase(routeName);
    }

    public void primeConfirmation(Player player, String routeName) {
        long ms = plugin.getConfig().getLong("confirm-window-ms", 5000L);
        confirmations.put(player.getUniqueId(), new Confirmation(routeName, System.currentTimeMillis() + ms));
    }

    public void clearConfirmation(UUID uuid) { confirmations.remove(uuid); }

    // ── Music ────────────────────────────────────────────────────────────────

    public boolean isMusicEnabled(UUID uuid) { return !musicDisabled.contains(uuid); }

    public void setMusicEnabled(UUID uuid, boolean enabled) {
        if (enabled) musicDisabled.remove(uuid); else musicDisabled.add(uuid);
    }

    public boolean toggleMusic(UUID uuid) {
        if (musicDisabled.contains(uuid)) { musicDisabled.remove(uuid); return true; }
        musicDisabled.add(uuid);
        return false;
    }

    public void stopVoyageAudio(Player player) {
        if (player == null) return;
        try {
            player.stopSound(Sound.MUSIC_DISC_MALL, SoundCategory.PLAYERS);
            player.stopSound(Sound.MUSIC_DISC_BLOCKS, SoundCategory.PLAYERS);
            player.stopSound(Sound.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS);
            player.stopSound(Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.PLAYERS);
            player.stopSound(Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.PLAYERS);
            player.stopSound(Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundCategory.PLAYERS);
            player.stopSound(Sound.ENTITY_DOLPHIN_AMBIENT_WATER, SoundCategory.PLAYERS);
        } catch (NoSuchMethodError ignored) {
            player.stopSound(Sound.MUSIC_DISC_MALL);
            player.stopSound(Sound.MUSIC_DISC_BLOCKS);
            player.stopSound(Sound.ENTITY_BOAT_PADDLE_WATER);
            player.stopSound(Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE);
            player.stopSound(Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE);
        }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    public String validateRouteForPlayer(Route route) {
        if (route == null) return "That route does not exist.";
        if (!routeManager.isValid(route, dockManager)) return "That voyage is currently unavailable.";
        Dock origin = dockManager.getByName(route.getOriginDock());
        Dock destination = dockManager.getByName(route.getDestinationDock());
        if (origin == null || destination == null) return "That voyage is currently unavailable.";
        Location spawn = route.getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) return "That voyage is currently unavailable.";
        if (origin.getHomeLocation() == null || destination.getHomeLocation() == null)
            return "That voyage is currently unavailable.";
        return null;
    }

    // ── Start ────────────────────────────────────────────────────────────────

    public boolean startVoyage(Player player, Route route) {
        String validation = validateRouteForPlayer(route);
        if (validation != null) {
            player.sendMessage(MessageUtils.error(validation));
            return false;
        }

        Dock origin = dockManager.getByName(route.getOriginDock());
        Dock destination = dockManager.getByName(route.getDestinationDock());
        double distance = route.totalDistance();
        double cost = economyManager.calculateCost(distance);
        boolean bypassCost = player.hasPermission("boattravel.bypass.cost");

        if (!bypassCost && !economyManager.canAfford(player, cost)) {
            player.sendMessage(MessageUtils.prefixed(Component.empty()
                    .append(MessageUtils.text("You need "))
                    .append(MessageUtils.var(economyManager.format(cost)))
                    .append(MessageUtils.text(" to take this voyage."))));
            return false;
        }

        Location spawn = route.getSpawnLocation();
        World world = spawn.getWorld();

        // Direction the boat should initially face — toward waypoint 1.
        Vector startDir = directionAt(route, 0);
        float startYaw = yawFromDirection(startDir);

        // Capture original player state up front so we can restore precisely.
        float origWalk = player.getWalkSpeed();
        float origFlySpeed = player.getFlySpeed();
        boolean origAllowFlight = player.getAllowFlight();
        boolean origFlying = player.isFlying();
        boolean origInvuln = player.isInvulnerable();
        boolean origCollidable = player.isCollidable();

        // Spawn the invisible marker armor stand seat at the start of the path.
        ArmorStand seat;
        Boat visualBoat;
        try {
            Location seatSpawn = spawn.clone();
            seatSpawn.setYaw(startYaw);
            seatSpawn.setPitch(0f);

            seat = world.spawn(seatSpawn, ArmorStand.class, s -> {
                s.setMarker(true);            // no hitbox; ideal for a seat
                s.setInvisible(true);
                s.setSmall(true);
                s.setBasePlate(false);
                s.setArms(false);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.setPersistent(false);
                s.setCollidable(false);
                s.setCanPickupItems(false);
            });

            // Spawn the cosmetic boat alongside the seat.
            Location boatSpawn = spawn.clone();
            boatSpawn.setYaw(startYaw);
            boatSpawn.setPitch(0f);
            boatSpawn.add(0, plugin.getConfig().getDouble("boat-visual-y-offset", -0.55D), 0);
            visualBoat = world.spawn(boatSpawn, Boat.class, b -> {
                try { b.setBoatType(route.getBoatType()); } catch (Throwable ignored) {}
                b.setGravity(false);
                b.setInvulnerable(true);
                b.setSilent(true);
                b.setPersistent(false);
                b.setCollidable(false);
                try { b.setAI(false); } catch (Throwable ignored) {}
                b.setVelocity(new Vector());
            });
        } catch (Throwable ex) {
            plugin.getLogger().warning("Failed to spawn voyage entities: " + ex.getMessage());
            player.sendMessage(MessageUtils.error("Could not start that voyage right now."));
            return false;
        }

        Voyage voyage = new Voyage(
                player.getUniqueId(), route, origin, destination, seat, visualBoat,
                distance, bypassCost ? 0.0 : cost,
                origWalk, origFlySpeed, origAllowFlight, origFlying, origInvuln, origCollidable
        );
        active.put(player.getUniqueId(), voyage);
        clearConfirmation(player.getUniqueId());

        // Player state during the ride.
        player.setInvulnerable(true);
        player.setCollidable(false);
        // We intentionally do NOT toggle flight, walk speed, or fly speed. The
        // player is a vehicle passenger; vanilla flight detection won't fire,
        // and they can't influence motion anyway since the seat is what's moved.

        // Mount the player onto the seat.
        {
            UUID id = player.getUniqueId();
            bypassTeleport.add(id);
            try {
                if (player.isInsideVehicle()) {
                    try { player.getVehicle().removePassenger(player); } catch (Throwable ignored) {}
                }
                seat.addPassenger(player);
            } finally {
                bypassTeleport.remove(id);
            }
        }

        prewarmChunks(voyage, spawn, route);

        // Cinematic intro
        showTitle(player, MessageConfig.departingTitle,
                MessageConfig.destinationSubtitlePrefix + destination.getName());
        player.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Departing for "))
                .append(MessageUtils.var(destination.getName()))
                .append(MessageUtils.text("."))));
        if (plugin.getConfig().getBoolean("departure-fireworks", true)) {
            launchDepartureFireworks(spawn, route);
        }
        if (plugin.getConfig().getBoolean("play-departure-sounds", true)) {
            playDeparture(player);
        }
        if (isMusicEnabled(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && active.containsKey(player.getUniqueId())) {
                    playSound(player, Sound.MUSIC_DISC_MALL, 0.55f, 1.0f);
                }
            }, 24L);
        }

        // Scheduling parameters
        long actionBarEvery = Math.max(1L, plugin.getConfig().getLong("actionbar-update-ticks", 4L));
        long ambienceEvery = Math.max(30L, plugin.getConfig().getLong("ambient-sound-interval-ticks", 70L));
        long particleEvery = Math.max(1L, plugin.getConfig().getLong("particle-interval-ticks", 4L));
        double speedBps = WALK_SPEED_BPS * plugin.getConfig().getDouble("travel-speed-multiplier", 6.0D);
        double distancePerTick = speedBps / 20.0D;
        double settleSeconds = plugin.getConfig().getDouble("arrival-settle-seconds", 2.0D);
        long settleTicks = (long) Math.max(0L, Math.round(settleSeconds * 20.0D));

        // ── Movement task (1 tick interval) ──────────────────────────────────
        BukkitRunnable movement = new BukkitRunnable() {
            long tickCounter = 0L;

            @Override
            public void run() {
                if (voyage.isEnded()) { cancel(); return; }
                if (!player.isOnline()) {
                    failVoyage(player.getUniqueId(),
                            "Voyage interrupted. You were returned to your origin and refunded.", true, false);
                    cancel();
                    return;
                }
                if (!seat.isValid() || !visualBoat.isValid()) {
                    failVoyage(player.getUniqueId(),
                            "Voyage interrupted. You were returned to your origin and refunded.", true, true);
                    cancel();
                    return;
                }

                // Make sure the player is still mounted (some plugins may force-dismount).
                if (!seat.getPassengers().contains(player)) {
                    UUID id = player.getUniqueId();
                    bypassTeleport.add(id);
                    try { seat.addPassenger(player); }
                    catch (Throwable ignored) {}
                    finally { bypassTeleport.remove(id); }
                }

                stepMovement(voyage, distancePerTick);

                if (tickCounter % actionBarEvery == 0L) {
                    player.sendActionBar(buildActionBar(voyage));
                }
                if (plugin.getConfig().getBoolean("play-mid-trip-moment", true)
                        && !voyage.isMidTripHornPlayed() && voyage.progress() >= 0.5D) {
                    voyage.setMidTripHornPlayed(true);
                    playMidTripMoment(player);
                }

                // Arrival settle window
                if (voyage.isArrived()) {
                    if (voyage.getSettleStartTick() < 0) {
                        voyage.setSettleStartTick(tickCounter);
                    }
                    if (tickCounter - voyage.getSettleStartTick() >= settleTicks) {
                        completeVoyage(player.getUniqueId());
                        cancel();
                        return;
                    }
                }

                tickCounter++;
            }
        };
        voyage.setMovementTask(movement.runTaskTimer(plugin, 1L, 1L));

        // ── Ambient sound task ───────────────────────────────────────────────
        BukkitRunnable audio = new BukkitRunnable() {
            @Override
            public void run() {
                if (voyage.isEnded() || !player.isOnline()) { cancel(); return; }
                playAmbient(player);
            }
        };
        voyage.setAudioTask(audio.runTaskTimer(plugin, ambienceEvery, ambienceEvery));

        // ── Particle/wake task ───────────────────────────────────────────────
        if (plugin.getConfig().getBoolean("particle-effects", true)) {
            BukkitRunnable particles = new BukkitRunnable() {
                @Override
                public void run() {
                    if (voyage.isEnded() || !player.isOnline()) { cancel(); return; }
                    Location at = visualBoat.isValid() ? visualBoat.getLocation() : seat.getLocation();
                    Vector dir = directionFor(voyage);
                    playWake(at, dir);
                }
            };
            voyage.setParticleTask(particles.runTaskTimer(plugin, particleEvery, particleEvery));
        }

        return true;
    }

    // ── Movement step ────────────────────────────────────────────────────────

    /**
     * Advance the voyage cursor by {@code distanceThisTick} blocks, then
     * teleport the seat (and via its passengers, the player) to the new path
     * location, and teleport the cosmetic boat alongside.
     */
    private void stepMovement(Voyage voyage, double distanceThisTick) {
        var points = voyage.getRoute().getWaypoints();
        if (points.size() < 2) { voyage.setArrived(true); return; }

        ArmorStand seat = voyage.getSeat();
        Boat boat = voyage.getVisualBoat();
        if (seat == null || !seat.isValid()) return;

        // Settle phase: hold position without further advancement.
        if (voyage.isArrived()) {
            Location hold = computeCurrentPathLocation(voyage);
            if (hold != null) {
                teleportSeat(seat, hold, seat.getLocation().getYaw());
                if (boat != null && boat.isValid()) {
                    Location bt = hold.clone();
                    bt.setY(hold.getY() + plugin.getConfig().getDouble("boat-visual-y-offset", -0.55D));
                    bt.setYaw(boat.getLocation().getYaw());
                    bt.setPitch(0f);
                    boat.teleport(bt);
                    boat.setVelocity(new Vector());
                }
            }
            return;
        }

        // Advance the path cursor across one or more segments if step is long enough.
        double remaining = distanceThisTick;
        while (remaining > 0.00001D) {
            int idx = voyage.getCurrentSegmentIndex();
            if (idx >= points.size() - 1) { voyage.setArrived(true); break; }

            Location a = points.get(idx).toLocation();
            Location b = points.get(idx + 1).toLocation();
            if (a == null || b == null
                    || a.getWorld() == null || b.getWorld() == null
                    || !a.getWorld().equals(b.getWorld())) {
                voyage.setArrived(true);
                break;
            }
            double segLen = b.toVector().subtract(a.toVector()).length();
            if (segLen <= 0.0001D) {
                voyage.setCurrentSegmentIndex(idx + 1);
                voyage.setSegmentProgress(0.0D);
                continue;
            }
            double travelled = voyage.getSegmentProgress() * segLen;
            double leftInSeg = segLen - travelled;
            double step = Math.min(remaining, leftInSeg);
            travelled += step;
            voyage.setSegmentProgress(travelled / segLen);
            voyage.addDistanceTravelled(step);
            remaining -= step;
            if (travelled >= segLen - 1.0E-4D) {
                voyage.setCurrentSegmentIndex(idx + 1);
                voyage.setSegmentProgress(0.0D);
            }
        }

        Location target = computeCurrentPathLocation(voyage);
        if (target == null) { voyage.setArrived(true); return; }

        Vector dir = directionFor(voyage);
        float targetYaw = yawFromDirection(dir);
        float blendedYaw = blendYaw(seat.getLocation().getYaw(), targetYaw, 0.3f);

        // Teleport the seat. The player's yaw/pitch is preserved automatically
        // because Minecraft does not override passenger rotation for armor stands.
        teleportSeat(seat, target, blendedYaw);

        // Teleport the cosmetic boat to the visual offset.
        if (boat != null && boat.isValid()) {
            Location bt = target.clone();
            bt.setY(target.getY() + plugin.getConfig().getDouble("boat-visual-y-offset", -0.55D));
            bt.setYaw(blendedYaw);
            bt.setPitch(0f);
            boat.teleport(bt);
            boat.setVelocity(new Vector());
        }

        // Light-touch chunk loading along the route
        maintainChunks(voyage, target, voyage.getRoute());
        if (plugin.getConfig().getBoolean("clear-minor-obstacles", true)) {
            clearMinorObstacles(target);
        }

        if (voyage.getCurrentSegmentIndex() >= points.size() - 1) {
            voyage.setArrived(true);
        }
    }

    /**
     * Teleport the seat armor stand to {@code target} with a chosen yaw.
     * Passengers are carried automatically by Minecraft; we don't touch them.
     */
    private void teleportSeat(ArmorStand seat, Location target, float yaw) {
        Location dst = target.clone();
        dst.setYaw(yaw);
        dst.setPitch(0f);
        try {
            seat.teleport(dst);
        } catch (Throwable ignored) {
        }
    }

    /** Linear interpolation across the current segment. */
    private Location computeCurrentPathLocation(Voyage voyage) {
        var points = voyage.getRoute().getWaypoints();
        if (points.isEmpty()) return null;
        int idx = voyage.getCurrentSegmentIndex();
        if (idx >= points.size() - 1) {
            return points.get(points.size() - 1).toLocation();
        }
        if (idx < 0) idx = 0;
        Location a = points.get(idx).toLocation();
        Location b = points.get(idx + 1).toLocation();
        if (a == null || b == null) return null;
        double t = Math.max(0.0, Math.min(1.0, voyage.getSegmentProgress()));
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        return new Location(a.getWorld(), x, y, z);
    }

    private Vector directionFor(Voyage voyage) {
        return directionAt(voyage.getRoute(), voyage.getCurrentSegmentIndex());
    }

    private Vector directionAt(Route route, int segmentIndex) {
        var points = route.getWaypoints();
        if (points.size() < 2) return new Vector(0, 0, 1);
        int idx = Math.max(0, Math.min(points.size() - 2, segmentIndex));
        Location a = points.get(idx).toLocation();
        Location b = points.get(idx + 1).toLocation();
        if (a == null || b == null) return new Vector(0, 0, 1);
        Vector v = b.toVector().subtract(a.toVector());
        if (v.lengthSquared() < 1.0E-6D) return new Vector(0, 0, 1);
        return v.normalize();
    }

    private float yawFromDirection(Vector dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    private float blendYaw(float current, float target, float amount) {
        float delta = target - current;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return current + delta * amount;
    }

    // ── Player state restoration ─────────────────────────────────────────────

    private void restorePlayerState(Player player, Voyage voyage) {
        player.setInvulnerable(voyage.isOriginalInvulnerable());
        player.setCollidable(voyage.isOriginalCollidable());

        // Don't grant flight that didn't exist before — never. Creative/Spectator
        // own flight inherently, so we respect the current gamemode.
        boolean creativeLike = player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
        boolean allowFlight = voyage.isOriginalAllowFlight() || creativeLike;
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && voyage.isOriginalFlying());
        player.setWalkSpeed(voyage.getOriginalWalkSpeed());
        player.setFlySpeed(voyage.getOriginalFlySpeed());

        player.setVelocity(new Vector());
        player.setFallDistance(0f);  // they didn't really fall
    }

    // ── End: complete / fail / cancel ────────────────────────────────────────

    public void cancelVoyage(UUID uuid) {
        Voyage v = getVoyage(uuid);
        String name = v != null && v.getOrigin() != null ? v.getOrigin().getName() : "your origin";
        failVoyage(uuid, "Voyage cancelled. Returned to " + name + ".", true, true);
    }

    public void completeVoyage(UUID uuid) {
        Voyage voyage = active.remove(uuid);
        if (voyage == null || voyage.isEnded()) return;
        voyage.setEnded(true);
        voyage.cancelTasks();
        voyage.releaseChunks();

        Player player = plugin.getServer().getPlayer(uuid);
        Location destination = voyage.getDestination().getHomeLocation();

        // Dismount the player from the seat before removing entities.
        if (player != null && voyage.getSeat() != null && voyage.getSeat().getPassengers().contains(player)) {
            bypassTeleport.add(uuid);
            try { voyage.getSeat().removePassenger(player); }
            catch (Throwable ignored) {}
            finally { bypassTeleport.remove(uuid); }
        }
        voyage.removeEntities();

        if (player != null && player.isOnline()) {
            stopVoyageAudio(player);

            // Charge on arrival per spec.
            if (voyage.getCost() > 0.0D && !player.hasPermission("boattravel.bypass.cost")) {
                if (economyManager.charge(player, voyage.getCost())) {
                    voyage.setCharged(true);
                } else {
                    player.sendMessage(MessageUtils.warn("Payment could not be processed for this voyage."));
                }
            }

            restorePlayerState(player, voyage);

            if (destination != null) {
                bypassTeleport.add(uuid);
                try {
                    Location dst = destination.clone();
                    dst.setYaw(player.getLocation().getYaw());
                    dst.setPitch(player.getLocation().getPitch());
                    player.teleport(dst, PlayerTeleportEvent.TeleportCause.PLUGIN);
                } finally {
                    bypassTeleport.remove(uuid);
                }
            }

            player.sendActionBar(Component.empty());
            showTitle(player, MessageConfig.arrivedTitle,
                    MessageConfig.destinationSubtitlePrefix + voyage.getDestination().getName());
            player.sendMessage(MessageUtils.prefixed(Component.empty()
                    .append(MessageUtils.text("Arrived at "))
                    .append(MessageUtils.var(voyage.getDestination().getName()))
                    .append(Component.text("!", MessageConfig.successAccentColor))));
            if (voyage.isCharged() && voyage.getCost() > 0.0D) {
                player.sendMessage(MessageUtils.prefixed(Component.empty()
                        .append(MessageUtils.text("You have been charged "))
                        .append(MessageUtils.var(economyManager.format(voyage.getCost())))
                        .append(MessageUtils.text(" for this voyage."))));
            }
            if (plugin.getConfig().getBoolean("play-arrival-sounds", true)) {
                playArrival(player);
            }
            statsManager.recordVoyageComplete(player,
                    voyage.getDestination().getName(),
                    voyage.getTotalDistance(),
                    voyage.getCost());
        }
    }

    public void failVoyage(UUID uuid, String playerMessage, boolean refund, boolean returnToOrigin) {
        Voyage voyage = active.remove(uuid);
        if (voyage == null || voyage.isEnded()) return;
        voyage.setEnded(true);
        voyage.cancelTasks();
        voyage.releaseChunks();

        Player player = plugin.getServer().getPlayer(uuid);
        Location origin = voyage.getOrigin().getHomeLocation();

        if (player != null && voyage.getSeat() != null && voyage.getSeat().getPassengers().contains(player)) {
            bypassTeleport.add(uuid);
            try { voyage.getSeat().removePassenger(player); }
            catch (Throwable ignored) {}
            finally { bypassTeleport.remove(uuid); }
        }
        voyage.removeEntities();

        if (player != null && player.isOnline()) {
            stopVoyageAudio(player);
            if (refund && voyage.isCharged()) {
                economyManager.refund(player, voyage.getCost());
            }
            statsManager.recordVoyageCancel(player);
            restorePlayerState(player, voyage);
            if (returnToOrigin && origin != null) {
                bypassTeleport.add(uuid);
                try {
                    Location o = origin.clone();
                    o.setYaw(player.getLocation().getYaw());
                    o.setPitch(player.getLocation().getPitch());
                    player.teleport(o, PlayerTeleportEvent.TeleportCause.PLUGIN);
                } finally {
                    bypassTeleport.remove(uuid);
                }
            }
            player.sendActionBar(Component.empty());
            player.sendMessage(MessageUtils.warn(playerMessage));
        } else if (returnToOrigin && origin != null) {
            double refundAmount = refund && voyage.isCharged() ? voyage.getCost() : 0.0D;
            pendingReturns.put(uuid, new PendingReturn(origin, refundAmount, playerMessage));
        }
    }

    public void handleDisconnect(UUID uuid) {
        failVoyage(uuid, "Voyage interrupted. You were returned to your origin and refunded.", true, true);
    }

    public void abortAll() {
        for (UUID uuid : Set.copyOf(active.keySet())) {
            failVoyage(uuid, "Voyage interrupted. You were returned to your origin and refunded.", true, true);
        }
    }

    public void deliverPendingReturn(Player player) {
        PendingReturn pending = pendingReturns.remove(player.getUniqueId());
        if (pending == null) return;
        stopVoyageAudio(player);
        if (pending.refundAmount() > 0.0D) {
            economyManager.refund(player, pending.refundAmount());
        }
        boolean creativeLike = player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setAllowFlight(creativeLike);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        bypassTeleport.add(player.getUniqueId());
        try {
            Location loc = pending.location().clone();
            loc.setYaw(player.getLocation().getYaw());
            loc.setPitch(player.getLocation().getPitch());
            player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            bypassTeleport.remove(player.getUniqueId());
        }
        player.sendMessage(MessageUtils.warn(pending.message()));
    }

    // ── Chunk management ─────────────────────────────────────────────────────

    private void prewarmChunks(Voyage voyage, Location base, Route route) {
        maintainChunks(voyage, base, route);
    }

    private void maintainChunks(Voyage voyage, Location base, Route route) {
        if (base == null || base.getWorld() == null) return;
        int radius = Math.max(1, plugin.getConfig().getInt("chunk-load-radius", 2));
        int cx = base.getChunk().getX();
        int cz = base.getChunk().getZ();
        Set<Chunk> desired = new HashSet<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                Chunk chunk = base.getWorld().getChunkAt(x, z);
                desired.add(chunk);
                if (!voyage.getForcedChunks().contains(chunk)) {
                    try { chunk.setForceLoaded(true); } catch (Throwable ignored) {}
                    voyage.getForcedChunks().add(chunk);
                }
            }
        }
        voyage.getForcedChunks().removeIf(chunk -> {
            if (desired.contains(chunk)) return false;
            try { chunk.setForceLoaded(false); } catch (Throwable ignored) {}
            return true;
        });
    }

    private void clearMinorObstacles(Location location) {
        if (location == null || location.getWorld() == null) return;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = location.getWorld().getBlockAt(x + dx, y, z + dz);
                Material type = block.getType();
                if (type == Material.LILY_PAD || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    // ── Cinematic effects ────────────────────────────────────────────────────

    private void launchDepartureFireworks(Location location, Route route) {
        if (location == null || location.getWorld() == null) return;
        Vector dir = route.getWaypoints().size() >= 2
                ? route.getWaypoints().get(1).toLocation().toVector()
                        .subtract(route.getWaypoints().get(0).toLocation().toVector()).normalize()
                : new Vector(0, 0, 1);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        int rgb;
        try { rgb = Integer.parseInt(plugin.getConfig().getString("departure-firework-color", "#4B22FF").replace("#", ""), 16); }
        catch (Exception ex) { rgb = 0x4B22FF; }
        int fadeRgb;
        try { fadeRgb = Integer.parseInt(plugin.getConfig().getString("departure-firework-fade-color", "#8A63FF").replace("#", ""), 16); }
        catch (Exception ex) { fadeRgb = 0x8A63FF; }
        final int colorRgb = rgb;
        final int fadeRgbF = fadeRgb;

        for (int i = -1; i <= 1; i += 2) {
            Location spawn = location.clone()
                    .add(dir.clone().multiply(3.0D))
                    .add(side.clone().multiply(1.1D * i))
                    .add(0, 1.25D, 0);
            try {
                Firework firework = spawn.getWorld().spawn(spawn, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.clearEffects();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL)
                        .withColor(Color.fromRGB(colorRgb))
                        .withFade(Color.fromRGB(fadeRgbF))
                        .trail(true)
                        .flicker(true)
                        .build());
                meta.setPower(0);
                firework.setFireworkMeta(meta);
                plugin.getServer().getScheduler().runTaskLater(plugin, firework::detonate, 8L + (i > 0 ? 4L : 0L));
            } catch (Throwable ignored) {
            }
        }
    }

    private void playDeparture(Player player) {
        playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 2.5f, 0.98f);
        playSound(player, Sound.BLOCK_BELL_USE, 1.9f, 0.9f);
        playSound(player, Sound.BLOCK_BELL_RESONATE, 1.6f, 1.12f);
        playSound(player, Sound.ENTITY_BOAT_PADDLE_WATER, 1.7f, 0.94f);
        playSound(player, Sound.ENTITY_DOLPHIN_SPLASH, 1.2f, 1.02f);
        playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.1f, 1.15f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            playSound(player, Sound.ENTITY_VILLAGER_CELEBRATE, 1.15f, 1.0f);
            playSound(player, Sound.ENTITY_PARROT_AMBIENT, 0.9f, 1.72f);
            playSound(player, Sound.ENTITY_DOLPHIN_AMBIENT_WATER, 0.9f, 1.0f);
        }, 8L);
    }

    private void playAmbient(Player player) {
        playSound(player, Sound.ENTITY_BOAT_PADDLE_WATER, 1.25f, 0.96f);
        playSound(player, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.55f, 1.04f);
        playSound(player, Sound.ENTITY_DOLPHIN_AMBIENT_WATER, 0.45f, 1.08f);
    }

    private void playMidTripMoment(Player player) {
        playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 1.15f, 1.05f);
        playSound(player, Sound.BLOCK_BELL_USE, 0.95f, 1.22f);
        playSound(player, Sound.ENTITY_VILLAGER_CELEBRATE, 0.8f, 1.12f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            playSound(player, Sound.ENTITY_DOLPHIN_JUMP, 0.55f, 1.05f);
            playSound(player, Sound.ENTITY_PARROT_AMBIENT, 0.45f, 1.85f);
        }, 8L);
    }

    private void playArrival(Player player) {
        playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 2.6f, 0.84f);
        playSound(player, Sound.BLOCK_BELL_RESONATE, 2.0f, 1.05f);
        playSound(player, Sound.ENTITY_VILLAGER_CELEBRATE, 1.45f, 1.0f);
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.08f);
        playSound(player, Sound.ENTITY_DOLPHIN_SPLASH, 1.15f, 1.0f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 1.5f, 1.22f);
            playSound(player, Sound.ENTITY_PARROT_AMBIENT, 0.95f, 1.72f);
            playSound(player, Sound.BLOCK_BELL_USE, 1.15f, 1.18f);
        }, 10L);
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player, sound, SoundCategory.PLAYERS, volume, pitch);
        } catch (NoSuchMethodError ignored) {
            player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private void playWake(Location location, Vector dir) {
        if (location == null || location.getWorld() == null) return;
        Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(0.42D);
        Location left = location.clone().subtract(side).add(0, 0.08D, 0);
        Location right = location.clone().add(side).add(0, 0.08D, 0);
        try {
            location.getWorld().spawnParticle(Particle.WATER_SPLASH, left, 5, 0.10, 0.04, 0.10, 0.02);
            location.getWorld().spawnParticle(Particle.WATER_SPLASH, right, 5, 0.10, 0.04, 0.10, 0.02);
            location.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, location.clone().add(0, 0.03D, 0), 3, 0.16, 0.03, 0.16, 0.01);
            location.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0, 0.14D, 0), 2, 0.10, 0.02, 0.10, 0.0);
        } catch (Throwable ignored) {
        }
    }

    // ── HUD ──────────────────────────────────────────────────────────────────

    private Component buildActionBar(Voyage voyage) {
        int remaining = (int) Math.ceil(voyage.distanceRemaining());
        int bars = Math.max(10, plugin.getConfig().getInt("actionbar-bar-length", 36));
        double progress = voyage.progress();
        int boatIndex = Math.min(bars - 1, Math.max(0, (int) Math.round(progress * (bars - 1))));

        Component boatGlyph = Component.text(MessageConfig.actionbarBoatChar, MessageConfig.actionbarBoatColor);
        for (TextDecoration d : MessageConfig.actionbarBoatDecorations) boatGlyph = boatGlyph.decorate(d);

        TextComponent.Builder bar = Component.text();
        for (int i = 0; i < bars; i++) {
            if (i == boatIndex) {
                bar.append(boatGlyph);
            } else if (i < boatIndex) {
                bar.append(Component.text(MessageConfig.actionbarFilledChar, MessageConfig.actionbarFilledColor));
            } else {
                bar.append(Component.text(MessageConfig.actionbarEmptyChar, MessageConfig.actionbarEmptyColor));
            }
        }

        Component destText = Component.text(voyage.getDestination().getName(), MessageConfig.actionbarDestinationColor);
        for (TextDecoration d : MessageConfig.actionbarDestinationDecorations) destText = destText.decorate(d);
        Component remainText = Component.text(String.valueOf(remaining), MessageConfig.actionbarRemainingColor);
        for (TextDecoration d : MessageConfig.actionbarRemainingDecorations) remainText = remainText.decorate(d);

        return Component.empty()
                .append(Component.text(MessageConfig.actionbarBracketChar + " ", MessageConfig.actionbarBracketColor))
                .append(bar.build())
                .append(Component.text(" " + MessageConfig.actionbarBracketChar + " ", MessageConfig.actionbarBracketColor))
                .append(Component.text("Sailing to ", MessageConfig.actionbarLabelColor))
                .append(destText)
                .append(Component.text(" • ", MessageConfig.actionbarBracketColor))
                .append(remainText)
                .append(Component.text(" blocks remaining", MessageConfig.actionbarLabelColor));
    }

    private void showTitle(Player player, String title, String subtitle) {
        Component titleC = Component.text(title, MessageConfig.titleColor);
        for (TextDecoration d : MessageConfig.titleDecorations) titleC = titleC.decorate(d);

        Component subC;
        String prefix = MessageConfig.destinationSubtitlePrefix;
        if (subtitle != null && subtitle.startsWith(prefix)) {
            subC = Component.text(prefix, MessageConfig.subtitleLabelColor)
                    .append(Component.text(subtitle.substring(prefix.length()), MessageConfig.subtitleValueColor));
        } else {
            subC = Component.text(subtitle == null ? "" : subtitle, MessageConfig.subtitleLabelColor);
        }
        player.showTitle(Title.title(titleC, subC,
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(550))));
    }
}
