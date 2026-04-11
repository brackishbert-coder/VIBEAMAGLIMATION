package com.imagedubstep.engine2;

import java.util.Arrays;

/** Simple neuron model mirroring the JS: weights=[r,g,b,brightness,contrast], plus grid coords. */
public final class SOMNeuron {
    public final double[] w; // length=5: r,g,b,brightness,contrast (all normalized 0..1)
    public final int x, y;

    public SOMNeuron(double[] weights, int x, int y) {
        if (weights == null || weights.length != 5) {
            throw new IllegalArgumentException("weights must be length 5: [r,g,b,brightness,contrast]");
        }
        this.w = Arrays.copyOf(weights, 5);
        this.x = x;
        this.y = y;
    }
}