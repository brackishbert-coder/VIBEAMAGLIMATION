// com/imagedubstep/dynamics/SidechainDuckerNode.java
package com.imagedubstep.dynamics;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;

public class SidechainDuckerNode extends AudioNode {
    private final AudioNode src;
    private final SidechainSignal bus;
    private final float depthLin;  // min gain when fully ducked (e.g., 0.5 = -6dB)
    private final float thresh;    // envelope threshold to start ducking (0..1)
    private final float ratio;     // knee-ish curve >1.0
    private final float atk, rel;  // gain-smoothing (seconds → coeffs)
    private float g;               // smoothed gain
    private final SidechainParams params;
    public SidechainDuckerNode(AudioContext ctx, AudioNode src, SidechainSignal bus,
                               double depthDb, double threshold, double ratio,
                               double attackMs, double releaseMs, SidechainParams params) {
        super(ctx);
        this.src = src;
        this.bus = bus;
        this.depthLin = (float)Math.pow(10.0, depthDb/20.0); // e.g., -6dB → 0.501
        this.thresh   = (float)threshold;
        this.ratio    = (float)Math.max(1.0, ratio);
        this.atk      = (float)Math.exp(-1.0/(ctx.getSampleRate()*(attackMs/1000.0)));
        this.rel      = (float)Math.exp(-1.0/(ctx.getSampleRate()*(releaseMs/1000.0)));
        this.g = 1.0f;
        this.params = params;
    }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);

        // snapshot params at block start
        float depthLin = (float)Math.pow(10.0, params.depthDb/20.0f);
        float thresh   = params.threshold;
        float ratio    = Math.max(1.0f, params.ratio);
        float atk = (float)Math.exp(-1.0/(ctx.getSampleRate()*(params.attackMs/1000.0)));
        float rel = (float)Math.exp(-1.0/(ctx.getSampleRate()*(params.releaseMs/1000.0)));

        float env = bus.get();
        float over = Math.max(0f, env - thresh);
        float k = (float)(1.0 / (1.0 + Math.pow(over * 4.0, ratio)));
        float target = depthLin + (1f - depthLin) * k;

        for (int i = 0; i < n; i++) {
            float coeff = (target < g) ? atk : rel;
            g = coeff * g + (1 - coeff) * target;
            L[i] += g * tL[i];
            R[i] += g * tR[i];
        }
    }

    @Override public boolean isDone() { return src.isDone(); }
}
