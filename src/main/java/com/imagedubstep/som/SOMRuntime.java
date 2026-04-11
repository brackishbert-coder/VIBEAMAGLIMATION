package com.imagedubstep.som;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background trainer with pooled feature buffers to avoid GC spikes. */
public final class SOMRuntime {
    public final SOM som;

    private final ConcurrentLinkedQueue<float[]> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]>   bmuEvents = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<float[]> pool = new ArrayDeque<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    // knobs
    private volatile int maxBatchPerTick = 32;  // lower to reduce CPU bursts
    private volatile int bmuPostEvery    = 8;
    private volatile int idleSleepMs     = 3;

    public SOMRuntime(SOM som){
        this.som = som;
        // preallocate a few reusable feature buffers
        for (int i=0;i<128;i++) pool.add(new float[som.dim]);
    }

    /** Audio-safe: copies 'v' into a pooled buffer; never allocates if pool has room. */
    public void submit(float[] v){
        if (v == null || v.length != som.dim) return;
        float[] buf = pool.pollFirst();
        if (buf == null) buf = new float[som.dim]; // fallback, rare
        System.arraycopy(v, 0, buf, 0, som.dim);
        queue.offer(buf);
    }

    /** UI polls recent BMUs to highlight cells. */
    public int[] pollBMU(){ return bmuEvents.poll(); }

    public void start(){
        if (running.getAndSet(true)) return;
        worker = new Thread(this::loop, "SOM-Worker");
        try { worker.setDaemon(true); worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY-2)); } catch(Throwable ignore){}
        worker.start();
    }
    public void stop(){
        running.set(false);
        if (worker != null) try { worker.join(200); } catch (InterruptedException ignored) {}
        worker = null;
    }

    private void loop(){
        int trainedSincePost = 0;
        while (running.get()){
            int n = 0;
            float[] last = null;
            for (; n < maxBatchPerTick; n++){
                float[] feat = queue.poll();
                if (feat == null) break;
                som.train(feat);
                last = feat;
                // recycle buffer back to pool
                pool.offerLast(feat);

                if (++trainedSincePost >= bmuPostEvery){
                    int[] b = som.bmu(last);
                    // reuse small int[] objects too, but they are tiny; ok to allocate few
                    bmuEvents.offer(b);
                    trainedSincePost = 0;
                }
            }
            if (n == 0) {
                try { Thread.sleep(idleSleepMs); } catch (InterruptedException ignored) {}
            }
        }
    }

    // live tuning
    public void setMaxBatchPerTick(int n){ maxBatchPerTick = Math.max(1, n); }
    public void setBmuPostEvery(int n){ bmuPostEvery = Math.max(1, n); }
    public void setIdleSleepMs(int ms){ idleSleepMs = Math.max(0, ms); }
}
