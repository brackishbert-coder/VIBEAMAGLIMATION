package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

public class LinePercNode extends OneShotNode {
    private final float[] buf;
    private final Biquad biquad;
    private final double volume;

    // NEW params
    private final double noiseBlend;   // 0..1  (0=tone, 1=noise)
    private final double resonanceQ;   // 0.5..20
    private final double filterHz;     // center frequency of band-pass

    // Back-compat: preserves your existing behavior (noise≈0.3, Q=5, filter from density)
    public LinePercNode(AudioContext ctx, double start, double duration,
                        double toneFreq, double density, double volume) {
        this(ctx, start, duration, toneFreq,
             200 + density * 1000.0, // legacy center
             0.3,                    // legacy noise amount
             5.0,                    // legacy Q
             density, volume);
    }

    // Extended ctor with explicit filterHz, noiseBlend, Q
    public LinePercNode(AudioContext ctx, double start, double duration,
                        double toneFreq, double filterHz,
                        double noiseBlend, double resonanceQ,
                        double density, double volume) {
        super(ctx, start, duration);

        int samples = Math.max(1, (int)Math.round(duration * ctx.getSampleRate()));
        this.buf = new float[samples];

        // Envelope shaped to (≈) reach -60 dB near the end of duration
        double sr = ctx.getSampleRate();
        double tau = Math.max(1e-3, duration / 6.91); // e^(-t/tau) -> -60 dB at t=duration
        double nb = Math.max(0.0, Math.min(1.0, noiseBlend));
        double tb = 1.0 - nb;
        for (int i = 0; i < samples; i++) {
            double t = i / sr;
            double env = Math.exp(-t / tau);
            double noise = (Math.random() * 2 - 1);
            double tone  = Math.sin(2 * Math.PI * toneFreq * t);
            // Weighted mix * density (keeps your prior mapping idea)
            double s = (nb * noise + tb * tone) * env * density;
            buf[i] = (float) s;
        }

        this.biquad = new Biquad(ctx.getSampleRate());
		this.noiseBlend = 0;
        this.filterHz = Math.max(50.0, filterHz);
        this.resonanceQ = Math.max(0.5, Math.min(20.0, resonanceQ));
        biquad.set(Biquad.Type.BANDPASS, this.filterHz, this.resonanceQ);

        this.volume = volume;
    }

    @Override
    public void process(float[] L, float[] R, int n){
        double sr = ctx.getSampleRate();
        double t0 = ctx.currentTime();
        int startIndex = (int)Math.round((startTime - t0) * sr);
        int endIndex   = (int)Math.round((stopTime  - t0) * sr);
        if (endIndex <= 0) return;
        int from = Math.max(0, startIndex);
        int to   = Math.min(n, endIndex);
        if (from >= to) return;

        float[] l = new float[n];
        int bufStart = (int)Math.max(0, Math.round((t0 - startTime) * sr));
        for (int i = from, j = bufStart; i < to && j < buf.length; i++, j++) {
            l[i] += buf[j] * (float)volume;
        }
        biquad.process(l, l, n);
        for (int i = 0; i < n; i++) { L[i] += l[i]; R[i] += l[i]; }
        if (t0 + n/sr >= stopTime) done = true;
    }
}
