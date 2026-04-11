package com.imagedubstep.engine2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BeatGenerator {
    private final Random rng;

    public BeatGenerator() { this(new Random()); }
    public BeatGenerator(Random rng) { this.rng = rng; }

    public BeatPattern generate(List<SOMNeuron> neurons, int steps) {
        if (neurons == null || neurons.isEmpty()) {
            // Fallback: create a simple 4-on-the-floor if no neurons provided.
            BeatPattern bp = new BeatPattern(steps);
            for (int i = 0; i < steps; i++) {
                boolean downbeat = (i % 4) == 0;
                bp.kick[i] = downbeat;
                bp.kickVel[i] = downbeat ? 0.9 : 0.0;
                bp.snare[i] = (i % 8) == 4;
                bp.snareVel[i] = bp.snare[i] ? 0.8 : 0.0;
                bp.hihat[i] = (i % 2) == 0;
                bp.hihatVel[i] = bp.hihat[i] ? 0.4 : 0.0;
                bp.bass[i] = (i % 2) == 0;
                bp.bassVel[i] = bp.bass[i] ? 0.5 : 0.0;
            }
            return bp;
        }

        BeatPattern bp = new BeatPattern(steps);

        // Filter “relevant” neurons per instrument (mirrors the JS heuristics).
        List<SOMNeuron> kickPool  = filtered(neurons, n -> n.w[3] > 0.3); // brightness
        List<SOMNeuron> snarePool = filtered(neurons, n -> n.w[4] > 0.4); // contrast
        List<SOMNeuron> hhatPool  = filtered(neurons, n -> n.w[2] > 0.5); // blue
        List<SOMNeuron> bassPool  = filtered(neurons, n -> n.w[0] > 0.4); // red
        if (kickPool.isEmpty())  kickPool  = neurons;
        if (snarePool.isEmpty()) snarePool = neurons;
        if (hhatPool.isEmpty())  hhatPool  = neurons;
        if (bassPool.isEmpty())  bassPool  = neurons;

        // Generate per-step activations using the same probability shaping as the JS.
        for (int i = 0; i < steps; i++) {
            // Pick a neuron along the pool using linear mapping like the JS.
            SOMNeuron nk = kickPool.get((int)Math.floor((i / (double)steps) * kickPool.size()));
            SOMNeuron ns = snarePool.get((int)Math.floor((i / (double)steps) * snarePool.size()));
            SOMNeuron nh = hhatPool.get((int)Math.floor((i / (double)steps) * hhatPool.size()));
            SOMNeuron nb = bassPool.get((int)Math.floor((i / (double)steps) * bassPool.size()));

            // JS-ish probability curves:
            double pKick  = nk.w[3] * 0.7;                     // brightness
            if ((i % 4) == 0) pKick += 0.3;                    // downbeats
            double pSnare = ns.w[4] * 0.5;                     // contrast
            if ((i % 8) == 4) pSnare += 0.4;                   // backbeat
            double pHat   = nh.w[2] * 0.6 + 0.2;               // blue-ish, frequent
            double pBass  = nb.w[0] * 0.4;                     // red-ish
            if ((i % 2) == 0) pBass += 0.2;                    // even steps

            bp.kick[i]  = roll(pKick);
            bp.snare[i] = roll(pSnare);
            bp.hihat[i] = roll(pHat);
            bp.bass[i]  = roll(pBass);

            // Velocity suggestion ~ probability clamped 0..1
            bp.kickVel[i]  = clamp01(pKick);
            bp.snareVel[i] = clamp01(pSnare);
            bp.hihatVel[i] = clamp01(pHat);
            bp.bassVel[i]  = clamp01(pBass);
        }

        return bp;
    }

    private boolean roll(double p) { return rng.nextDouble() < clamp01(p); }
    private double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private static List<SOMNeuron> filtered(List<SOMNeuron> src, java.util.function.Predicate<SOMNeuron> pred) {
        ArrayList<SOMNeuron> out = new ArrayList<>();
        for (SOMNeuron n : src) if (pred.test(n)) out.add(n);
        return out;
    }
}
