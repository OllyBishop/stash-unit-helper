package io.github.ollybishop.stashunithelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup( StashUnitHelperConfig.GROUP )
public interface StashUnitHelperConfig extends Config
{
    String GROUP = "stash-unit-helper-diagnostics";

    @ConfigItem(
            keyName = "capture",
            name = "Capture diagnostics",
            description = "Toggle on to capture for 120 seconds; toggle off to stop. Search either supported STASH to start automatically." )
    default boolean isCaptureEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "variableChanges",
            name = "Capture variable changes",
            description = "Include up to 80 varbit/varp changes per capture. These are raw candidates, not verified STASH state." )
    default boolean isVariableCaptureEnabled()
    {
        return false;
    }
}
