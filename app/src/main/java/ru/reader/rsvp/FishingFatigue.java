package ru.reader.rsvp;

/** A single fish's temporary fatigue. Delta-time is supplied only by the active game clock. */
final class FishingFatigue {
    static final int FULL_STRENGTH=0, TIRED=1, RECOVERING=2;
    private int state=FULL_STRENGTH;
    private float remaining=0f;
    private float duration=2f;
    private float threshold=.9f;

    void reset() {state=FULL_STRENGTH;remaining=0f;}
    void begin(int rarity, boolean shimmering, int rod) {
        reset();
        threshold=FishingBalance.fatigueThreshold(rarity,shimmering);
        duration=FishingBalance.recoverySeconds(rod);
    }
    void update(float progress, float dt) {
        if (state==FULL_STRENGTH) {
            if (progress>=threshold) {state=TIRED;remaining=duration;}
        } else if (progress>=threshold) {
            // Returning to the threshold cancels the countdown, not just pauses it.
            state=TIRED;remaining=duration;
        } else if (state==TIRED) {
            state=RECOVERING;remaining=duration;
        } else { // RECOVERING with progress below threshold
            remaining=Math.max(0f,remaining-Math.max(0f,dt));
            if (remaining<=0f) reset();
        }
    }
    boolean isTired() {return state!=FULL_STRENGTH;}
    int state() {return state;}
    float threshold() {return threshold;}
    float remaining() {return remaining;}
    float duration() {return duration;}
    float ringFraction() {return state==TIRED?1f:state==RECOVERING?Math.max(0f,Math.min(1f,remaining/duration)):0f;}
}
