package com.imagedubstep.nodes;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.util.PolyBLEP;
import com.imagedubstep.util.TptSvf;

public class BassWobbleNode extends OneShotNode {

    // React parity constants
    private static final double DURATION_SEC = 0.5;   // osc/lfo stop at time + 0.5
    private static final double LFO_DEPTH_HZ = 200.0; // lfoGain.gain = 200
    private static final double Q_FIXED      = 10.0;  // filter.Q = 10
    private static final double GAIN_TARGET  = 0.01;  // gain → 0.01 at end
    private static final double END_FADE_S = 0.004;
    private static final double DRIVE_DB   = 9.0;

    private final double drive = Math.pow(10.0, DRIVE_DB/20.0);
    private final double baseFreq;     // osc.frequency (Hz)
    private final double baseCutoff;   // filter.frequency (Hz)
    private final double lfoRate;      // LFO Hz
    private final double startGain;    // initial gain = "intensity * bassVolume" from caller

    private final TptSvf svf;          // 2-pole TPT state-variable filter (lowpass)
    private double phase01 = 0.0;      // osc phase  [0..1)
    private double lfo01   = 0.0;      // lfo phase  [0..1)

    // Back-compat ctor: extra params are ignored to match React behavior
    public BassWobbleNode(AudioContext ctx,
                          double start, double duration,
                          double baseFreq, double baseCutoff,
                          double lfoRate, double volume) {
        this(ctx, start, /*duration ignored*/ DURATION_SEC,
             baseFreq, baseCutoff, lfoRate, volume,
             /*ignored*/ 0.0, /*ignored*/ 0.0, /*ignored*/ null);
    }

    // Extended ctor kept for API compatibility, but extra fields are ignored on purpose
    public BassWobbleNode(AudioContext ctx,
                          double start, double duration,
                          double baseFreq, double baseCutoff,
                          double lfoRate, double volume,
                          double lfoDepthHz, double resonanceQ, Waveform unused) {
        super(ctx, start, DURATION_SEC); // force 0.5s to mirror React
        this.baseFreq   = Math.max(10.0, baseFreq);
        this.baseCutoff = Math.max(10.0, baseCutoff);
        this.lfoRate    = Math.max(0.01, lfoRate);
        this.startGain  = Math.max(0.0, Math.min(1.0, volume));
        this.svf        = new TptSvf(ctx.getSampleRate());
        
    }

    public enum Waveform { SAW, SQUARE, TRI } // not used (React is always saw)

   @Override
    public void process(float[] L, float[] R, int n) {
        final double sr   = ctx.getSampleRate();
        final double tNow = ctx.currentTime();

        int startIndex = (int) Math.round((startTime - tNow) * sr);
        int endIndex   = (int) Math.round((stopTime  - tNow) * sr);
        int from = Math.max(0, startIndex);
        int to   = Math.min(n, endIndex);
        if (to <= from) {
            if (tNow + n / sr >= stopTime) done = true;
            return;
        }

        final double dtOsc = baseFreq / sr;
        final double dtLfo = lfoRate  / sr;
        final double dur    = DURATION_SEC;
        final double expoBase = (GAIN_TARGET <= 0 ? 1e-6 : (GAIN_TARGET / Math.max(1e-6, startGain)));

        for (int i = from; i < to; i++) {
            double tAbs = tNow + i / sr;
            double rel  = tAbs - startTime;          // 0..dur
            double x    = Math.max(0.0, Math.min(1.0, rel / dur));

            // oscillator (saw + PolyBLEP)
            phase01 += dtOsc;
            if (phase01 >= 1.0) phase01 -= 1.0;
            double y = 2.0 * phase01 - 1.0;
            y -= PolyBLEP.polyBLEP(phase01, dtOsc);

            // LFO → cutoff
            lfo01 += dtLfo;
            if (lfo01 >= 1.0) lfo01 -= 1.0;
            double cutoff = baseCutoff + Math.sin(2.0 * Math.PI * lfo01) * LFO_DEPTH_HZ;
            if (cutoff < 10.0) cutoff = 10.0;
            double nyq = sr * 0.5;
            if (cutoff > nyq * 0.9) cutoff = nyq * 0.9;

            // 2-pole LPF
            double filtered = svf.processLowMod(y, cutoff, Q_FIXED);

            // exact expo gain to target
            double gain = startGain * Math.pow(expoBase, x);

            // --- NEW: tiny end fade so we *actually* hit zero at stopTime ---
            double tLeft  = stopTime - tAbs;
            double endcap = (tLeft <= 0.0) ? 0.0
                           : (tLeft >= END_FADE_S) ? 1.0
                           : Math.pow(tLeft / END_FADE_S, 2.0);

            // --- NEW: gentle soft clip for loudness without crackle ---
            double sample = Math.tanh(filtered * gain * drive) * 0.98;

            float out = (float)(sample * endcap);
            L[i] += out;
            R[i] += out;
        }

        if (tNow + n / sr >= stopTime) done = true;
    }
}
