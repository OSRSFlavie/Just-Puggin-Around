package com.osrsflavie.justpugginaround;

import com.google.inject.Provides;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
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

    /**
     * Normal tired sound.
     */
    private static final String PUG_TIRED_SOUND = "/pug_tired.wav";

    /**
     * Rare alternate tired sound.
     */
    private static final String PUPPY_TIRED_SOUND = "/puppy_tired.wav";

    @Inject
    private Client client;

    @Inject
    private JustPugginAroundConfig config;

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

        /*
         * If there is no qualifying follower, there is
         * nothing to track.
         */
        if (!hasQualifyingFollower(follower))
        {
            resetTracking();

            previousLocation =
                    client.getLocalPlayer().getWorldLocation();

            return;
        }

        WorldPoint currentLocation =
                client.getLocalPlayer().getWorldLocation();

        /*
         * Establish the initial player position.
         */
        if (previousLocation == null)
        {
            previousLocation = currentLocation;
            return;
        }

        boolean moved =
                !currentLocation.equals(previousLocation);

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

    /**
     * Handles a tick during which the player moved.
     */
    private void handleMovement(WorldPoint currentLocation)
    {
        int distance =
                previousLocation.distanceTo(currentLocation);

        if (distance > 0)
        {
            tilesTravelled += distance;

            log.debug(
                    "Travelled {} tiles this tick; {} tiles total",
                    distance,
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
    private void handleStopped(NPC follower)
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
            finishStoppedRun(follower);
        }
    }

    /**
     * Evaluates the completed stopping period.
     */
    private void finishStoppedRun(NPC follower)
    {
        int threshold = config.distanceThreshold();

        boolean followerNextToPlayer =
                isFollowerNextToPlayer(follower);

        log.debug(
                "Stopped for {} ticks after travelling {} tiles; " +
                        "threshold is {}; follower adjacent: {}",
                stoppedTicks,
                tilesTravelled,
                threshold,
                followerNextToPlayer
        );

        /*
         * The sound only plays when:
         *
         * 1. The configured distance threshold was reached.
         * 2. The follower is adjacent to the player.
         */
        if (tilesTravelled >= threshold && followerNextToPlayer)
        {
            log.info(
                    "Tired sound triggered after {} tiles",
                    tilesTravelled
            );

            playTiredSoundWithChance();
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

            if (!followerNextToPlayer)
            {
                log.debug(
                        "Follower is not adjacent to player; " +
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
     * Determines whether the current follower qualifies.
     *
     * With "All followers tire like pugs" disabled,
     * only a follower named Pug qualifies.
     */
    private boolean hasQualifyingFollower(NPC follower)
    {
        if (follower == null)
        {
            return false;
        }

        /*
         * Configuration override allowing any follower.
         */
        if (config.allFollowers())
        {
            return true;
        }

        /*
         * Default behavior: only Pug.
         */
        return follower.getName() != null
                && follower.getName().equalsIgnoreCase("Pug");
    }

    /**
     * Determines whether the follower is adjacent to the player.
     *
     * A distance of 0 or 1 is accepted. Normally a follower
     * will be on a neighboring tile, but accepting 0 prevents
     * false negatives if the client reports both entities
     * on the same tile.
     */
    private boolean isFollowerNextToPlayer(NPC follower)
    {
        if (follower == null || client.getLocalPlayer() == null)
        {
            return false;
        }

        WorldPoint playerLocation =
                client.getLocalPlayer().getWorldLocation();

        WorldPoint followerLocation =
                follower.getWorldLocation();

        if (playerLocation == null || followerLocation == null)
        {
            return false;
        }

        return playerLocation.distanceTo(followerLocation) <= 1;
    }

    /**
     * Performs the 1/100 rare-sound roll.
     *
     * 1/100:
     *     puppy_tired.wav
     *
     * 99/100:
     *     pug_tired.wav
     */
    private void playTiredSoundWithChance()
    {
        /*
         * nextInt(100) returns 0 through 99.
         *
         * Only 0 selects the rare sound, giving exactly
         * a 1/100 chance.
         */
        if (ThreadLocalRandom.current().nextInt(100) == 0)
        {
            log.info(
                    "Rare tired sound triggered: {}",
                    PUPPY_TIRED_SOUND
            );

            playTiredSound(PUPPY_TIRED_SOUND);
        }
        else
        {
            playTiredSound(PUG_TIRED_SOUND);
        }
    }

    /**
     * Plays a tired sound resource at the configured volume.
     */
    private void playTiredSound(String soundResource)
    {
        log.info("Attempting to play {}", soundResource);

        try (InputStream inputStream =
                     getClass().getResourceAsStream(soundResource))
        {
            if (inputStream == null)
            {
                log.error(
                        "Sound resource was not found: {}",
                        soundResource
                );

                return;
            }

            try (BufferedInputStream bufferedInputStream =
                         new BufferedInputStream(inputStream);
                 AudioInputStream audioInputStream =
                         AudioSystem.getAudioInputStream(
                                 bufferedInputStream))
            {
                log.info(
                        "Audio format for {}: {}",
                        soundResource,
                        audioInputStream.getFormat()
                );

                final Clip clip = AudioSystem.getClip();

                clip.open(audioInputStream);

                /*
                 * Apply configured volume if the audio device
                 * supports MASTER_GAIN.
                 */
                if (clip.isControlSupported(
                        FloatControl.Type.MASTER_GAIN))
                {
                    FloatControl gainControl =
                            (FloatControl) clip.getControl(
                                    FloatControl.Type.MASTER_GAIN
                            );

                    int volume = config.volume();

                    if (volume <= 0)
                    {
                        gainControl.setValue(
                                gainControl.getMinimum()
                        );
                    }
                    else
                    {
                        /*
                         * Convert percentage to decibels.
                         *
                         * 100% = 0 dB
                         * 50%  ≈ -6 dB
                         * 25%  ≈ -12 dB
                         */
                        float gain =
                                (float) (
                                        20.0 *
                                                Math.log10(volume / 100.0)
                                );

                        gain = Math.max(
                                gainControl.getMinimum(),
                                Math.min(
                                        gainControl.getMaximum(),
                                        gain
                                )
                        );

                        gainControl.setValue(gain);
                    }

                    log.debug(
                            "Playing {} at {}% volume ({} dB)",
                            soundResource,
                            volume,
                            gainControl.getValue()
                    );
                }
                else
                {
                    log.warn(
                            "Audio clip does not support MASTER_GAIN; " +
                                    "playing {} at default volume",
                            soundResource
                    );
                }

                clip.start();

                Thread cleanupThread = new Thread(() ->
                {
                    try
                    {
                        long duration =
                                clip.getMicrosecondLength();

                        if (duration > 0)
                        {
                            Thread.sleep(duration / 1000L);
                        }

                        clip.close();
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        clip.close();
                    }
                });

                cleanupThread.setDaemon(true);
                cleanupThread.start();
            }
        }
        catch (Exception ex)
        {
            log.error(
                    "Unable to play {}. Exception type: {}. Message: {}",
                    soundResource,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex
            );
        }
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