package com.bettercontent.betterdimensionfonts.api

sealed interface RunBeginResult {
    data class Accepted(val run: RunHandle) : RunBeginResult
    data class Rejected(val reason: String) : RunBeginResult
}
