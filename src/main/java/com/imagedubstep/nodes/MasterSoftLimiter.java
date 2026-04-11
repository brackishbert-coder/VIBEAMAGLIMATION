package com.imagedubstep.nodes;
import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;

/** Always-on simple soft clip on master. Add once at startup. */
public class MasterSoftLimiter extends AudioNode {
    private final double drive, makeup;
	private AudioNode input;
    public MasterSoftLimiter(AudioContext ctx, double drive, double makeup,com.imagedubstep.core.AudioNode input) {
        super(ctx); this.drive = drive; this.makeup = makeup;
        this.input = input;
    }
    @Override public void process(float[] L, float[] R, int n) {
    	
    	float[] tl = new float[n], tr = new float[n];
        input.process(tl, tr, n);
    	
        for (int i=0;i<n;i++) {
            float l = (float)Math.tanh(L[i] * drive);
            float r = (float)Math.tanh(R[i] * drive);
            L[i] = (float)(l * makeup);
            R[i] = (float)(r * makeup);
        }
    }
    @Override public boolean isDone() { return false; }
}
