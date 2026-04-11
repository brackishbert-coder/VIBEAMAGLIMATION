package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.FastSin;

public class KickNode extends OneShotNode {
	// musical params
	private final double fStart, fEnd; // Hz sweep
	private final double attack; // seconds
	private final double decay; // seconds
	private final double volume; // 0..1

	// NEW params
	private final double drive; // 1.0..3.0 typical
	private final double toneMix; // 0..1 (0=pure sine, 1=harmonic-rich)
	private static final double END_FADE_S = 0.004; // 4 ms

	// NEW: inline one-pole DC blocker y[n] = x[n] - x[n-1] + r*y[n-1]
	// r ≈ e^(-2*pi*fc/sr) for fc ~ 20 Hz
	private final double hpR;
	private double hp_x1 = 0.0, hp_y1 = 0.0;

	// osc state
	private double phase = 0.0;

	private static final double TWO_PI = 2.0 * Math.PI;

	// Backward-compatible ctor (uses tasteful defaults)
	public KickNode(AudioContext ctx, double start, double duration, double startFreq, double endFreq, double attack,
			double decay, double volume) {
		this(ctx, start, duration, startFreq, endFreq, attack, decay, volume, 1.7 /* drive */, 0.25 /* toneMix */);
	}

	public KickNode(AudioContext ctx, double start, double duration, double startFreq, double endFreq, double attack,
			double decay, double volume, double drive, double toneMix) {
		super(ctx, start, Math.max(0.08, duration));

		this.fStart = clamp(startFreq, 50.0, 160.0);
		this.fEnd = clamp(endFreq, 32.0, 90.0);

		this.attack = Math.max(0.005, attack);
		this.decay = Math.max(0.18, decay);
		this.volume = clamp(volume, 0.0, 1.0);

		this.drive = clamp(drive, 1.0, 3.5);
		this.toneMix = clamp(toneMix, 0.0, 1.0);

// DC blocker coefficient for ~20 Hz
		double sr = ctx.getSampleRate();
		this.hpR = Math.exp(-2.0 * Math.PI * 20.0 / sr);
	}

	@Override
	public void process(float[] L, float[] R, int n) {
		final double sr = ctx.getSampleRate();
		final double tNow = ctx.currentTime();

		int startIndex = (int) Math.round((startTime - tNow) * sr);
		int endIndex = (int) Math.round((stopTime - tNow) * sr);
		if (endIndex <= 0)
			return;
		int from = Math.max(0, startIndex);
		int to = Math.min(n, endIndex);
		if (from >= to) {
			if (tNow + n / sr >= stopTime)
				done = true;
			return;
		}

		final double total = Math.max(1e-6, stopTime - startTime);
		final double k = Math.log(fEnd / fStart) / total; // exponential sweep
		final double TWO_PI = 2.0 * Math.PI;

		for (int i = from; i < to; i++) {
			double tAbs = tNow + i / sr;
			double rel = tAbs - startTime;
			double x = rel / total;

			// Base envelope (your shapes)
			double win = Math.sin(Math.PI * x);
			win *= win;
			double thump = Math.exp(-2.5 * x);
			double atkShape = (rel <= attack) ? Math.pow(rel / attack, 2.0) : 1.0;
			double env = win * thump * atkShape;

			// --- NEW: end-cap to force zero at stopTime (prevents stop click)
			double tLeft = stopTime - tAbs;
			double endcap = (tLeft <= 0.0) ? 0.0 : (tLeft >= END_FADE_S) ? 1.0 : Math.pow(tLeft / END_FADE_S, 2.0);
			env *= endcap;

			// Sweep
			double f = fStart * Math.exp(k * rel);
			double dp = TWO_PI * f / sr;
			phase += dp;
			if (phase > 1e9)
				phase -= 1e9;

			double s = FastSin.sin(phase);

			// Tone mix (adds odd harmonics)
			double harmonic = Math.tanh(2.5 * s);
			double core = (1.0 - toneMix) * s + toneMix * harmonic;

			// Gain + soft clip
			double y = Math.tanh(drive * (volume * env * core));

			// --- NEW: inline DC blocker ---
			double hp_y = (y - hp_x1) + hpR * hp_y1;
			hp_x1 = y;
			hp_y1 = hp_y;

			float samp = (float) hp_y;
			L[i] += samp;
			R[i] += samp;
		}

		if (tNow + n / sr >= stopTime)
			done = true;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
