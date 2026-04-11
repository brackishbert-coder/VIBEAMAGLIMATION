package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class ReverbNode extends AudioNode {
    private final AudioNode src;
    // 4 comb + 2 allpass per channel (Freeverb-like constants, scaled)
    private final Comb[] combL, combR;
    private final Allpass[] apL, apR;
    private volatile float wet = 0.2f, dry = 0.8f;
    private volatile float room = 0.78f;  // 0..0.98
    private volatile float damp = 0.3f;   // 0..1

    public ReverbNode(AudioContext ctx, AudioNode src) {
        super(ctx); this.src = src;
        int sr = (int)ctx.getSampleRate();
        combL = new Comb[]{ new Comb(sr,1116), new Comb(sr,1188), new Comb(sr,1277), new Comb(sr,1356) };
        combR = new Comb[]{ new Comb(sr,1116+23), new Comb(sr,1188+23), new Comb(sr,1277+23), new Comb(sr,1356+23) };
        apL = new Allpass[]{ new Allpass(sr,556), new Allpass(sr,441) };
        apR = new Allpass[]{ new Allpass(sr,556+23), new Allpass(sr,441+23) };
        updateParams();
    }

    public void setWet(double w){ wet = clamp01((float)w); dry = 1f - wet; }
    public void setRoom(double r){ room = (float)Math.max(0, Math.min(0.98, r)); updateParams(); }
    public void setDamp(double d){ damp = clamp01((float)d); updateParams(); }

    private void updateParams(){
        for (Comb c: combL) c.set(room, damp);
        for (Comb c: combR) c.set(room, damp);
    }

    @Override public void process(float[] L, float[] R, int n) {
        float[] tL = new float[n], tR = new float[n];
        src.process(tL, tR, n);

        for (int i=0;i<n;i++){
            float inp = 0.5f*(tL[i]+tR[i]);

            float accL = 0, accR = 0;
            for (Comb c: combL) accL += c.tick(inp);
            for (Comb c: combR) accR += c.tick(inp);
            for (Allpass a: apL) accL = a.tick(accL);
            for (Allpass a: apR) accR = a.tick(accR);

            L[i] += dry*tL[i] + wet*accL*0.25f;
            R[i] += dry*tR[i] + wet*accR*0.25f;
        }
    }
    @Override public boolean isDone(){ return src.isDone(); }

    private static float clamp01(float x){ return x<0?0:(x>1?1:x); }

    private static final class Comb {
        private final float[] buf; private int idx=0; private float fb, damp1, damp2, f=0;
        Comb(int sr, int size){ buf = new float[Math.max(32, (int)(size*sr/44100.0))]; }
        void set(float room, float damp){ fb = room; damp1 = damp; damp2 = 1f-damp; }
        float tick(float x){
            float y = buf[idx];
            f = y*damp1 + f*damp2;
            buf[idx] = x + f*fb;
            idx = (idx+1)%buf.length;
            return y;
        }
    }
    private static final class Allpass {
        private final float[] buf; private int idx=0;
        Allpass(int sr, int size){ buf = new float[Math.max(32, (int)(size*sr/44100.0))]; }
        float tick(float x){
            float y = buf[idx];
            float z = x + y*(-0.5f);
            buf[idx] = z;
            idx = (idx+1)%buf.length;
            return y + (-0.5f)*z;
        }
    }
}
