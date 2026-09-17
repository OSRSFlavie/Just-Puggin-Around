package com.osrsflavie.justpugginaround;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.runelite.api.NPC;

/**
 * Defines the tired sounds associated with specific followers.
 *
 * Each profile contains a weighted pool of sounds. Additional sounds
 * can be added to a profile without changing the movement or audio logic.
 */
enum FollowerSoundProfile
{
    /**
     * Pug variants.
     *
     * Normal sound: 499 / 500
     * Rare sound:     1 / 500
     */
    PUG(
            new WeightedSound(Sound.PUG_TIRED, 499),
            new WeightedSound(Sound.PUPPY_TIRED, 1),
            16379,
            16380,
            16381
    ),

    /**
     * Labrador variants.
     *
     * Normal sound: 499 / 500
     * Rare sound:     1 / 500
     */
    LABRADOR(
            new WeightedSound(Sound.LABRADOR_TIRED, 499),
            new WeightedSound(Sound.PUPPY_TIRED_RARE, 1),
            16361,
            16362,
            16363
    );

    private static final Map<Integer, FollowerSoundProfile> BY_NPC_ID =
            new HashMap<>();

    static
    {
        for (FollowerSoundProfile profile : values())
        {
            for (int npcId : profile.npcIds)
            {
                BY_NPC_ID.put(npcId, profile);
            }
        }
    }

    private final WeightedSound[] sounds;
    private final int[] npcIds;

    FollowerSoundProfile(
            WeightedSound sound1,
            WeightedSound sound2,
            int... npcIds)
    {
        this.sounds = new WeightedSound[]
        {
                sound1,
                sound2
        };

        this.npcIds = npcIds;
    }

    static FollowerSoundProfile forNpc(NPC npc)
    {
        if (npc == null)
        {
            return null;
        }

        return BY_NPC_ID.get(npc.getId());
    }

    /**
     * Chooses one sound from this profile's weighted sound pool.
     */
    Sound chooseSound()
    {
        return chooseWeightedSound(sounds);
    }

    /**
     * Represents one sound and its relative selection weight.
     */
    private static final class WeightedSound
    {
        private final Sound sound;
        private final int weight;

        private WeightedSound(Sound sound, int weight)
        {
            this.sound = sound;
            this.weight = weight;
        }
    }

    /**
     * Selects one sound according to the configured relative weights.
     *
     * Current pools total 500, giving the rare sound a 1/500 chance.
     * Additional sounds can be added by adding another WeightedSound entry.
     */
    private static Sound chooseWeightedSound(WeightedSound... sounds)
    {
        int totalWeight = 0;

        for (WeightedSound weightedSound : sounds)
        {
            if (weightedSound.weight > 0)
            {
                totalWeight += weightedSound.weight;
            }
        }

        if (totalWeight <= 0)
        {
            throw new IllegalStateException(
                    "Follower sound profile has no positive sound weights"
            );
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulativeWeight = 0;

        for (WeightedSound weightedSound : sounds)
        {
            if (weightedSound.weight <= 0)
            {
                continue;
            }

            cumulativeWeight += weightedSound.weight;

            if (roll < cumulativeWeight)
            {
                return weightedSound.sound;
            }
        }

        throw new IllegalStateException(
                "Unable to select follower tired sound"
        );
    }
}
