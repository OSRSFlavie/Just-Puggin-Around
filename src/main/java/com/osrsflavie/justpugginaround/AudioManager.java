package com.osrsflavie.justpugginaround;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads and reuses audio clips.
 *
 * Each sound has one cached Clip. Since sounds never need to overlap,
 * the manager stops the currently playing clip before starting another.
 */
@Slf4j
final class AudioManager
{
    private final Class<?> resourceClass;
    private final Map<Sound, Clip> clips = new EnumMap<>(Sound.class);

    private Clip currentlyPlaying;

    AudioManager(Class<?> resourceClass)
    {
        this.resourceClass = resourceClass;
    }

    /**
     * Preloads the supplied sounds so normal GameTick processing does not
     * perform audio I/O or decoding.
     */
    void preload(Sound... sounds)
    {
        for (Sound sound : sounds)
        {
            if (clips.containsKey(sound))
            {
                continue;
            }

            Clip clip = loadClip(sound);

            if (clip != null)
            {
                clips.put(sound, clip);
            }
        }
    }

    /**
     * Plays a cached sound. Only one sound can play at a time.
     *
     * If another sound is playing, it is stopped first. If the requested
     * sound is already playing, it is restarted from the beginning.
     */
    void play(Sound sound, int volume)
    {
        Clip clip = clips.get(sound);

        if (clip == null)
        {
            log.warn(
                    "Sound {} was requested but has not been loaded",
                    sound.getResource()
            );
            return;
        }

        if (currentlyPlaying != null && currentlyPlaying != clip)
        {
            currentlyPlaying.stop();
        }

        applyVolume(clip, volume);

        clip.stop();
        clip.setFramePosition(0);
        clip.start();

        currentlyPlaying = clip;
    }

    /**
     * Releases all native audio resources owned by this manager.
     */
    void close()
    {
        for (Clip clip : clips.values())
        {
            clip.close();
        }

        clips.clear();
        currentlyPlaying = null;
    }

    private Clip loadClip(Sound sound)
    {
        String resource = sound.getResource();
        log.debug("Loading sound resource {}", resource);

        try (InputStream inputStream =
                     resourceClass.getResourceAsStream(resource))
        {
            if (inputStream == null)
            {
                log.error("Sound resource was not found: {}", resource);
                return null;
            }

            try (BufferedInputStream bufferedInputStream =
                         new BufferedInputStream(inputStream);
                 AudioInputStream audioInputStream =
                         AudioSystem.getAudioInputStream(bufferedInputStream))
            {
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);

                log.debug(
                        "Loaded {} with format {}",
                        resource,
                        audioInputStream.getFormat()
                );

                return clip;
            }
        }
        catch (Exception ex)
        {
            log.error(
                    "Unable to load {}. Exception type: {}. Message: {}",
                    resource,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex
            );
            return null;
        }
    }

    private void applyVolume(Clip clip, int volume)
    {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
        {
            return;
        }

        FloatControl gainControl =
                (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

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
}
