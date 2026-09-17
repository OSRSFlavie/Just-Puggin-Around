package com.osrsflavie.justpugginaround;

import com.google.inject.Provides;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Just Puggin' Around",
        description = "Plays tired sounds after travelling a configurable distance with a follower"
)
public class JustPugginAroundPlugin extends Plugin
{
    private static final int STOPPING_TICKS = 2;
    private static final String PUG_TIRED_SOUND = "/pug_tired.wav";
    private static final String PUPPY_TIRED_SOUND = "/puppy_tired.wav";

    private static final Logger log = LoggerFactory.getLogger(JustPugginAroundPlugin.class);

    @Inject
    private Client client;

    @Inject
    private JustPugginAroundConfig config;

    @Inject
    private AudioPlayer audioPlayer;

    @Inject
    private ScheduledExecutorService executorService;

    private WorldPoint previousLocation;
    private int tilesTravelled;
    private int stoppedTicks;
    private boolean wasMoving;

    @Override
    protected void startUp()
    {
        resetTracking();

        log.info("Just Puggin' Around started");
    }

    @Override
    protected void shutDown()
    {
        resetTracking();

        log.info("Just Puggin' Around stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            resetTracking();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            resetTracking();
            return;
        }

        if (client.getLocalPlayer() == null)
        {
            resetTracking();
            return;
        }

        NPC follower = client.getFollower();

        if (!hasQualifyingFollower(follower))
        {
            resetTracking();
            previousLocation = client.getLocalPlayer().getWorldLocation();
            return;
        }

        WorldPoint currentLocation = client.getLocalPlayer().getWorldLocation();

        if (previousLocation == null)
        {
            previousLocation = currentLocation;
            return;
        }

        boolean moved = !currentLocation.equals(previousLocation);

        if (moved)
        {
            handleMovement(currentLocation);
        }
        else
        {
            handleStopped(follower);
        }

        previousLocation = currentLocation;
    }

    private void handleMovement(WorldPoint currentLocation)
    {
        int distance = previousLocation.distanceTo(currentLocation);

        if (distance > 0)
        {
            tilesTravelled += distance;
        }

        stoppedTicks = 0;
        wasMoving = true;
    }

    private void handleStopped(NPC follower)
    {
        if (wasMoving)
        {
            stoppedTicks = 1;
            wasMoving = false;
            return;
        }

        if (stoppedTicks > 0)
        {
            stoppedTicks++;
        }
        else
        {
            return;
        }

        if (stoppedTicks >= STOPPING_TICKS)
        {
            finishStoppedRun(follower);
        }
    }

    private void finishStoppedRun(NPC follower)
    {
        int threshold = config.distanceThreshold();

        /*
         * When All Followers Tire is enabled, any detected follower
         * can trigger the tired sound after the configured distance.
         *
         * When disabled, Pugs retain the original requirement of
         * being adjacent to the player when the run ends.
         */
        boolean canTrigger = config.allFollowers()
                || isFollowerNextToPlayer(follower);

        if (tilesTravelled >= threshold && canTrigger)
        {
            log.info("Tired sound triggered after {} tiles", tilesTravelled);
            playTiredSoundWithChance();
        }

        tilesTravelled = 0;
        stoppedTicks = 0;
        wasMoving = false;
    }

    private boolean hasQualifyingFollower(NPC follower)
    {
        if (follower == null)
        {
            return false;
        }

        /*
         * All Followers Tire overrides the Pug-only name check.
         */
        if (config.allFollowers())
        {
            return true;
        }

        return follower.getName() != null
                && follower.getName().equalsIgnoreCase("Pug");
    }

    private boolean isFollowerNextToPlayer(NPC follower)
    {
        if (follower == null || client.getLocalPlayer() == null)
        {
            return false;
        }

        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
        WorldPoint followerLocation = follower.getWorldLocation();

        if (playerLocation == null || followerLocation == null)
        {
            return false;
        }

        return playerLocation.distanceTo(followerLocation) <= 1;
    }

    /**
     * Performs the 1/100 rare-sound roll.
     * 1/100: puppy_tired.wav
     * 99/100: pug_tired.wav
     */
    private void playTiredSoundWithChance()
    {
        if (ThreadLocalRandom.current().nextInt(100) == 0)
        {
            log.info("Rare tired sound triggered: {}", PUPPY_TIRED_SOUND);
            playTiredSound(PUPPY_TIRED_SOUND);
        }
        else
        {
            playTiredSound(PUG_TIRED_SOUND);
        }
    }

    /**
     * Plays a bundled audio resource using RuneLite's audio manager.
     */
    private void playTiredSound(String soundResource)
    {
        int volume = config.volume();

        if (volume <= 0)
        {
            return;
        }

        float gain = (float) (20.0 * Math.log10(volume / 100.0));

        executorService.execute(() ->
        {
            try
            {
                audioPlayer.play(JustPugginAroundPlugin.class, soundResource, gain);
            }
            catch (Exception ex)
            {
                log.warn("Unable to play {}", soundResource, ex);
            }
        });
    }

    private void resetTracking()
    {
        previousLocation = null;
        tilesTravelled = 0;
        stoppedTicks = 0;
        wasMoving = false;
    }

    @Provides
    JustPugginAroundConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(JustPugginAroundConfig.class);
    }
}