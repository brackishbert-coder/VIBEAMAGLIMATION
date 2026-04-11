package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class WidenerNode extends AudioNode {
    private final AudioNode src;
    private volatile float sideGain = 1.2f; // 1.0=neutral, >1 wider, <1 narrower

    public WidenerNode(AudioContext ctx, AudioNode src) { super(ctx); this.src = src; }
    public void setSideGain(double g){ sideGain = (float)Math.max(0.0, Math.min(2.0, g)); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n]; src.process(tL,tR,n);
        float sG = sideGain;
        for (int i=0;i<n;i++){
            float M = 0.5f*(tL[i]+tR[i]);
            float S = 0.5f*(tL[i]-tR[i]) * sG;
            L[i] += M + S;
            R[i] += M - S;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }
}
