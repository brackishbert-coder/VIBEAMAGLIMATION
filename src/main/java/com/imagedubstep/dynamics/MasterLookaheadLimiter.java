// com/imagedubstep/dynamics/MasterLookaheadLimiter.java
package com.imagedubstep.dynamics;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;

/** Simple stereo peak limiter with look-ahead delay and smooth GR. */
public class MasterLookaheadLimiter extends AudioNode {
    private final AudioNode src;
    private float thresh; // linear threshold (e.g., 0.98)
    private final int look;     // samples of look-ahead
    private float atk;
	private float rel;
    // delay buffers
    private final float[] dL, dR;
    private int wp = 0;
    private float gain = 1f;

    public MasterLookaheadLimiter(AudioContext ctx, AudioNode src,
                                  double threshold, double lookaheadMs,
                                  double attackMs, double releaseMs) {
        super(ctx);
        this.src = src;
        this.thresh = (float)threshold;
        this.look = Math.max(1, (int)Math.round(ctx.getSampleRate() * (lookaheadMs/1000.0)));
        this.atk = (float)Math.exp(-1.0/(ctx.getSampleRate()*(attackMs/1000.0)));
        this.rel = (float)Math.exp(-1.0/(ctx.getSampleRate()*(releaseMs/1000.0)));
        this.dL = new float[look];
        this.dR = new float[look];
    }

public void setThreshold(double t) { this.thresh = (float)Math.max(0.1, Math.min(1.2, t)); }
public void setAttackMs(double ms) { this.atk = (float)Math.exp(-1.0/(ctx.getSampleRate()*(ms/1000.0))); }
public void setReleaseMs(double ms){ this.rel = (float)Math.exp(-1.0/(ctx.getSampleRate()*(ms/1000.0))); }
    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);

        for (int i = 0; i < n; i++) {
            // write into delay
            dL[wp] = tL[i];
            dR[wp] = tR[i];
            int rp = (wp + 1) % look; // read pointer (lookahead ahead)
            wp = rp;

            float preL = dL[rp];
            float preR = dR[rp];

            // peak envelope (undelayed signal)
            float peak = Math.max(Math.abs(tL[i]), Math.abs(tR[i]));
            float needed = peak > thresh ? (thresh / (peak + 1e-9f)) : 1f;

            // smooth gain towards needed (faster down, slower up)
            float coeff = (needed < gain) ? atk : rel;
            gain = coeff * gain + (1 - coeff) * needed;

            L[i] += preL * gain;
            R[i] += preR * gain;
        }
    }

    @Override public boolean isDone() { return src.isDone(); }
}
