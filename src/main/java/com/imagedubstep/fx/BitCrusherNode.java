package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class BitCrusherNode extends AudioNode {
    private final AudioNode src;
    private volatile int bits = 10;         // 4..16
    private volatile int holdSamples = 2;   // >=1
    private volatile float wet=0.25f;
    private float hL=0, hR=0; private int c=0;

    public BitCrusherNode(AudioContext ctx, AudioNode src){ super(ctx); this.src=src; }
    public void setBits(int b){ bits=Math.max(4, Math.min(16,b)); }
    public void setDownsample(int n){ holdSamples=Math.max(1,n); }
    public void setWet(double w){ wet=(float)Math.max(0, Math.min(1,w)); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL=new float[n], tR=new float[n]; src.process(tL,tR,n);
        float step = (float)Math.pow(2, 16 - bits); // crude scale for quantization granularity
        float w=wet, d=1f-w;
        for(int i=0;i<n;i++){
            if (c==0){ // sample
                // quantize roughly by scaling, rounding, and back
                hL = Math.round(tL[i]*step)/step;
                hR = Math.round(tR[i]*step)/step;
                c = holdSamples;
            }
            c--;
            L[i] += tL[i]*d + hL*w;
            R[i] += tR[i]*d + hR*w;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }
}
