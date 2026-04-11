package com.imagedubstep.engine2;
public interface NoteSink {
    /**
     * Called on each active step.
     * @param instrument Instrument to trigger
     * @param step       0-based step index
     * @param velocity   0..1 suggested loudness
     * @param whenNanos  monotonic timestamp the step belongs to (for tight scheduling if needed)
     */
    void trigger(Instrument instrument, int step, double velocity, long whenNanos);
}
