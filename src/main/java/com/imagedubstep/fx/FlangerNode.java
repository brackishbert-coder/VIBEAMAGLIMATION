package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class FlangerNode extends AudioNode {
    private final AudioNode src;
    private final float[] dlL, dlR; private int wp=0;
    private volatile float baseMs = 2.0f, depthMs = 1.2f, rate=0.25f, fb=0.3f, wet=0.4f;
    private float ph=0;

    public FlangerNode(AudioContext ctx, AudioNode src){
        super(ctx); this.src=src;
        int maxSamp = Math.max(1, (int)(ctx.getSampleRate()*0.02)); // 20ms
        dlL = new float[maxSamp]; dlR = new float[maxSamp];
    }
    public void setBaseMs(double ms){ baseMs=(float)ms; }
    public void setDepthMs(double ms){ depthMs=(float)ms; }
    public void setRate(double hz){ rate=(float)hz; }
    public void setFeedback(double f){ fb=(float)Math.max(-0.95, Math.min(0.95, f)); }
    public void setWet(double w){ wet=(float)Math.max(0, Math.min(1, w)); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n]; src.process(tL,tR,n);
        float sr = (float)ctx.getSampleRate();
        int len = dlL.length; float wet=this.wet, fb=this.fb;
        for (int i=0;i<n;i++){
            float mod = (float)(baseMs + depthMs*(0.5*Math.sin(2*Math.PI*ph)+0.5));
            ph += rate/sr; if (ph>=1f) ph-=1f;
            int d = Math.max(1, Math.min(len-1, (int)Math.round(sr*mod/1000.0)));

            int rp = (wp - d + len) % len;
            float eL = dlL[rp], eR = dlR[rp];
            dlL[wp] = tL[i] + eL*fb; dlR[wp] = tR[i] + eR*fb;
            wp = (wp+1)%len;

            L[i] += tL[i]*(1f-wet) + eL*wet;
            R[i] += tR[i]*(1f-wet) + eR*wet;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }
}
