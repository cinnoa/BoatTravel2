# BoatTravel — Same-World Edition

Paper 1.20.4 plugin that adds an immersive same-world ferry network. Players right-click a **voyage sign**, get a one-tap confirmation prompt, and are then carried smoothly along a pre-defined waypoint route inside a cosmetic boat — they can free-look around the whole way but cannot control the boat. On arrival they're dismounted at the destination dock and charged via Vault.

## Build

The repo ships with a GitHub Actions workflow at `.github/workflows/build.yml`. Push to GitHub and a CI run produces `BoatTravel-SameWorld.jar` as an artifact. To build locally:

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
mvn -U clean package
```

The jar lands at `target/BoatTravel-SameWorld.jar`. Drop it into your server's `plugins/` directory.

## Movement model (v12)

The previous versions struggled to find a movement model that was both *smooth* and *non-controllable*. v12 uses the following approach:

- An **invisible marker armor stand** is the seat. The player rides it as a passenger.
- A **cosmetic boat** is spawned alongside, gravity-free, and teleport-followed each tick for visuals.
- The plugin teleports the **seat** each tick along the path. Minecraft automatically carries the rider with the seat's movement, which gives smooth interpolated motion on the client without ever touching the player entity directly.
- Because the player is inside a vehicle, flight-detection ignores them — no flying kicks.
- Because the plugin never overrides the player's yaw/pitch, the player keeps full free-look the entire ride.
- Because the player isn't driving the seat (the server is teleporting it), they have zero ability to influence speed or direction.

## Commands

```
/bt reload
/bt cancel
/bt docks
/bt info <dock>
/bt sethome <dock>
/bt route create <name> <origin> <destination>
/bt route addpoint <name>
/bt route removelast <name>
/bt route boattype <name> <type>
/bt route enable|disable|delete|info <name>
/bt route list
/bt stats [player <name> | top <stat>]
/bt music <toggle|on|off>
```

## Signs

- `[Dock]` on line 1, dock name on line 2 → creates a dock sign. Then stand in the water and `/bt sethome <name>`.
- `[Voyage]` on line 1, existing route name on line 2 → creates a voyage sign that points at that route. The sign renders `✦ % Voyage % ✦`, the destination dock, town (if Towny is installed), and the fare. Breaking the sign removes the sign only — the underlying route is preserved.

## Configuration

`config.yml` controls movement mechanics (speed multiplier, particle/sound cadence, chunk pre-loading) and contains a full `messages:` tree that defines every color, decoration, and glyph used by chat messages, action bars, titles, and signs. Hex or named colors are accepted; decoration lists accept `BOLD`, `ITALIC`, `UNDERLINED`, `STRIKETHROUGH`, `OBFUSCATED`.

## Soft dependencies

- **Vault** — economy (cost-per-block + flat fee). Without it, voyages are free.
- **Towny** — populates the town line on signs.
- **PlaceholderAPI** — exposes `%boattravel_stat_*%`, `%boattravel_global_*%`, `%boattravel_top_*_*%`, `%boattravel_topvalue_*_*%`.
