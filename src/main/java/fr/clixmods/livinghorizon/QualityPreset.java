package fr.clixmods.livinghorizon;

import java.util.Locale;

/**
 * Ready-made settings for how far and how many: {@link #NORMAL} is the defaults, the others
 * trade the cost of drawing more and farther for a fuller horizon. They set only the
 * settings that scale that cost; everything else stays as the player chose it. None of them
 * goes without a limit: that is for a player who chose it, and was asked first.
 */
public enum QualityPreset {
    /**
     * Everything at its least, for the weakest computers: few mobs, close, and every one the mod
     * draws a picture rather than a model, walking (with {@link FarConfig#impostorAnimation} on),
     * which costs the least to draw.
     */
    POTATO(16, 64, 128, 10, 200, 128),
    LOW(64, 128, 256, 50, 400, 128),
    /** Read from the defaults themselves, so that it is always what a fresh install has. */
    NORMAL(new FarConfig()),
    HIGH(256, 1500, 1024, 200, 1500, 512),
    ULTRA(512, 3000, 2048, 400, 3000, 1024);

    private final int impostorDistance, maxDistantMobs, mobMaxDistance, birdDensity, birdMaxDistance,
            animationDistance;

    QualityPreset(int impostorDistance, int maxDistantMobs, int mobMaxDistance, int birdDensity, int birdMaxDistance,
                  int animationDistance) {
        this.impostorDistance = impostorDistance;
        this.maxDistantMobs = maxDistantMobs;
        this.mobMaxDistance = mobMaxDistance;
        this.birdDensity = birdDensity;
        this.birdMaxDistance = birdMaxDistance;
        this.animationDistance = animationDistance;
    }

    QualityPreset(FarConfig defaults) {
        this(defaults.impostorDistance, defaults.maxDistantMobs, defaults.mobMaxDistance, defaults.birdDensity,
                defaults.birdMaxDistance, defaults.impostorAnimationDistance);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** How far its pictures walk ({@link FarConfig#impostorAnimationDistance}). */
    int animationDistance() {
        return animationDistance;
    }

    public void apply(FarConfig c) {
        c.impostorDistance = impostorDistance;
        c.maxDistantMobs = maxDistantMobs;
        c.mobMaxDistance = mobMaxDistance;
        c.birdDensity = birdDensity;
        c.birdMaxDistance = birdMaxDistance;
        c.impostorAnimationDistance = animationDistance;
    }

    public boolean matches(FarConfig c) {
        return matchesScale(c) && c.impostorAnimationDistance == animationDistance;
    }

    /** Whether the settings it had before it set {@link FarConfig#impostorAnimationDistance} are its own. */
    boolean matchesScale(FarConfig c) {
        return c.impostorDistance == impostorDistance && c.maxDistantMobs == maxDistantMobs
                && c.mobMaxDistance == mobMaxDistance && c.birdDensity == birdDensity
                && c.birdMaxDistance == birdMaxDistance;
    }

    /** The preset the settings are on, or null when they match none (a custom setup). */
    public static QualityPreset of(FarConfig c) {
        for (QualityPreset preset : values()) if (preset.matches(c)) return preset;
        return null;
    }

    /** The next one up, round to the lowest; a custom setup goes to {@link #NORMAL}. */
    public static QualityPreset next(FarConfig c) {
        QualityPreset current = of(c);
        if (current == null) return NORMAL;
        return values()[(current.ordinal() + 1) % values().length];
    }
}
