package com.osrsflavie.justpugginaround;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("justpugginaround")
public interface JustPugginAroundConfig extends Config
{
    @ConfigItem(
            keyName = "allPetsTireLikePugs",
            name = "All Pets Tire Like Pugs",
            description = "Makes every pet use the Pug tired sounds",
            position = 0
    )
    default boolean allPetsTireLikePugs()
    {
        return false;
    }

    @ConfigItem(
            keyName = "distanceThreshold",
            name = "Distance Before Tiring",
            description = "Number of tiles before your pet tires",
            position = 1
    )
    default int distanceThreshold()
    {
        return 25;
    }

    @Range(
            min = 0,
            max = 100
    )
    @Units(Units.PERCENT)
    @ConfigItem(
            keyName = "volume",
            name = "Volume",
            description = "Volume",
            position = 2
    )
    default int volume()
    {
        return 10;
    }
}
