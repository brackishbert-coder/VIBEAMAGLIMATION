// com/imagedubstep/filter/LowMonoizerNode.java
package com.imagedubstep.filter;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;
import com.imagedubstep.util.Biquad;

/** Converts deep lows toward mono by low-passing the Side signal and scaling it down. */
public class LowMonoizerNode extends AudioNode {
    private final AudioNode src;
    private final Biquad sideLP;
    private float sideGain; // 0..1 amount of side retained in low band
    private volatile double cutoffHz;
    public LowMonoizerNode(AudioContext ctx, AudioNode src, double cutoffHz, double sideGain) {
        super(ctx);
        this.src = src;
        this.sideLP = new Biquad(ctx.getSampleRate());
        setCutoff(cutoffHz);
        setSideGain(sideGain);;
    }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);

        // Mid/Side
        float[] M = new float[n], S = new float[n];
        for (int i = 0; i < n; i++) { M[i] = 0.5f*(tL[i]+tR[i]); S[i] = 0.5f*(tL[i]-tR[i]); }

        // Low-pass the Side (only lows), then scale it down
        sideLP.process(S, S, n);
        for (int i = 0; i < n; i++) {
            S[i] *= sideGain;
            // Recombine
            L[i] += M[i] + S[i];
            R[i] += M[i] - S[i];
        }
    }
    public void setSideGain(double amt) { this.sideGain = (float)Math.max(0, Math.min(1, amt)); }
    public void setCutoff(double hz) {
        this.cutoffHz = Math.max(20.0, Math.min(2000.0, hz));
        this.sideLP.set(Biquad.Type.LOWPASS, this.cutoffHz, 0.707);
    }
    @Override public boolean isDone() { return src.isDone(); }
}
