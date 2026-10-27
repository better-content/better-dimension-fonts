# Native Font verification

`./gradlew verifyFast` runs deterministic checks; `./gradlew verifyFull` additionally runs all production headless GameTests. For the focused existing activation profile:

```sh
export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.17-tem"
export PATH="$JAVA_HOME/bin:$PATH"
export TMPDIR="$HOME/.tmp"
export BC_CUSTOM_MOD_JAR_DIR=/path/to/explicit/verified/providers
flock "$HOME/.tmp/better-content-gradle.lock" ./gradlew headlessGameTest -PdimensionDrinkGameTestSelection=activation --max-workers=2
```

The charged activation round-trip test calls the actual placed `ObeliskBlock.use` boundary. It checks that a held stick passes through without consuming the item, debiting Font charge, or creating a run; an empty hand can activate even while crouched at the origin. At the destination, a placed return seal rejects held-item and crouched use, accepts normal empty-hand use, emits exactly one return event, and safely handles another use after the assignment is gone. The existing fixture still validates destination/run identity and final cleanup.

These use the existing headless player and no-op transport fixture. They prove server interaction admission and lifecycle behavior, not real-client rendering, input dispatch, or pack-wide dimension geometry. Existing authorization, session, persistence, multiplayer, and target tests remain owned by their existing profiles; no pack geometry tour or warning/TPS quota is imported.
