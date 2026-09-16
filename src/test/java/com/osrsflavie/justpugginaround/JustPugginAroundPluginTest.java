package com.osrsflavie.justpugginaround;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class JustPugginAroundPluginTest
{
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        /*
         * Load the plugin into the RuneLite development client
         * before RuneLite itself starts.
         */
        ExternalPluginManager.loadBuiltin(
                JustPugginAroundPlugin.class
        );

        /*
         * Start the RuneLite development client.
         */
        RuneLite.main(args);
    }
}