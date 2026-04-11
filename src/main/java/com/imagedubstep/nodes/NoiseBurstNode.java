
package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

public class NoiseBurstNode extends OneShotNode {
    private final float[] buf;
    private final Biquad biquad;
    private final Biquad.Type filterType;
    private final double cutoff, q;
    private final double volume;

    public NoiseBurstNode(AudioContext ctx, double start, double duration, int samples, Biquad.Type type, double cutoff, double q, double volume){
        super(ctx, start, duration);
        this.buf = new float[Math.max(1, samples)];
        double sr = ctx.getSampleRate();
        for(int i=0;i<this.buf.length;i++){
            double t = (double)i / sr;
            double env = Math.exp(-t / Math.max(1e-4, duration*0.8));
            this.buf[i] = (float)((Math.random()*2-1) * env);
        }
        this.biquad = new Biquad(ctx.getSampleRate());
        this.filterType = type; this.cutoff = cutoff; this.q = q; this.volume = volume;
    }

    @Override
    public void process(float[] L, float[] R, int n){
        double sr = ctx.getSampleRate();
        double t0 = ctx.currentTime();
        int startIndex = (int)Math.round((startTime - t0) * sr);
        int endIndex   = (int)Math.round((stopTime - t0) * sr);
        if(endIndex <= 0) return;
        int from = Math.max(0, startIndex);
        int to   = Math.min(n, endIndex);
        if(from >= to) return;

        float[] l = new float[n];
        biquad.updateIfChanged(filterType, cutoff, q);
        int bufStart = (int)Math.max(0, Math.round((t0 - startTime) * sr));
        for(int i=from, j=bufStart; i<to && j<buf.length; i++, j++){
            l[i] += buf[j] * (float)volume;
        }
        biquad.process(l, l, n);
        for(int i=0;i<n;i++){ L[i]+=l[i]; R[i]+=l[i]; }
        if(t0 + n/sr >= stopTime) done = true;
    }
}
