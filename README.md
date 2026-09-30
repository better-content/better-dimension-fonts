# Better Dimension Fonts

Pack-owned obelisk and charge-font worldgen/runtime mod for Forge `1.20.1`.

## Common commands

```bash
./gradlew verifyFast
./gradlew verifyFull
./gradlew verifySmoke
```

`verifyFast` runs the JVM verification lane. `verifyFull` adds **all production
GameTests** (48 at this revision). `verifySmoke` adds only the 11 lifecycle smoke
tests. `./gradlew headlessGameTest -PdimensionDrinkGameTestSelection=runtime` selects a focused
profile; supported selectors are `all`, `smoke`, `run`, `activation`, `rewards`,
`void`, `data`, `template`, `multiplayer`, `commands`, and `runtime`. Unknown selectors
and overrides conflicting with `verifyFull` or `verifySmoke` fail.

Each GameTest invocation retains its generated world, logs, and `execution.json` under
`build/gametest/<run-id>/`. The gate compares actual runtime registration and
successful completion against the reviewed IDs in `gametest/profiles/`, rejects
missing or incomplete evidence, and records the selected profile and run ID.
Inspect failed evidence before manually removing its fixture. A later run uses a
new fixture; it does not delete a previous failed world. Runtime charge scenarios
use deterministic modifier fixtures and separate batches; worldgen scenarios
prepare their terrain and worldgen heightmaps explicitly. `verifyFast` also runs
12 negative execution-evidence checks without starting Forge.

## Font generation and discovery

Better Dimension Fonts bundles the Aether, Bumblezone, Nether, and Ratlantis Font definitions at equal default `worldgenWeight`. A weight controls deterministic selection at an eligible structure start; it does not guarantee equal visible counts in a finite explored area. Terrain rejection, exploration history, destroyed Fonts, and map sales are measured separately from configured probability.

Layout and definition selection use independent deterministic seed domains, so terrain opportunity cannot systematically favor a Font type. Custom JSON definitions remain supported when their weights are positive and finite. The effective normalized weights are logged on reload and available with the permission-level-2 `/font audit` command.

Use the permission-level-2 `/font find` command to list one indexed natural Font for each enabled worldgen definition. Found coordinates are clickable and teleport the operator to the site. The command only reads locations recorded as generated chunks load; it does not search or generate remote terrain. A missing type means more terrain must be explored before that world's generated Fonts can be fully checked.

Naturally generated Fonts are added to a saved discovery index as their chunks load. Wandering-trader Font maps use only that index and never locate or generate remote structure chunks; maps are marker-only until players explore their terrain. Harvesting a bound Font drops an unbound Font: it can be replanted as a World Condenser base but cannot start another expedition. Harvesting during an active run is blocked.

New Overworld Font sites use a circular oxidized-copper court without water pools, candles, and biome-matched stripped-log supports only where trees grow. Bumblezone sites include dense wax and hive clusters. Ratlantis sites use varied marbled-cheese masonry and small decorations instead of Automaton Heads. Font maps display their destination's salience aspects; themed spirit sellers offer only matching surveyed destinations. Underwater return Fonts preserve the water and use four soul-sand corners as bubble shafts to the surface.

Vanilla plains, desert, savanna, snowy, and taiga villages can include a Font shrine. The shrine joins their house pools with a street-facing entrance. Its selection rate targets roughly one shrine in five newly generated villages when a village makes twelve house placement attempts; actual rates vary with village layout and available space.

The craftable Font Pourer sits two blocks above a Font, leaving one air block for a visible pour. It accepts the matching Nether, Aether, Bumblezone, or Ratlantis Libation by bucket or fluid automation and restores charge only while a run is active. One bucket supplies 48,000 charge, about ten extra minutes at the current 80 charge per second drain; a nominal 15,000-charge Font lasts roughly three minutes before entry costs. The Font itself has no fluid capability.

These contracts are new-world-only. Existing copied configuration, historical structures, and saves are not migrated or scanned; remove old Font configuration and create a new world when validating the new distribution.

`FontAggregateReturnEvent` posts once for every living participant actually transported back to
the origin, including a voluntary return, charge-expiry extraction, and a Font-bound Aether
fall-out. It is factual extraction evidence rather than a challenge reward. Final death, logout
without transport, rejected transport, and server-stop cleanup do not emit it. Aether departure
handling applies only while the player is bound to an Aether Font session. When Better Content
Threads is present, the optional bridge reuses the active `the_end_is_not_a_door` correlation token
to emit `font_route_completed=returned`.

## Release artifact

Use the staged reobfuscated runtime jar for pack deployment:

- `build/libs/better-dimension-fonts-<version>.jar`

`stageRuntimeJar` copies `build/reobfJar/output.jar` onto that canonical release path so pack deployment does not need a repo-specific rename rule.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and Gradle project: `better-dimension-fonts`
- Mod ID and resource namespace: `better_dimension_fonts`
- Maven group: `com.bettercontent`
- Runtime artifact: `build/libs/better-dimension-fonts-<version>.jar`

The canonical identity is a clean break. Legacy mod IDs, resource namespaces, configuration paths, commands, network channels, and saved-data keys are not migrated or aliased.
