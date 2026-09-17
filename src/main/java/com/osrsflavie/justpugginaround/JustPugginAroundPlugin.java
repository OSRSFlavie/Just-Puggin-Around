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

    /**
     * Whether the distance threshold has been reached and
     * we are waiting for the follower to catch up.
     *
     * This is intentionally NOT reset when the stopping window
     * completes. The sound remains pending until the follower
     * actually reaches the player.
     */
    private boolean tiredSoundPending;

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
         * There is nothing to track without a follower.
         */
        if (follower == null)
        {
            resetTracking();

            previousLocation =
                    localPlayer.getWorldLocation();

            return;
        }

        /*
         * If the threshold was previously reached but the pet
         * was too far away, keep checking every tick until it
         * catches up.
         *
         * This check happens BEFORE movement/stopping processing
         * so the sound is not tied to the 2-tick stopping window.
         */
        if (tiredSoundPending &&
                isFollowerNextToPlayer(localPlayer, follower))
        {
            log.info(
                    "Pet caught up after threshold was reached; playing tired sound"
            );

            playTiredSound(follower);
            tiredSoundPending = false;

            /*
             * The completed tired-sound event starts a new run.
             */
            tilesTravelled = 0;
            stoppedTicks = 0;
            wasMoving = false;
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
         * As soon as the configured distance threshold is reached,
         * remember that a tired sound is owed to the player.
         *
         * We do NOT require the pet to be adjacent here.
         */
        if (!tiredSoundPending &&
                tilesTravelled >= config.distanceThreshold())
        {
            tiredSoundPending = true;

            log.debug(
                    "Distance threshold reached at {} tiles; tired sound is now pending",
                    tilesTravelled
            );
        }

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
     *
     * IMPORTANT:
     * If the pet is not adjacent, we do NOT discard the event.
     * The tiredSoundPending flag remains true and onGameTick()
     * will continue checking for the pet to catch up.
     */
    private void finishStoppedRun(NPC follower, Player localPlayer)
    {
        int threshold = config.distanceThreshold();

        boolean petNextToPlayer =
                isFollowerNextToPlayer(localPlayer, follower);

        log.debug(
                "Stopped for {} ticks after travelling {} tiles; " +
                        "threshold is {}; pet adjacent: {}; sound pending: {}",
                stoppedTicks,
                tilesTravelled,
                threshold,
                petNextToPlayer,
                tiredSoundPending
        );

        /*
         * If the threshold was reached but the movement handler
         * hasn't already marked the sound pending, mark it now.
         *
         * This also covers the case where the threshold was reached
         * without a movement tick being processed in the normal path.
         */
        if (!tiredSoundPending && tilesTravelled >= threshold)
        {
            tiredSoundPending = true;

            log.debug(
                    "Distance threshold reached during stop evaluation; " +
                            "tired sound is now pending"
            );
        }

        /*
         * If the pet is already beside the player, play immediately.
         *
         * Otherwise, leave tiredSoundPending=true so that the sound
         * will play later when the pet catches up.
         */
        if (tiredSoundPending && petNextToPlayer)
        {
            log.info(
                    "Tired sound triggered after {} tiles",
                    tilesTravelled
            );

            playTiredSound(follower);

            tiredSoundPending = false;

            /*
             * The sound event has completed, so start tracking
             * a fresh distance run.
             */
            tilesTravelled = 0;
        }
        else if (tilesTravelled < threshold)
        {
            log.debug(
                    "Distance threshold not reached: {} / {} tiles",
                    tilesTravelled,
                    threshold
            );

            /*
             * No threshold was reached, so this run can safely reset.
             */
            tilesTravelled = 0;
            tiredSoundPending = false;
        }
        else if (!petNextToPlayer)
        {
            log.debug(
                    "Pet is not adjacent to player; " +
                            "tired sound remains pending until pet catches up"
            );

            /*
             * IMPORTANT:
             * Do NOT reset tiredSoundPending here.
             *
             * The pet may take additional ticks to catch up.
             */
        }

        /*
         * The stopping window itself is complete.
         * Reset only the stopping timer.
         *
         * Do NOT clear tiredSoundPending here.
         */
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

        WorldPoint playerLocation =
                localPlayer.getWorldLocation();

        WorldPoint petLocation =
                follower.getWorldLocation();

        if (playerLocation == null || petLocation == null)
        {
            return false;
        }

        return playerLocation.distanceTo(petLocation)
                <= FOLLOWER_ADJACENT_DISTANCE;
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
        tiredSoundPending = false;
    }

    @Provides
    JustPugginAroundConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(
                JustPugginAroundConfig.class
        );
    }
}
