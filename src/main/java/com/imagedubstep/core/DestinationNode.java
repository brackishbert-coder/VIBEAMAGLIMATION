
package com.imagedubstep.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.imagedubstep.util.SoftClip;

public class DestinationNode extends AudioNode {
    private final List<AudioNode> sources = new ArrayList<>();
    private volatile double masterGain = 0.9;
    private float lastGain = 0.9f;

    public DestinationNode(AudioContext ctx){ super(ctx); }
    public void addSource(AudioNode node){ synchronized (sources){ sources.add(node); } }
    public void setMasterGain(double g){ masterGain = Math.max(0, Math.min(100, g)); }

    @Override
    public void process(float[] L, float[] R, int n) {
        for(int i=0;i<n;i++){ L[i]=0; R[i]=0; }
        synchronized (sources){
            Iterator<AudioNode> it = sources.iterator();
            while(it.hasNext()){
                AudioNode node = it.next();
                node.process(L, R, n);
                if(node.isDone()) it.remove();
            }
        }
        float g0 = lastGain;
        float g1 = (float) masterGain;
        float dg = (g1 - g0) / n;
        float g  = g0;

        for (int i = 0; i < n; i++) {
            float l = L[i] * g;
            float r = R[i] * g;
            L[i] = SoftClip.clip(l);
            R[i] = SoftClip.clip(r);
            g += dg;
        }
        lastGain = g1;

    }
}
