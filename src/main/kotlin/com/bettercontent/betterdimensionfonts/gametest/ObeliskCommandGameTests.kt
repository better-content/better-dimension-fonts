package com.bettercontent.betterdimensionfonts.gametest

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.PrefixGameTestTemplate

@PrefixGameTestTemplate(false)
class ObeliskCommandGameTests {
    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "obelisk_commands_spawn", timeoutTicks = 500)
    fun debug_spawn_command_creates_charged_obelisk(helper: GameTestHelper) {
        ObeliskGameTestSupport.commandDebugSpawnCreatesChargedObelisk(helper)
    }

    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "obelisk_commands", timeoutTicks = 500)
    fun cleanup_run_command_removes_active_run(helper: GameTestHelper) {
        ObeliskGameTestSupport.commandCleanupRunRemovesActiveRun(helper)
    }

    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "obelisk_commands", timeoutTicks = 800)
    fun return_command_respects_binding_state(helper: GameTestHelper) {
        ObeliskGameTestSupport.commandReturnValidatesPlayerBinding(helper)
    }
}
