// DCBlockerNode.java
package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

public class DCBlockerNode extends AudioNode {
    private final AudioNode source;
    private final Biquad hpL, hpR;
    private final float[] tmpL, tmpR;

    public DCBlockerNode(AudioContext ctx, AudioNode source) {
        super(ctx);
        this.source = source;
        hpL = new Biquad(ctx.getSampleRate());
        hpR = new Biquad(ctx.getSampleRate());
        hpL.set(Biquad.Type.HIGHPASS, 20.0, 0.707);
        hpR.set(Biquad.Type.HIGHPASS, 20.0, 0.707);
        tmpL = new float[ctx.getBlockSize()];
        tmpR = new float[ctx.getBlockSize()];
    }

    @Override
    public void process(float[] L, float[] R, int n) {
        for (int i = 0; i < n; i++) { tmpL[i] = 0; tmpR[i] = 0; }
        source.process(tmpL, tmpR, n);
        hpL.process(tmpL, tmpL, n);
        hpR.process(tmpR, tmpR, n);
        for (int i = 0; i < n; i++) { L[i] += tmpL[i]; R[i] += tmpR[i]; }
    }

    @Override
    public boolean isDone() {
        return source.isDone();
    }
}
