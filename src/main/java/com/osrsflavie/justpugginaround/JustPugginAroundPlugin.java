package com.osrsflavie.justpugginaround;

import com.google.inject.Provides;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "Just Puggin' Around",
        description = "Plays tired sounds after travelling a configurable distance with a follower"
)
public class JustPugginAroundPlugin extends Plugin
{
    /**
     * Number of consecutive stationary game ticks required
     * before the run is evaluated.
     */
    private static final int STOPPING_TICKS = 2;
    private static final int FOLLOWER_ADJACENT_DISTANCE = 1;

    @Inject
    private Client client;

    @Inject
    private JustPugginAroundConfig config;

    private final AudioManager audioManager =
            new AudioManager(JustPugginAroundPlugin.class);

    /**
     * Player location from the previous game tick.
     */
    private WorldPoint previousLocation;

    /**
     * Number of tiles travelled during the current run.
     */
    private int tilesTravelled;

    /**
     * Number of consecutive stationary ticks.
     */
    private int stoppedTicks;

    /**
     * Whether the player moved during the previous tick.
     */
    private boolean wasMoving;

    @Override
    protected void startUp()
    {
        resetTracking();

        // Decode sounds once during startup so GameTick never has to do audio I/O.
        audioManager.preload(
                Sound.PUG_TIRED,
                Sound.LABRADOR_TIRED,
                Sound.PUPPY_TIRED,
                Sound.PUPPY_TIRED_RARE
        );

        log.info("Just Puggin' Around started");
    }

    @Override
    protected void shutDown()
    {
        audioManager.close();
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

        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            resetTracking();
            return;
        }

        NPC follower = client.getFollower();

        /*
         * There is nothing to track without a follower. Every pet is
         * eligible; the tired sound is gated by adjacency when the run ends.
         */
        if (follower == null)
        {
            resetTracking();

            previousLocation =
                    localPlayer.getWorldLocation();

            return;
        }

        WorldPoint currentLocation = localPlayer.getWorldLocation();

        /*
         * Establish the initial player position.
         */
        if (previousLocation == null)
        {
            previousLocation = currentLocation;
            return;
        }

        int distance = previousLocation.distanceTo(currentLocation);

        if (distance > 0)
        {
            handleMovement(distance);
        }
        else
        {
            handleStopped(localPlayer, follower);
        }

        previousLocation = currentLocation;
    }

    /**
     * Handles a tick during which the player moved.
     */
    private void handleMovement(int distance)
    {
        tilesTravelled += distance;

        /*
         * Moving cancels the stopping timer but does not
         * reset accumulated travel distance.
         */
        stoppedTicks = 0;
        wasMoving = true;
    }

    /**
     * Handles a tick during which the player did not move.
     */
    private void handleStopped(Player localPlayer, NPC follower)
    {
        /*
         * First stationary tick after movement.
         */
        if (wasMoving)
        {
            stoppedTicks = 1;
            wasMoving = false;

            log.debug(
                    "Player stopped: tick 1 of {}",
                    STOPPING_TICKS
            );

            return;
        }

        /*
         * Continue counting consecutive stationary ticks.
         */
        if (stoppedTicks > 0)
        {
            stoppedTicks++;

            log.debug(
                    "Player stopped: tick {} of {}",
                    stoppedTicks,
                    STOPPING_TICKS
            );
        }
        else
        {
            return;
        }

        /*
         * Once the stopping threshold has been reached,
         * evaluate the run.
         */
        if (stoppedTicks >= STOPPING_TICKS)
        {
            finishStoppedRun(follower, localPlayer);
        }
    }

    /**
     * Evaluates the completed stopping period.
     */
    private void finishStoppedRun(NPC follower, Player localPlayer)
    {
        int threshold = config.distanceThreshold();

        boolean petNextToPlayer =
                isFollowerNextToPlayer(localPlayer, follower);

        log.debug(
                "Stopped for {} ticks after travelling {} tiles; " +
                        "threshold is {}; pet adjacent: {}",
                stoppedTicks,
                tilesTravelled,
                threshold,
                petNextToPlayer
        );

        /*
         * The sound only plays when:
         *
         * 1. The configured distance threshold was reached.
         * 2. The pet is adjacent to the player.
         */
        if (tilesTravelled >= threshold && petNextToPlayer)
        {
            log.info(
                    "Tired sound triggered after {} tiles",
                    tilesTravelled
            );

            playTiredSound(follower);
        }
        else
        {
            if (tilesTravelled < threshold)
            {
                log.debug(
                        "Distance threshold not reached: {} / {} tiles",
                        tilesTravelled,
                        threshold
                );
            }

            if (!petNextToPlayer)
            {
                log.debug(
                        "Pet is not adjacent to player; " +
                                "tired sound will not play"
                );
            }
        }

        /*
         * Once the stopping period has completed, reset
         * both the accumulated distance and stopping timer.
         */
        tilesTravelled = 0;
        stoppedTicks = 0;
        wasMoving = false;
    }

    /**
     * Determines whether the pet is adjacent to the player.
     *
     * A distance of 0 or 1 is accepted. Normally a follower
     * will be on a neighboring tile, but accepting 0 prevents
     * false negatives if the client reports both entities
     * on the same tile.
     */
    private boolean isFollowerNextToPlayer(
            Player localPlayer,
            NPC follower)
    {
        if (follower == null || localPlayer == null)
        {
            return false;
        }

        WorldPoint playerLocation = localPlayer.getWorldLocation();

        WorldPoint petLocation =
                follower.getWorldLocation();

        if (playerLocation == null || petLocation == null)
        {
            return false;
        }

        return playerLocation.distanceTo(petLocation) <= FOLLOWER_ADJACENT_DISTANCE;
    }

    /**
     * Selects and plays the tired sound for the current follower.
     *
     * When the global Pug override is enabled, every follower uses
     * the Pug sound pool. Otherwise the follower-specific profile is
     * used when one exists, with Labrador sounds as the default pool.
     */
    private void playTiredSound(NPC follower)
    {
        FollowerSoundProfile profile;

        if (config.allPetsTireLikePugs())
        {
            profile = FollowerSoundProfile.PUG;

            log.info(
                    "All Pets Tire Like Pugs enabled; using Pug sound pool for follower ID {}",
                    follower.getId()
            );
        }
        else
        {
            profile = FollowerSoundProfile.forNpc(follower);

            if (profile == null)
            {
                profile = FollowerSoundProfile.LABRADOR;
            }
        }

        Sound sound = profile.chooseSound();

        log.info(
                "Tired sound triggered for follower ID {}: {}",
                follower.getId(),
                sound.getResource()
        );

        audioManager.play(sound, config.volume());
    }

    /**
     * Resets all movement tracking.
     */
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
        return configManager.getConfig(
                JustPugginAroundConfig.class
        );
    }
}