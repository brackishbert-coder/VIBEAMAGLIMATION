package com.imagedubstep.nodes;


import java.util.ArrayList;
import java.util.List;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;
import com.imagedubstep.util.Biquad;

/** Serial parametric EQ: apply multiple shelves/peaks after a source node. */
public class ParametricEQNode extends AudioNode {
    private final AudioNode source;
    private final List<Biquad> bands = new ArrayList<>();

    public ParametricEQNode(AudioContext ctx, AudioNode source) {
        super(ctx);
        this.source = source;
    }

    /** Remove all EQ bands. */
    public ParametricEQNode clear() { bands.clear(); return this; }

    /** Add a bell/peak (or notch if gainDb < 0). */
    public ParametricEQNode addPeak(double freqHz, double q, double gainDb) {
        Biquad b = new Biquad(ctx.getSampleRate());
        b.setPeak(freqHz, q, gainDb);
        bands.add(b);
        return this;
    }

    /** Add a low shelf. */
    public ParametricEQNode addLowShelf(double freqHz, double q, double gainDb) {
        Biquad b = new Biquad(ctx.getSampleRate());
        b.setLowShelf(freqHz, q, gainDb);
        bands.add(b);
        return this;
    }

    /** Add a high shelf. */
    public ParametricEQNode addHighShelf(double freqHz, double q, double gainDb) {
        Biquad b = new Biquad(ctx.getSampleRate());
        b.setHighShelf(freqHz, q, gainDb);
        bands.add(b);
        return this;
    }

    @Override public void process(float[] L, float[] R, int n) {
        // pull from source to a temp buffer, then run all EQ bands, then sum to output
        float[] tL = new float[n], tR = new float[n];
        source.process(tL, tR, n);
        for (int i = 0; i < bands.size(); i++) {
            bands.get(i).process(tL, tR, n);
        }
        for (int i = 0; i < n; i++) { L[i] += tL[i]; R[i] += tR[i]; }
    }

    @Override public boolean isDone() { return source.isDone(); }
}
