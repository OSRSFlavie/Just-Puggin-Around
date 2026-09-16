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

    /** Pre-loaded audio clips reused for playback. */
    private Clip pugTiredClip;
    private Clip puppyTiredClip;

    /** Player location from the previous game tick. */
    private WorldPoint previousLocation;

    /** Number of tiles travelled during the current run. */
    private int tilesTravelled;

    /** Number of consecutive stationary ticks. */
    private int stoppedTicks;

    /** Whether the player moved during the previous tick. */
    private boolean wasMoving;

    @Override
    protected void startUp()
    {
        resetTracking();

        pugTiredClip = loadClip(PUG_TIRED_SOUND);
        puppyTiredClip = loadClip(PUPPY_TIRED_SOUND);

        log.info("Just Puggin' Around started");
    }

    @Override
    protected void shutDown()
    {
        closeClip(pugTiredClip);
        pugTiredClip = null;

        closeClip(puppyTiredClip);
        puppyTiredClip = null;

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
        boolean followerNextToPlayer = isFollowerNextToPlayer(follower);

        if (tilesTravelled >= threshold && followerNextToPlayer)
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
            playTiredSound(puppyTiredClip, PUPPY_TIRED_SOUND);
        }
        else
        {
            playTiredSound(pugTiredClip, PUG_TIRED_SOUND);
        }
    }

    /**
     * Plays an already-loaded audio clip.
     * No audio decoding or Clip creation occurs on the GameTick path.
     */
    private void playTiredSound(Clip clip, String soundResource)
    {
        if (clip == null)
        {
            log.warn(
                    "Unable to play {} because the audio clip was not loaded",
                    soundResource
            );
            return;
        }

        if (clip.isRunning())
        {
            return;
        }

        try
        {
            applyVolume(clip);
            clip.setFramePosition(0);
            clip.start();
        }
        catch (Exception ex)
        {
            log.warn("Unable to play {}", soundResource, ex);
        }
    }

    /**
     * Loads a classpath audio resource into a Clip during plugin startup.
     */
    private Clip loadClip(String soundResource)
    {
        try (InputStream inputStream = getClass().getResourceAsStream(soundResource))
        {
            if (inputStream == null)
            {
                log.warn("Sound resource was not found: {}", soundResource);
                return null;
            }

            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream))
            {
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);

                return clip;
            }
        }
        catch (Exception ex)
        {
            log.warn("Unable to load sound resource {}", soundResource, ex);
            return null;
        }
    }

    /**
     * Applies the configured volume to an already-loaded clip.
     */
    private void applyVolume(Clip clip)
    {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
        {
            return;
        }

        FloatControl gainControl =
                (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        int volume = config.volume();

        if (volume <= 0)
        {
            gainControl.setValue(gainControl.getMinimum());
            return;
        }

        float gain = (float) (20.0 * Math.log10(volume / 100.0));

        gain = Math.max(
                gainControl.getMinimum(),
                Math.min(gainControl.getMaximum(), gain)
        );

        gainControl.setValue(gain);
    }

    /**
     * Stops and closes a loaded audio clip.
     */
    private void closeClip(Clip clip)
    {
        if (clip == null)
        {
            return;
        }

        try
        {
            clip.stop();
            clip.close();
        }
        catch (Exception ex)
        {
            log.debug("Error while closing audio clip", ex);
        }
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