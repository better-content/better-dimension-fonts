package com.bettercontent.betterdimensionfonts.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void rewardEjection(PlaytestLog log, PlaytestLog.Context context, int bursts) {
        if (log.enabled("rewards")) log.debug("rewards", "font.reward_ejection", context, "scheduled", "native_ejection_schedule",
                () -> PlaytestLog.fields("bursts", bursts));
    }
    public static void entry(PlaytestLog log, PlaytestLog.Context context, java.util.UUID runId, boolean entered) {
        log.debug("lifecycle", "font.entry", context, entered ? "entered" : "rejected", entered ? "charged_and_bound" : "backend_rejected", () -> PlaytestLog.fields("run", runId));
    }
}
