package dev.bekololek.boattravel.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-flight state for a voyage. The {@link dev.bekololek.boattravel.managers.VoyageManager}
 * owns the lifecycle; this class is a pure container.
 *
 * Movement model in v12:
 *  - {@code seat}    = invisible marker armor stand the player rides. The
 *                       seat is moved by per-tick teleport along the path and
 *                       Minecraft automatically carries the rider.
 *  - {@code visualBoat} = a cosmetic, gravity-free Boat entity teleport-followed
 *                       alongside the seat so spectators (and the rider in F5)
 *                       see a real boat gliding across the water.
 */
public class Voyage {

    private final UUID playerId;
    private final Route route;
    private final Dock origin;
    private final Dock destination;
    private final ArmorStand seat;
    private final Boat visualBoat;
    private final double totalDistance;
    private final double cost;
    private final Set<Chunk> forcedChunks = new HashSet<>();

    private BukkitTask movementTask;
    private BukkitTask audioTask;
    private BukkitTask particleTask;

    private boolean ended;
    private boolean arrived;
    private boolean charged;
    private boolean refundOnFailure = true;
    private boolean midTripHornPlayed;

    private int currentSegmentIndex = 0;
    private double segmentProgress = 0.0;
    private double distanceTravelled = 0.0;
    private long settleStartTick = -1L;

    // Original player state captured at voyage start, restored at end.
    private final float originalWalkSpeed;
    private final float originalFlySpeed;
    private final boolean originalAllowFlight;
    private final boolean originalFlying;
    private final boolean originalInvulnerable;
    private final boolean originalCollidable;

    public Voyage(UUID playerId,
                  Route route,
                  Dock origin,
                  Dock destination,
                  ArmorStand seat,
                  Boat visualBoat,
                  double totalDistance,
                  double cost,
                  float originalWalkSpeed,
                  float originalFlySpeed,
                  boolean originalAllowFlight,
                  boolean originalFlying,
                  boolean originalInvulnerable,
                  boolean originalCollidable) {
        this.playerId = playerId;
        this.route = route;
        this.origin = origin;
        this.destination = destination;
        this.seat = seat;
        this.visualBoat = visualBoat;
        this.totalDistance = totalDistance;
        this.cost = cost;
        this.originalWalkSpeed = originalWalkSpeed;
        this.originalFlySpeed = originalFlySpeed;
        this.originalAllowFlight = originalAllowFlight;
        this.originalFlying = originalFlying;
        this.originalInvulnerable = originalInvulnerable;
        this.originalCollidable = originalCollidable;
    }

    public UUID getPlayerId() { return playerId; }
    public Route getRoute() { return route; }
    public Dock getOrigin() { return origin; }
    public Dock getDestination() { return destination; }
    public ArmorStand getSeat() { return seat; }
    public Boat getVisualBoat() { return visualBoat; }
    public double getTotalDistance() { return totalDistance; }
    public double getCost() { return cost; }

    public int getCurrentSegmentIndex() { return currentSegmentIndex; }
    public void setCurrentSegmentIndex(int v) { this.currentSegmentIndex = v; }
    public double getSegmentProgress() { return segmentProgress; }
    public void setSegmentProgress(double v) { this.segmentProgress = v; }
    public double getDistanceTravelled() { return distanceTravelled; }
    public void addDistanceTravelled(double amt) { this.distanceTravelled += amt; }

    public boolean isEnded() { return ended; }
    public void setEnded(boolean v) { this.ended = v; }
    public boolean isArrived() { return arrived; }
    public void setArrived(boolean v) { this.arrived = v; }
    public boolean isCharged() { return charged; }
    public void setCharged(boolean v) { this.charged = v; }
    public boolean isRefundOnFailure() { return refundOnFailure; }
    public void setRefundOnFailure(boolean v) { this.refundOnFailure = v; }
    public boolean isMidTripHornPlayed() { return midTripHornPlayed; }
    public void setMidTripHornPlayed(boolean v) { this.midTripHornPlayed = v; }
    public long getSettleStartTick() { return settleStartTick; }
    public void setSettleStartTick(long t) { this.settleStartTick = t; }

    public Set<Chunk> getForcedChunks() { return forcedChunks; }

    public BukkitTask getMovementTask() { return movementTask; }
    public void setMovementTask(BukkitTask t) { this.movementTask = t; }
    public BukkitTask getAudioTask() { return audioTask; }
    public void setAudioTask(BukkitTask t) { this.audioTask = t; }
    public BukkitTask getParticleTask() { return particleTask; }
    public void setParticleTask(BukkitTask t) { this.particleTask = t; }

    public float getOriginalWalkSpeed() { return originalWalkSpeed; }
    public float getOriginalFlySpeed() { return originalFlySpeed; }
    public boolean isOriginalAllowFlight() { return originalAllowFlight; }
    public boolean isOriginalFlying() { return originalFlying; }
    public boolean isOriginalInvulnerable() { return originalInvulnerable; }
    public boolean isOriginalCollidable() { return originalCollidable; }

    public double distanceRemaining() {
        return Math.max(0.0, totalDistance - distanceTravelled);
    }

    public double progress() {
        if (totalDistance <= 0.0001) return 1.0;
        return Math.min(1.0, Math.max(0.0, distanceTravelled / totalDistance));
    }

    public void cancelTasks() {
        if (movementTask != null) { try { movementTask.cancel(); } catch (Throwable ignored) {} movementTask = null; }
        if (audioTask != null) { try { audioTask.cancel(); } catch (Throwable ignored) {} audioTask = null; }
        if (particleTask != null) { try { particleTask.cancel(); } catch (Throwable ignored) {} particleTask = null; }
    }

    public void releaseChunks() {
        for (Chunk chunk : forcedChunks) {
            try {
                chunk.setForceLoaded(false);
            } catch (Exception ignored) {
            }
        }
        forcedChunks.clear();
    }

    public void removeEntities() {
        if (seat != null && seat.isValid()) {
            try { seat.remove(); } catch (Throwable ignored) {}
        }
        if (visualBoat != null && visualBoat.isValid()) {
            try { visualBoat.remove(); } catch (Throwable ignored) {}
        }
    }

    public Location currentLocation() {
        if (seat != null && seat.isValid()) return seat.getLocation();
        return null;
    }
}
