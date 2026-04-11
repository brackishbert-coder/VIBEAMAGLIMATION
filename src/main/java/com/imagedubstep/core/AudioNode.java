
package com.imagedubstep.core;
public abstract class AudioNode {
    protected final AudioContext ctx;
    public AudioNode(AudioContext ctx){ this.ctx = ctx; }
    public abstract void process(float[] L, float[] R, int n);
    public boolean isDone(){ return false; }
}
