package com.imagedubstep.fx;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

/** Auto-wah: envelope follower modulates a resonant band/low-pass. */
public class AutoWahNode extends AudioNode {
    private final AudioNode src;
    private final Biquad bp;             // bandpass core
    private final Biquad lp;             // optional post LP tame
    private volatile float wet = 1.0f;   // 0..1
    private volatile float minHz = 300f, maxHz = 2000f;
    private volatile float q = 6.0f;     // resonance
    private volatile float sens = 1.0f;  // envelope sensitivity
    private volatile float atkMs = 6f, relMs = 120f;
    private volatile boolean postLP = true;
    private float env = 0f, atk, rel;

    public AutoWahNode(AudioContext ctx, AudioNode src) {
        super(ctx); this.src = src;
        bp = new Biquad(ctx.getSampleRate());
        lp = new Biquad(ctx.getSampleRate());
        lp.set(Biquad.Type.LOWPASS, 9000, 0.707);
        recalcTimes();
        updateFilter(minHz); // init
    }

    public void setWet(double w){ wet = clamp01((float)w); }
    public void setRange(double min, double max){ minHz=(float)min; maxHz=(float)max; }
    public void setQ(double Q){ q=(float)Math.max(0.3, Math.min(20.0, Q)); }
    public void setSensitivity(double s){ sens=(float)Math.max(0, Math.min(4, s)); }
    public void setAttackMs(double ms){ atkMs=(float)ms; recalcTimes(); }
    public void setReleaseMs(double ms){ relMs=(float)ms; recalcTimes(); }
    public void setPostLPHz(double hz){ postLP=true; lp.set(Biquad.Type.LOWPASS, hz, 0.707); }
    public void setPostLPOn(boolean on){ postLP=on; }

    private void recalcTimes(){
        atk = (float)Math.exp(-1.0/(ctx.getSampleRate()*(atkMs/1000.0)));
        rel = (float)Math.exp(-1.0/(ctx.getSampleRate()*(relMs/1000.0)));
    }
    private void updateFilter(double hz){ bp.set(Biquad.Type.BANDPASS, hz, q); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);
        for (int i=0;i<n;i++){
            float x = 0.5f*(Math.abs(tL[i])+Math.abs(tR[i])) * sens;
            env = (x > env) ? atk*env + (1- atk)*x : rel*env + (1- rel)*x;
            // map env (0..~1) to frequency range (log-ish)
            float t = clamp01(env);
            double hz = minHz * Math.pow(maxHz/minHz, t);
            updateFilter(hz);

            float[] oneL = { tL[i] }, oneR = { tR[i] };
            bp.process(oneL, oneR, 1);
            if (postLP) lp.process(oneL, oneR, 1);

            float w = wet, d = 1f - w;
            L[i] += d*tL[i] + w*oneL[0];
            R[i] += d*tR[i] + w*oneR[0];
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }

    private static float clamp01(float v){ return v<0?0:(v>1?1:v); }
}
