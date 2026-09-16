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
            keyName = "distanceThreshold",
            name = "Distance Before Tiring",
            description = "Number of tiles before your follower tires",
            position = 0
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
            position = 1
    )
    default int volume()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "allFollowers",
            name = "All Followers Tire",
            description = "Allow followers tire like pugs",
            position = 2
    )
    default boolean allFollowers()
    {
        return false;
    }
}