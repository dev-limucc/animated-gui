package dev.limucc.animatedgui.client.anim;

/**
 * The transition curves the user can pick per feature. Every curve maps a normalized progress
 * {@code t ∈ [0,1]} to an eased {@code [0,1]} (a couple overshoot past 1 on the way, by design).
 *
 * <p>Kept deliberately small and named in plain language so it reads well in the settings screen:
 * the label is what the user cycles through.
 */
public enum Easing {
    LINEAR("Linear") {
        @Override public float apply(float t) { return t; }
    },
    SINE("Smooth") {
        @Override public float apply(float t) { return -(float) (Math.cos(Math.PI * t) - 1) / 2f; }
    },
    EASE_OUT("Ease-out") {
        @Override public float apply(float t) { float u = 1 - t; return 1 - u * u * u; }
    },
    EASE_IN_OUT("Ease-in-out") {
        @Override public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2f;
        }
    },
    BACK("Overshoot") {
        @Override public float apply(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1;
            float u = t - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }
    },
    ELASTIC("Elastic") {
        @Override public float apply(float t) {
            if (t == 0 || t == 1) return t;
            final float c4 = (float) (2 * Math.PI / 3);
            return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
        }
    },
    BOUNCE("Bounce") {
        @Override public float apply(float t) {
            final float n1 = 7.5625f, d1 = 2.75f;
            if (t < 1 / d1)        return n1 * t * t;
            else if (t < 2 / d1)   { t -= 1.5f / d1;  return n1 * t * t + 0.75f; }
            else if (t < 2.5 / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
            else                   { t -= 2.625f / d1; return n1 * t * t + 0.984375f; }
        }
    };

    private final String label;

    Easing(String label) { this.label = label; }

    public String label() { return label; }

    /** Maps normalized progress t∈[0,1] to the eased value (may briefly exceed [0,1] for BACK/ELASTIC). */
    public abstract float apply(float t);

    public Easing next() {
        Easing[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
