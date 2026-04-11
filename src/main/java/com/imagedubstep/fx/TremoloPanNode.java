package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class TremoloPanNode extends AudioNode {
    private final AudioNode src;
    private volatile float tremDepth=0.35f, tremRate=4.0f; // Hz
    private volatile float panDepth=0.5f,   panRate=0.25f; // Hz
    private float phT=0, phP=0;

    public TremoloPanNode(AudioContext ctx, AudioNode src){ super(ctx); this.src=src; }

    public void setTremolo(double depth, double rateHz){ tremDepth=(float)depth; tremRate=(float)rateHz; }
    public void setPan(double depth, double rateHz){ panDepth=(float)depth; panRate=(float)rateHz; }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL=new float[n], tR=new float[n]; src.process(tL,tR,n);
        float sr=(float)ctx.getSampleRate();
        for(int i=0;i<n;i++){
            float trem = 1f - tremDepth*0.5f + tremDepth*0.5f*(float)Math.sin(2*Math.PI*phT);
            float pan  = panDepth*(float)Math.sin(2*Math.PI*phP); // -1..+1
            phT += tremRate/sr; if (phT>=1f) phT-=1f;
            phP += panRate /sr; if (phP>=1f) phP-=1f;

            float l = trem * (float)Math.sqrt(0.5*(1 - pan)); // equal-power pan
            float r = trem * (float)Math.sqrt(0.5*(1 + pan));

            L[i] += tL[i]*l;
            R[i] += tR[i]*r;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }
}
