
package com.imagedubstep.nodes;
import com.imagedubstep.core.*;
public abstract class OneShotNode extends AudioNode {
    protected final double startTime;
    protected final double stopTime;
    protected boolean done=false;
    public OneShotNode(AudioContext ctx, double startTime, double duration){
        super(ctx); this.startTime = startTime; this.stopTime = startTime + duration;
    }
    @Override public boolean isDone(){ return done; }
}
