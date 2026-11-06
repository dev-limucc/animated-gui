package dev.limucc.animatedgui.client.anim;

/**
 * Implemented by the selection-list mixin so a click can be hit-tested against the <em>displayed</em> (smoothed)
 * scroll position rather than the target the list is still gliding toward.
 */
public interface ListScrollHit {
    /** Swap the displayed scroll position in for the duration of a click. */
    void animatedgui$hitBegin();

    /** Put the real target scroll position back (unless the click itself moved the scroll, e.g. a scrollbar grab). */
    void animatedgui$hitEnd();
}
