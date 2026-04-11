package com.imagedubstep.audio;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;

public class MixerChannel extends AudioNode {
    private final java.util.List<AudioNode> sources = new java.util.ArrayList<>();
    private double gain = 1.0;

    public MixerChannel(AudioContext ctx) { super(ctx); }

    public void addSource(AudioNode n) {
        synchronized(sources) { sources.add(n); }
    }
    public void setGain(double g) { gain = g; }

    @Override
    public void process(float[] L, float[] R, int n) {
        float[] tmpL = new float[n];
        float[] tmpR = new float[n];
        synchronized(sources) {
            var it = sources.iterator();
            while (it.hasNext()) {
                AudioNode node = it.next();
                node.process(tmpL, tmpR, n);
                if (node.isDone()) it.remove();
            }
        }
        for (int i=0; i<n; i++) {
            L[i] += tmpL[i] * gain;
            R[i] += tmpR[i] * gain;
        }
    }
}
