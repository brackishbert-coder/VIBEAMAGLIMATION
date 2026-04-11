package com.imagedubstep.som;

import java.util.Random;

/** Online Self-Organizing Map (Kohonen) with Gaussian neighborhood. */
public final class SOM {
    public final int w, h, dim;
    private final float[][] weight; // [h*w][dim]
    private final Random rng = new Random(42);

    // training schedule
    private final int totalSteps;
    private int step = 0;
    private final float initAlpha;
    private final float initSigma;

    public SOM(int w, int h, int dim, float initAlpha, float initSigma, int totalSteps) {
        this.w = w; this.h = h; this.dim = dim;
        this.initAlpha = initAlpha;
        this.initSigma = initSigma;
        this.totalSteps = Math.max(1, totalSteps);
        this.weight = new float[w*h][dim];
        // small random init
        for (int i = 0; i < w*h; i++) {
            for (int d = 0; d < dim; d++) {
                weight[i][d] = (float)(rng.nextGaussian()*0.05);
            }
        }
    }

    /** Return (x,y) of BMU for v. */
    public int[] bmu(float[] v) {
        int bi = 0;
        float best = Float.POSITIVE_INFINITY;
        for (int i = 0; i < w*h; i++) {
            float d = 0f;
            float[] wi = weight[i];
            for (int k = 0; k < dim; k++) {
                float t = v[k] - wi[k];
                d += t*t;
            }
            if (d < best) { best = d; bi = i; }
        }
        return new int[]{ bi % w, bi / w };
    }

    /** One online training step with vector v (expects ~0..1 normalized). */
    public void train(float[] v) {
        if (v == null || v.length != dim) return;
        // decay schedule
        float t = Math.min(1f, step / (float) totalSteps);
        float alpha = initAlpha * (1f - t) + 0.01f;        // keep a floor
        float sigma = (float) (initSigma * Math.pow(0.01, t)); // shrink radius

        // find BMU
        int[] xy = bmu(v);
        int bx = xy[0], by = xy[1];

        float twoSigma2 = 2f * sigma * sigma;
        int rad = Math.max(1, (int)Math.ceil(3*sigma)); // ~3σ window
        for (int y = Math.max(0, by - rad); y <= Math.min(h - 1, by + rad); y++) {
            for (int x = Math.max(0, bx - rad); x <= Math.min(w - 1, bx + rad); x++) {
                float dx = x - bx, dy = y - by;
                float g = (float)Math.exp(-(dx*dx + dy*dy)/twoSigma2);
                float lr = alpha * g;
                float[] wi = weight[y*w + x];
                for (int k = 0; k < dim; k++) {
                    wi[k] += lr * (v[k] - wi[k]);
                }
            }
        }
        step++;
    }

    /** Copy neuron weight vector at (x,y). */
    public void get(int x, int y, float[] out) {
        System.arraycopy(weight[y*w + x], 0, out, 0, dim);
    }

    /** U-Matrix: average distance to 4-neighbors. */
    public float uDistance(int x, int y) {
        float[] c = weight[y*w + x];
        float sum = 0f; int cnt = 0;
        if (x > 0)   { sum += dist2(c, weight[y*w + (x-1)]); cnt++; }
        if (x < w-1) { sum += dist2(c, weight[y*w + (x+1)]); cnt++; }
        if (y > 0)   { sum += dist2(c, weight[(y-1)*w + x]); cnt++; }
        if (y < h-1) { sum += dist2(c, weight[(y+1)*w + x]); cnt++; }
        return cnt > 0 ? (float)Math.sqrt(sum / cnt) : 0f;
    }

    private static float dist2(float[] a, float[] b) {
        float s=0f; for (int i=0;i<a.length;i++){ float d=a[i]-b[i]; s+=d*d; } return s;
    }
}
