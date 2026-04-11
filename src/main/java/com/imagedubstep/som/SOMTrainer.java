package com.imagedubstep.som;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Small queue + trainer. Call drainSome(...) regularly (e.g., in AnimationTimer). */
public final class SOMTrainer {
    public final SOM som;
    private final ConcurrentLinkedQueue<float[]> queue = new ConcurrentLinkedQueue<>();

    public SOMTrainer(SOM som){ this.som = som; }

    public void submit(float[] v){
        if (v != null && v.length == som.dim) queue.add(v);
    }

    /** Train up to max vectors this call. */
    public void drainSome(int max){
        for (int i=0;i<max;i++){
            float[] v = queue.poll();
            if (v == null) break;
            som.train(v);
        }
    }
}
