# Native Font verification

The repository provides `verifyFast` for deterministic checks and `verifyFull` for those checks plus all production headless GameTests. The existing focused activation profile can be selected with:

```sh
TMPDIR="$HOME/.tmp" BC_CUSTOM_MOD_JAR_DIR=/path/to/verified/providers \
  ./gradlew headlessGameTest -PdimensionDrinkGameTestSelection=activation --max-workers=2
```

Use Java 17 and point `BC_CUSTOM_MOD_JAR_DIR` at the required verified provider JARs. The activation profile calls the placed `ObeliskBlock.use` interaction. It checks held-item pass-through, crouched empty-hand activation at the origin, and the return seal's held-item, crouch, normal-use, and repeat-use behavior. The fixture also checks run identity and cleanup. This headless server fixture does not establish client rendering or pack geometry. These are available owner commands; this note records no execution result.

`verifyFull` requires the `all` GameTest selection; `verifySmoke` requires `smoke`. The build rejects another explicit selection for either task. Run focused `headlessGameTest` separately.
