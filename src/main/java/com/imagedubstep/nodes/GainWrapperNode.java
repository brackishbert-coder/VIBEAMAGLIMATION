package com.imagedubstep.nodes;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;
import java.util.Arrays;

/** Multiplies the output of an inner node by a gain factor. */
public final class GainWrapperNode extends AudioNode {
    private final AudioNode inner;
    private volatile float gain;
    private float[] tmpL, tmpR;

    public GainWrapperNode(AudioContext ctx, AudioNode inner, double gain) {
        super(ctx);
        this.inner = inner;
        this.gain = (float) gain;
    }

    public void setGain(double g) { this.gain = (float) g; }

    @Override
    public void process(float[] L, float[] R, int n) {
        // Render source into temp buffers, then scale+mix into L/R.
        float[] tL = new float[n];
        float[] tR = new float[n];
        inner.process(tL, tR, n);
        float g = (float) gain;
        for (int i = 0; i < n; i++) {
            L[i] += tL[i] * g;
            R[i] += tR[i] * g;
        }
    }
}
