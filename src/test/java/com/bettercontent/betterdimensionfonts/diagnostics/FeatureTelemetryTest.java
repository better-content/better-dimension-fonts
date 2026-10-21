package com.bettercontent.betterdimensionfonts.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class FeatureTelemetryTest {
    @Test public void nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload() {
        List<String> records = new ArrayList<>();
        PlaytestLog log = new PlaytestLog("better_dimension_fonts", new PlaytestLog.Sink() {
            public boolean enabled(String category, String level) { return true; }
            public void write(String category, String level, String message) { records.add(message); }
        }, () -> 0L, new Properties());
        var context = new PlaytestLog.Context("server", "minecraft:overworld", 42,
                "7ac1c622-f1a4-4986-9404-70bf2345061c", "");
        FeatureTelemetry.entry(log, context, java.util.UUID.fromString("341f78c8-47eb-4c02-a7ea-46a0c9081785"), false);
        if (records.size() != 1 || !records.get(0).contains("result=rejected reason=backend_rejected")
                || !records.get(0).contains("subject=7ac1c622-f1a4-4986-9404-70bf2345061c")
                || !records.get(0).contains("run=341f78c8-47eb-4c02-a7ea-46a0c9081785")
                || !records.get(0).contains("dimension=minecraft:overworld") || !records.get(0).contains("tick=42")
                || records.get(0).contains("player=") || records.get(0).contains("payload=")) {
            throw new AssertionError(records.toString());
        }
        FeatureTelemetry.rewardEjection(log, new PlaytestLog.Context("server", "minecraft:overworld", 43, "", "10,64,-3"), 3);
        if (records.size() != 2 || !records.get(1).contains("event=font.reward_ejection")
                || !records.get(1).contains("result=scheduled reason=native_ejection_schedule")
                || !records.get(1).contains("bursts=3") || !records.get(1).contains("pos=10,64,-3")
                || records.get(1).contains("item=") || records.get(1).contains("player=")) {
            throw new AssertionError(records.toString());
        }
    }
    public static void main(String[] args) { new FeatureTelemetryTest().nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload(); }
}
