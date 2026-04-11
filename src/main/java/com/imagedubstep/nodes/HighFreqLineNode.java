package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;
import com.imagedubstep.util.PolyBLEP;

public class HighFreqLineNode extends OneShotNode {
    // existing
    private double f1, f2, volume;
    private final Biquad hp;
    private double phase1=0, phase2=0;

    // NEW
    private double oscMix = 0.5;   // 0=saw, 1=square
    private double hpCut   = 600;  // Hz

    // Back-compat: original behavior (0.5 mix, 600 Hz HP)
    public HighFreqLineNode(AudioContext ctx, double start, double duration, double f1, double f2, double volume){
        super(ctx, start, duration);
        this.f1=f1; this.f2=f2; this.volume=volume;
        this.hpCut = 600;
        hp = new Biquad(ctx.getSampleRate());
        hp.set(Biquad.Type.HIGHPASS, hpCut, 8.0);
    }

    // Extended ctor: base + detune + mix + cutoff
    public HighFreqLineNode(AudioContext ctx, double start, double duration,
                            double baseFreq, double detuneHz,
                            double oscMix, double hpCutoff, double volume) {
        super(ctx, start, duration);
        this.f1 = Math.max(50.0, baseFreq);
        this.f2 = Math.max(50.0, baseFreq + detuneHz);
        this.oscMix = Math.max(0.0, Math.min(1.0, oscMix));
        this.hpCut = Math.max(100.0, hpCutoff);
        this.volume = volume;

        hp = new Biquad(ctx.getSampleRate());
        hp.set(Biquad.Type.HIGHPASS, this.hpCut, 8.0);
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
        for(int i=from;i<to;i++){
            double inc1 = f1/sr;
            double inc2 = f2/sr;

            // phase -> 0..1
            double t1 = (phase1 / (2*Math.PI));
            double t2 = (phase2 / (2*Math.PI));

            // band-limited square
            double sq = (t1 < 0.5 ? 1.0 : -1.0);
            sq += PolyBLEP.polyBLEP(t1, inc1) - PolyBLEP.polyBLEP((t1+0.5)%1.0, inc1);

            // band-limited saw
            double saw = 2.0*(t2 - Math.floor(t2 + 0.5));
            saw -= PolyBLEP.polyBLEP(t2, inc2);

            // NEW: user-mix
            double core = (1.0 - oscMix) * saw + (oscMix) * sq;

            l[i] += (float)(core * volume);

            phase1 += 2*Math.PI*f1/sr; if(phase1 > 2*Math.PI) phase1 -= 2*Math.PI;
            phase2 += 2*Math.PI*f2/sr; if(phase2 > 2*Math.PI) phase2 -= 2*Math.PI;
        }

        // NEW: user HP cutoff (with update)
        hp.updateIfChanged(Biquad.Type.HIGHPASS, hpCut, 8.0);
        hp.process(l, l, n);

        for(int i=0;i<n;i++){ L[i]+=l[i]; R[i]+=l[i]; }
        if(t0 + n/sr >= stopTime) done = true;
    }
}
