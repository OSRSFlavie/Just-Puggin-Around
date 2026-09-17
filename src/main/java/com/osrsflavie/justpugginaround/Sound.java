package com.osrsflavie.justpugginaround;

/**
 * Sounds available to the plugin.
 *
 * Add new sounds here as the sound library grows so resource
 * names remain centralized and type-safe.
 */
enum Sound
{
    PUG_TIRED("/pug_tired.wav"),
    LABRADOR_TIRED("/labrador_tired.wav"),
    PUPPY_TIRED("/puppy_tired.wav"),
    PUPPY_TIRED_RARE("/puppy_tired2.wav");

    private final String resource;

    Sound(String resource)
    {
        this.resource = resource;
    }

    String getResource()
    {
        return resource;
    }
}
