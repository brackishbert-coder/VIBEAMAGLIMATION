package com.imagedubstep.filter;


import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

/** Wraps any source node and runs up to three serial filters: HP -> BP -> LP. */
public class FilterChainNode extends AudioNode {
    private final AudioNode source;
    private final Biquad hp, bp, lp;
    private boolean useHP, useBP, useLP;

    public FilterChainNode(AudioContext ctx, AudioNode source) {
        super(ctx);
        this.source = source;
        hp = new Biquad(ctx.getSampleRate());
        bp = new Biquad(ctx.getSampleRate());
        lp = new Biquad(ctx.getSampleRate());
    }

    public FilterChainNode setHP(double cutoffHz, double q) {
        useHP = cutoffHz > 0;
        if (useHP) hp.set(Biquad.Type.HIGHPASS, cutoffHz, q);
        return this;
    }
    public FilterChainNode setBP(double centerHz, double q) {
        useBP = centerHz > 0;
        if (useBP) bp.set(Biquad.Type.BANDPASS, centerHz, q);
        return this;
    }
    public FilterChainNode setLP(double cutoffHz, double q) {
        useLP = cutoffHz > 0;
        if (useLP) lp.set(Biquad.Type.LOWPASS, cutoffHz, q);
        return this;
    }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        source.process(tL, tR, n);
        if (useHP) hp.process(tL, tR, n);
        if (useBP) bp.process(tL, tR, n);
        if (useLP) lp.process(tL, tR, n);
        for (int i = 0; i < n; i++) { L[i] += tL[i]; R[i] += tR[i]; }
    }

    @Override public boolean isDone() { return source.isDone(); }
}
