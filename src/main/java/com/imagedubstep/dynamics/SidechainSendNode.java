// com/imagedubstep/dynamics/SidechainSendNode.java
package com.imagedubstep.dynamics;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;

public class SidechainSendNode extends AudioNode {
    private final AudioNode src;
    private final SidechainSignal bus;
    // envelope follower (attack/release in seconds)
    private final float atk, rel;
    private float env;

    public SidechainSendNode(AudioContext ctx, AudioNode src,
                             SidechainSignal bus, double attackMs, double releaseMs) {
        super(ctx);
        this.src = src;
        this.bus = bus;
        this.atk = (float) Math.exp(-1.0 / (ctx.getSampleRate() * (attackMs/1000.0)));
        this.rel = (float) Math.exp(-1.0 / (ctx.getSampleRate() * (releaseMs/1000.0)));
    }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);
        for (int i = 0; i < n; i++) {
            float x = 0.5f * (Math.abs(tL[i]) + Math.abs(tR[i])); // rectified mono
            // peak-ish follower
            if (x > env) env = atk * env + (1 - atk) * x;
            else         env = rel * env + (1 - rel) * x;
            // sum to output
            L[i] += tL[i];
            R[i] += tR[i];
        }
        bus.set(env);
    }

    @Override public boolean isDone() { return src.isDone(); }
}
