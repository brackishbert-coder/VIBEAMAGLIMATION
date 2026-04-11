package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class ChorusNode extends AudioNode {
    private final AudioNode src;
    private final float[] dlL, dlR;
    private int wp=0;
    private volatile float wet=0.25f, depthSamp, rate; // depth in samples, rate in Hz
    private float ph=0;

    public ChorusNode(AudioContext ctx, AudioNode src, double depthMs, double rateHz, double wet) {
        super(ctx);
        this.src = src;
        int maxSamp = Math.max(1, (int)(ctx.getSampleRate()*0.03)); // 30ms line
        dlL = new float[maxSamp]; dlR = new float[maxSamp];
        setDepthMs(depthMs); setRate(rateHz); setWet(wet);
    }
    public void setDepthMs(double ms){ depthSamp = (float)(ctx.getSampleRate()*ms/1000.0); }
    public void setRate(double hz){ rate = (float)hz; }
    public void setWet(double w){ wet = (float)Math.max(0, Math.min(1, w)); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n]; src.process(tL,tR,n);
        float sr = (float)ctx.getSampleRate();
        for (int i=0;i<n;i++){
            // write input
            dlL[wp] = tL[i]; dlR[wp] = tR[i];
            // LFO mod
            float d = 6f + depthSamp*(0.5f*(float)Math.sin(2*Math.PI*ph)+0.5f); // base ~6samp plus mod
            ph += rate/sr; if (ph>=1f) ph-=1f;

            int len = dlL.length;
            int rp = (int)(wp - d + len) % len;
            float cL = dlL[rp], cR = dlR[rp];
            wp = (wp+1)%len;

            // mix
            L[i] += tL[i]*(1f-wet) + cL*wet;
            R[i] += tR[i]*(1f-wet) + cR*wet;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }
}
