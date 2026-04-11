package com.imagedubstep.fx;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;
import com.imagedubstep.util.Biquad;

/** Stereo echo with feedback and simple HP/LP in the loop. */
public class EchoNode extends AudioNode {
    private final AudioNode src;
    private final float[] dlL, dlR;
    private int wp = 0;
    private volatile float wet = 0.35f;    // 0..1
    private volatile float fb  = 0.35f;    // 0..0.95
    private volatile int   delaySamp;      // samples
    private final Biquad fbHP, fbLP;       // tone in feedback loop

    public EchoNode(AudioContext ctx, AudioNode src, double timeMs, double feedback, double wet) {
        super(ctx);
        this.src = src;
        int maxSamp = Math.max(1, (int)(ctx.getSampleRate()*2.5)); // up to 2.5s delay
        dlL = new float[maxSamp];
        dlR = new float[maxSamp];
        setTimeMs(timeMs);
        setFeedback(feedback);
        setWet(wet);
        fbHP = new Biquad(ctx.getSampleRate());
        fbLP = new Biquad(ctx.getSampleRate());
        fbHP.set(Biquad.Type.HIGHPASS, 120.0, 0.707);
        fbLP.set(Biquad.Type.LOWPASS, 9000.0, 0.707);
    }

    public void setTimeMs(double ms) {
        int s = Math.max(1, (int)Math.round(ctx.getSampleRate()*ms/1000.0));
        this.delaySamp = Math.min(s, dlL.length-1);
    }
    public void setFeedback(double f) { this.fb = (float)Math.max(0.0, Math.min(0.95, f)); }
    public void setWet(double w)      { this.wet = (float)Math.max(0.0, Math.min(1.0, w)); }
    public void setFeedbackHP(double hz) { fbHP.set(Biquad.Type.HIGHPASS, hz, 0.707); }
    public void setFeedbackLP(double hz) { fbLP.set(Biquad.Type.LOWPASS , hz, 0.707); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);

        float wet = this.wet, fb = this.fb;
        int d = this.delaySamp, len = dlL.length;

        for (int i = 0; i < n; i++) {
            int rp = (wp - d + len) % len;
            float echoL = dlL[rp], echoR = dlR[rp];

            // write input + feedback into delay
            float inL = tL[i] + echoL * fb;
            float inR = tR[i] + echoR * fb;
            dlL[wp] = inL; dlR[wp] = inR;
            wp = (wp + 1) % len;
            // quick tonal shaping on the feedback path
            float[] oneL = { echoL }, oneR = { echoR };
            fbHP.process(oneL, oneR, 1);
            fbLP.process(oneL, oneR, 1);
            echoL = oneL[0]; echoR = oneR[0];

            // mix
            L[i] += tL[i] * (1f - wet) + echoL * wet;
            R[i] += tR[i] * (1f - wet) + echoR * wet;
        }
    }
    @Override public boolean isDone() { return src.isDone(); }
}
