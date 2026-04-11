package com.imagedubstep.fx;

import com.imagedubstep.core.*;

/** Classic multi-stage phaser using first-order all-pass sections and an LFO. */
public class PhaserNode extends AudioNode {
    private final AudioNode src;
    private final Stage[] Ls, Rs;
    private volatile int stages = 6;          // 2..8
    private volatile float rate = 0.3f;       // Hz
    private volatile float depth = 0.9f;      // 0..1
    private volatile float fb = 0.25f;        // -0.95..0.95
    private volatile float wet = 0.35f;       // 0..1
    private float ph=0, fbL=0, fbR=0;

    public PhaserNode(AudioContext ctx, AudioNode src) {
        super(ctx); this.src = src;
        Ls = new Stage[]{ new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage()};
        Rs = new Stage[]{ new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage(),new Stage()};
    }

    public void setStages(int n){ stages = Math.max(2, Math.min(8, n)); }
    public void setRate(double hz){ rate = (float)hz; }
    public void setDepth(double d){ depth = (float)Math.max(0, Math.min(1, d)); }
    public void setFeedback(double f){ fb = (float)Math.max(-0.95, Math.min(0.95, f)); }
    public void setWet(double w){ wet = (float)Math.max(0, Math.min(1, w)); }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n]; src.process(tL,tR,n);
        float sr = (float)ctx.getSampleRate();
        for (int i=0;i<n;i++){
            // LFO → allpass coefficient a in ~[0.1..0.9]
            float lfo = (float)Math.sin(2*Math.PI*ph); ph += rate/sr; if (ph>=1f) ph-=1f;
            float a = 0.5f + 0.4f*(lfo*depth); // move poles

            float yL = tL[i] + fbL*fb;
            float yR = tR[i] + fbR*fb;
            for (int s=0; s<stages; s++){ yL = Ls[s].tick(yL, a); yR = Rs[s].tick(yR, a); }
            fbL = yL; fbR = yR;

            float w = wet, d = 1f - w;
            L[i] += d*tL[i] + w*yL;
            R[i] += d*tR[i] + w*yR;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }

    private static final class Stage {
        float z=0;
        float tick(float x, float a){
            // 1st-order AP: y = -a*x + z + a*y, re-arranged in one-pole form
            float y = a*(x - z) + z; // equivalent stable formulation
            z = y + x * (-a);
            return y;
        }
    }
}
