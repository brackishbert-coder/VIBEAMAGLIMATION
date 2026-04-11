package com.imagedubstep.engine;

import java.util.ArrayList;
import java.util.List;

import com.imagedubstep.audio.MixerChannel;
import com.imagedubstep.core.AudioContext;
import com.imagedubstep.dynamics.SidechainParams;
import com.imagedubstep.filter.FilterChainNode;
import com.imagedubstep.filter.LowMonoizerNode;
import com.imagedubstep.nodes.BassWobbleNode;
import com.imagedubstep.nodes.HighFreqLineNode;
import com.imagedubstep.nodes.KickNode;
import com.imagedubstep.nodes.LinePercNode;
import com.imagedubstep.nodes.NoiseBurstNode;
import com.imagedubstep.nodes.ParametricEQNode;
import com.imagedubstep.nodes.SubBassNode;
import com.imagedubstep.som.SOM;
import com.imagedubstep.som.SOMRuntime;
import com.imagedubstep.som.SOMTrainer;
import com.imagedubstep.som.SOMView;
import com.imagedubstep.util.Biquad;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

public class VectorEngine  {
	public double subBassBrightnessThreshold = 0.4;
	public double subBassBlueTreshold = 0.6;
	public double kickEnergyThreshold = 0.3;
	public double kickRedThreshold = 0.40;
	public  double freqBrightnessThreshold = 0.48;
	public  double freqLineDensityThreshold = 0.58;
	public double lineLineDensityThreshold = 0.28;
	public  double hiHatSaturationThreshold = 0.6;
	public  double snareGreenThreshold = 0.5;
	public double midTakenBrightnessThreshold = 0.2;
	public double midTakenBlueThreshold = 0.3;
	public double wantBassbrightnessThreshold = 0.2;
	public double wantBassBlueThreshold = 0.3;

	public static double midTakenFreqBase = 20.0;
	public static double wantBassFrequencyBrightnessBase = 20.0;
	public static double blueBassAmpOverdrive2 = 0.4;
	public static double blueBassAmp2 = 0.6;
	public static double subBaseDecayOverdrive = 0.55;
	public static double energyKickAmp2 = 0.3;
	public static double redKickAmp2 = 0.4;
	public static double kickAmpBase = 0.80;
	public static double bassAmpOverdrive = 0.45;
	public static double blueBassAmpOverdrive = 0.4;
	public static double blueBassAmp = 0.6;
	public double energyKickAmp = 0.3;
	public double redKickAmp = 0.7;

	public static List<Vector> vectors = new ArrayList<>();
	public ImageView imageView;
	public boolean loopEnabled = true;
	public volatile boolean analysisQueued = false;
	public static float[] prevLuma64 = null;
	public static int prevLumaW = 0, prevLumaH = 0;
	// State

	public AudioContext ctx;
	public static double winSize = 0.5; // fraction of image min(width,height)
	public static double winX = 0.0; // 0..1 (left to right)
	public static double winY = 0.0; // 0..1 (top to bottom)

	public int tempo = 140;
	// --- Mixing & Layering (defaults tuned for 'rich' sound) ---
	public boolean mixLowBlend = true; // Kick+Sub can co-exist
	public boolean mixHighTwoLayers = true; // Allow Line+High (and/or Hat) in same step

	// Times (ms)
	public double mixSubNudgeMs = 18; // Sub starts after kick transient
	public double mixSubDuckMs = 90; // Sub duck window length
	public double mixHighStaggerMs = 7; // Stagger between Line Perc and High Freq

	// Trims (linear scale ~ -dB)
	public double mixLowBlendTrim = 1.0; // ~ -1.4 dB on Kick and Sub when blended
	public double mixHighTrim = 1.0; // ~ -1.4 dB on Line/High layers
	public double mixHatTrim = 0.90; // ~ -0.9 dB on hats when layered
	public int bassPitch = 80;

	public boolean kickEnabled = true;
	public double kickVolume = 0.8, kickAttack = 0.2, kickDecay = 0.3;
	public double kickStartHz = 78; // 50..160 recommended
	public double kickEndHz = 36; // 32..90 recommended
	public double kickDrive = 1.7; // 1.0..3.5
	public double kickToneMix = 0.25; // 0..1

	public boolean bassEnabled = true;
	public double bassVolume = 0.5, bassFilterFreq = 400, bassLfoRate = 6;
	public double bassLfoDepth = 200; // Hz (0..1000)
	public double bassResonance = 10; // Q (0.5..20)
	public double bassDuration = 0.50; // seconds (0.2..1.5)
	public com.imagedubstep.nodes.BassWobbleNode.Waveform bassWaveform = com.imagedubstep.nodes.BassWobbleNode.Waveform.SAW;

	public boolean subBassEnabled = true;
	public double subBassVolume = 0.9, subBassDecay = 0.8;
	public double subBassFreq = 45; // 30..90 Hz
	public double subBassAttack = 0.008; // 0..0.060 s
	public double subBassHarmonicMix = 0.35; // 0..1
	public double subBassDrive = 1.2; // 1..3.5
	public double subBassStereo = 0.0; // 0..0.6 (keep small!)

	public boolean snareEnabled = true;
	public double snareVolume = 0.6, snareFilterFreq = 300, snareDecay = 0.1;

	public boolean hiHatEnabled = true;
	public double hiHatVolume = 0.3, hiHatFilterFreq = 8000;

	public boolean extraBassEnabled = true;
	public double extraBassVolume = 0.7;

	public boolean linePercEnabled = true;
	public double linePercVolume = 0.4, linePercDecay = 0.12;
	public double linePercBaseFreq = 150; // Hz
	public double linePercFreqRange = 300; // Hz
	public double linePercNoiseBlend = 0.30; // 0..1 (0=tone, 1=noise)
	public double linePercQ = 5.0; // 0.5..20

	public boolean highFreqEnabled = true;
	public double highFreqVolume = 0.3, highFreqDecay = 0.08;
	public double highFreqOscMix = 0.5; // 0=saw, 1=square
	public double highFreqHpCut = 600; // Hz
	public double highFreqDetune = 0.0; // Hz (-400..+400 is useful)

	public enum Rate {
		HALF, NORMAL, DOUBLE
	}

	public Rate kickRate = Rate.NORMAL;
	public int kickPhase = 0; // 0 or 1 (used only in HALF)

	// Wobble Bass tempo
	public Rate bassRate = Rate.NORMAL;
	public int bassPhase = 0;

	// Sub Bass tempo
	public Rate subRate = Rate.NORMAL;
	public int subPhase = 0;
	public volatile double uiGainLin = 1.0;
	public final java.util.concurrent.ScheduledExecutorService uiDebounceExec = java.util.concurrent.Executors
			.newSingleThreadScheduledExecutor();

	// fields
	public final java.util.concurrent.ExecutorService analysisExec = java.util.concurrent.Executors
			.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "ImageAnalysis");
				t.setDaemon(true);
				return t;
			});
	public final java.util.concurrent.ScheduledExecutorService loopExec = java.util.concurrent.Executors
			.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "DubstepLoop");
				t.setPriority(Thread.MAX_PRIORITY - 1);
				t.setDaemon(true);
				return t;
			});

	public final Object mixLock = new Object();

	public static Image loadedImage;
	// VectorEngine.java
	public static double cornerTauScale = 0.8; // 0.4..1.6 is a good range
	// --- Add to fields in VectorEngine ---
	private final java.util.concurrent.atomic.AtomicInteger playGen = new java.util.concurrent.atomic.AtomicInteger();
	private volatile int sessionId = 0;
	private volatile java.util.concurrent.ScheduledFuture<?> loopFuture = null;
	public Bus busLow;
	public Bus busMid;
	public Bus busHigh;

	// add field:
	public final com.imagedubstep.dynamics.SidechainSignal scKick = new com.imagedubstep.dynamics.SidechainSignal();
	public final SidechainParams scParams = new SidechainParams();
	// VectorEngine fields
	public com.imagedubstep.filter.LowMonoizerNode monoLow;
	public com.imagedubstep.dynamics.MasterLookaheadLimiter limiter;
	public com.imagedubstep.audio.MixerChannel masterMix;
	public com.imagedubstep.fx.FxRackNode fx;
	private final float[] fvBuf = new float[com.imagedubstep.som.BeatFeatureExtractor.DIM];

	// Call this when (re)starting playback
	public void startNewSession() {
		sessionId = playGen.incrementAndGet();
	}

	// Cancel any pending re-loop scheduling
	public void cancelLoop() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
			loopFuture = null;
		}
	}

	public VectorEngine(ImageView iView, Image loadedImage, AudioContext audioContext) {
		imageView = iView;
		this.loadedImage = loadedImage;
		ctx = audioContext;

		busLow = new Bus(ctx);
		busMid = new Bus(ctx);
		busHigh = new Bus(ctx);

		busLow.carve.setHP(28, 0.707).setLP(160, 0.707); // sub/lows only
		busMid.carve.setHP(140, 0.9).setLP(4000, 0.8); // body/presence
		busHigh.carve.setHP(3500, 0.7).setLP(16000, 0.7); // air/sparkle

		busLow.tone.clear().addLowShelf(60, 0.707, -3.0) // tighten subs
				.addPeak(100, 1.0, +2.0); // add punch
		busMid.tone.clear().addPeak(250, 1.0, -2.0) // unbox
				.addPeak(2500, 1.2, +2.5); // presence
		busHigh.tone.clear().addHighShelf(8000, 0.8, +3.0);
		busLow.carve.setHP(28, 0.707).setHP(28, 0.707);

		// Build a master mix from your bus outputs
		masterMix = new MixerChannel(ctx);
		masterMix.addSource(busLow.tone);
		masterMix.addSource(busMid.tone);
		masterMix.addSource(busHigh.tone);

		fx = new com.imagedubstep.fx.FxRackNode(ctx, masterMix);
		// Low-band mono (optional but recommended)
		monoLow = new com.imagedubstep.filter.LowMonoizerNode(ctx, fx, /* cutoffHz */120.0, /* sideGain */0.3);

		// Look-ahead limiter
		limiter = new com.imagedubstep.dynamics.MasterLookaheadLimiter(ctx, monoLow, 0.98, 10.0, 1.0, 120.0);

		// Send the single master chain to the destination
		synchronized (mixLock) {
			ctx.destination().addSource(limiter);
		}



	}
	public void addToMix(com.imagedubstep.core.AudioNode voice) {
		synchronized (mixLock) {
			ctx.destination().addSource(new com.imagedubstep.nodes.GainWrapperNode(ctx, voice, uiGainLin));
		}
	}

	private static double db2lin(double dB) {
		return Math.pow(10.0, dB / 20.0);
	}

	public static class Vector {
		public double r, g, b, brightness, saturation, energy, lineDensity, lineOrientation;
		public int cr;
		public int cg;
		public int cb;
		// NEW feature dimensions (0..1 unless noted)
		public double rmsContrast; // local luminance stddev (normalized)
		public double entropy; // 0..1 (texture complexity)
		public double cornerDensity; // 0..1 (Sobel corner hits / pixels)
		public double hfEnergy; // 0..1 (high-frequency/edge energy)
		public double symLR; // 0..1 (left-right mirror similarity)
		public double motion; // 0..1 (mean |Δluma| vs previous analysis for this cell)

	}

	// Background-only. No Canvas, no FX calls.
	private static void analyzeImage_BG() {
		Image img = loadedImage;
		if (img == null)
			return;

		final int W = 64, H = 64;
		// snapshot window parameters once to avoid tearing
		final double imgW = img.getWidth(), imgH = img.getHeight();
		final double base = Math.min(imgW, imgH);
		final double winPix = base * winSize;
		final double maxX = Math.max(0, imgW - winPix);
		final double maxY = Math.max(0, imgH - winPix);
		final double srcX = winX * maxX, srcY = winY * maxY;

		float[] luma64 = downsampleWindowTo64(img, srcX, srcY, winPix, winPix);
		// compute features (same code you already have)
		float[][] grads = sobel(luma64, W, H);

		// publish atomically
		vectors = computeVectorsFrom(img, winX, winY, winSize, W, H, grads, luma64); // normalized inputs

		// update prevLuma64 here too if you use motion
		if (prevLuma64 == null || prevLuma64.length != luma64.length)
			prevLuma64 = new float[luma64.length];
		System.arraycopy(luma64, 0, prevLuma64, 0, luma64.length);
		prevLumaW = W;
		prevLumaH = H;
	}

	// simple NN downsample of a src window -> 64x64
	private static float[] downsampleWindowTo64(Image img, double sx, double sy, double sw, double sh) {
		final int W = 64, H = 64;
		float[] out = new float[W * H];
		PixelReader pr = img.getPixelReader();
		for (int y = 0; y < H; y++) {
			double syf = sy + (y + 0.5) * (sh / H);
			int siY = Math.min((int) img.getHeight() - 1, (int) syf);
			for (int x = 0; x < W; x++) {
				double sxf = sx + (x + 0.5) * (sw / W);
				int siX = Math.min((int) img.getWidth() - 1, (int) sxf);
				int argb = pr.getArgb(siX, siY);
				int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
				double ya = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 * (a / 255.0);
				out[y * W + x] = (float) ya;
			}
		}
		return out;
	}

	public void queueAnalysis() {
		if (analysisQueued)
			return;
		analysisQueued = true;
		uiDebounceExec.schedule(() -> {
			analysisQueued = false;
			analysisExec.submit(() -> {
				analyzeImage_BG(); // new: background version (no FX)
			});
		}, 25, java.util.concurrent.TimeUnit.MILLISECONDS); // ~40 Hz max while dragging
	}

	private static float[][] sobel(float[] luma, int width, int height) {
		float[] gx = new float[width * height];
		float[] gy = new float[width * height];

		for (int y = 1; y < height - 1; y++) {
			for (int x = 1; x < width - 1; x++) {
				int idx = y * width + x;

				// 3x3 neighborhood
				float p00 = luma[idx - width - 1];
				float p01 = luma[idx - width];
				float p02 = luma[idx - width + 1];
				float p10 = luma[idx - 1];
				// float p11 = luma[idx]; // center, not used directly
				float p12 = luma[idx + 1];
				float p20 = luma[idx + width - 1];
				float p21 = luma[idx + width];
				float p22 = luma[idx + width + 1];

				// Sobel kernels:
				// Gx = [ [-1 0 +1], [-2 0 +2], [-1 0 +1] ]
				// Gy = [ [-1 -2 -1], [ 0 0 0], [+1 +2 +1] ]
				gx[idx] = (-p00 - 2f * p10 - p20) + (p02 + 2f * p12 + p22);
				gy[idx] = (-p00 - 2f * p01 - p02) + (p20 + 2f * p21 + p22);
			}
		}
		return new float[][] { gx, gy };
	}

	public void forceDubstepNow2() {
		if (this.vectors == null || this.vectors.isEmpty())
			return;

		final double stepTime = 60.0 / tempo / 4.0;
		final double startTime = ctx.currentTime();

		for (int i = 0; i < this.vectors.size(); i++) {
			Vector v = this.vectors.get(i);
			double t = startTime + i * stepTime;

			if (v.r > 0.4 && v.energy > 0.3 && kickEnabled) {
				makeKick(t, v.r * v.energy, v);
			}

			if (v.b > 0.3 && v.brightness > 0.2 && bassEnabled) {
				makeBass(t, v.b * v.brightness, v);
			}

			if (v.b > 0.6 && v.brightness < 0.4 && subBassEnabled) {
				makeSubBass(t, v.b, v);
			}

			if (v.g > 0.5 && i % 8 == 4 && snareEnabled) {
				makeSnare(t, v.g, v);
			}

			if (v.saturation > 0.6 && i % 2 == 1 && hiHatEnabled) {
				makeHiHat(t, v.saturation, v);
			}

			if (v.lineDensity > 0.3 && linePercEnabled) {
				makeLinePercussion(t, v.lineDensity, v.lineOrientation, v);
			}

			if (v.lineDensity > 0.6 && v.brightness > 0.5 && highFreqEnabled) {
				makeHighFreqLine(t, v.lineDensity * v.brightness, v);
			}

		}
		final int sid = sessionId;

		if (loopEnabled) {
			long durationMs = (long) (this.vectors.size() * stepTime * 1000.0);
			loopFuture = loopExec.schedule(() -> {
				// only continue if we’re still in the same session
				if (sid == sessionId) {
					forceDubstepNow2();
				}
			}, durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		}

	}

	private void makeHighFreqLine(double t, double intensity, Vector v) {
		final double highStaggerS = mixHighStaggerMs / 1000.0;
		double base = 800 + v.lineDensity * 1200;
		double hpCut = lerp(500, 2600, v.hfEnergy);
		HighFreqLineNode n = new HighFreqLineNode(ctx, t + highStaggerS, Math.max(0.06, highFreqDecay), base,
				highFreqDetune, highFreqOscMix, hpCut,
				Math.max(0.0, v.lineDensity * v.brightness * highFreqVolume) * mixHighTrim);
		var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busHigh.mix.addSource(kEQ);
	}

	private void makeLinePercussion(double t, double lineDensity, double lineOrientation, Vector v) {
		double toneFreq = linePercBaseFreq + v.lineOrientation * linePercFreqRange;
		double filterHz = toneFreq;
		double lineNoise = lerp(0.1, 0.9, v.cornerDensity);
		LinePercNode linePercNode = new LinePercNode(ctx, t + 0.003, Math.max(0.10, linePercDecay), toneFreq, filterHz,
				lineNoise, linePercQ, v.lineDensity, Math.max(0.0, v.lineDensity * linePercVolume) * mixHighTrim);
		var kEQ = new ParametricEQNode(ctx, linePercNode).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busMid.mix.addSource(kEQ);

	}

	private void makeHiHat(double t, double saturation, Vector v) {

		int samples = (int) (ctx.getSampleRate() * 0.05);
		NoiseBurstNode n = new NoiseBurstNode(ctx, t + 0.002, 0.05, samples, com.imagedubstep.util.Biquad.Type.HIGHPASS,
				hiHatFilterFreq, 0.707, Math.max(0.0, v.saturation * hiHatVolume) * mixHatTrim);
		var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busHigh.mix.addSource(kEQ);

	}

	private void makeSnare(double t, double intensity, Vector v) {
		double liveBoost = 0.85 + 0.3 * v.motion;
		int samples = (int) (ctx.getSampleRate() * 0.10);
		double snrAmp = Math.max(0.08, v.g * snareVolume) * liveBoost;
		NoiseBurstNode n = new NoiseBurstNode(ctx, t, Math.max(0.08, snareDecay), samples, Biquad.Type.HIGHPASS,
				snareFilterFreq, 0.707, snrAmp);
		var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busHigh.mix.addSource(kEQ);
	}

	private void makeSubBass(double t, double intensity, Vector v) {
		double subStereoFromSym = Math.max(0.0, Math.min(0.25, 0.25 * v.symLR));
		final double subNudgeS = mixSubNudgeMs / 1000.0;
		final double subDuckS = mixSubDuckMs / 1000.0;
		double decS = Math.max(0.65, subBassDecay);
		double liveBoost = 0.85 + 0.3 * v.motion;
		// Ducked segment
		double duckDur = Math.min(subDuckS, decS);
		double baseAmpS = Math.max(0.0,
				(v.b * (blueBassAmp + blueBassAmpOverdrive * (1.0 - v.brightness))) * subBassVolume) * mixLowBlendTrim
				* liveBoost;
		SubBassNode n2 = new SubBassNode(ctx, t + subNudgeS, duckDur, clamp(subBassFreq, 30, 90),
				baseAmpS * bassAmpOverdrive, subBassAttack, subBassHarmonicMix, subBassDrive, subStereoFromSym // ⬅️
																												// stereo
																												// from
																												// symmetry
		);

		var subDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n2, scKick, /* depthDb */ -6.0,
				/* threshold */ 0.02, /* ratio */ 3.0, /* atk ms */ 3, /* rel ms */ 120, scParams);

		var kEQ2 = new ParametricEQNode(ctx, subDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busLow.mix.addSource(kEQ2);

	}

	private void makeBass(double t, double intensity, Vector v) {
		double liveBoost = 0.85 + 0.3 * v.motion;
		double freq = bassPitch + v.b * v.brightness * wantBassFrequencyBrightnessBase;
		double lfoDepth = lerp(50, 900, v.entropy);
		double amp = Math.max(0.0, v.b * v.brightness * bassVolume) * liveBoost;

		BassWobbleNode n = new BassWobbleNode(ctx, t, clamp(bassDuration, 0.2, 1.5), freq, bassFilterFreq, bassLfoRate,
				amp, lfoDepth, bassResonance, bassWaveform);
		var bwDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick, -4.0, 0.02, 2.5, 5, 140,
				scParams);
		var kEQ = new ParametricEQNode(ctx, bwDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busLow.mix.addSource(kEQ);

	}

	private void makeKick(double t, double intensity, Vector v) {

		double liveBoost = 0.85 + 0.3 * v.motion;
		double atk = Math.max(0.012, kickAttack);
		double decK = Math.max(0.22, kickDecay);

		double ampK = Math.max(0.0, (v.r * redKickAmp + v.energy * energyKickAmp) * kickVolume) * mixLowBlendTrim
				* liveBoost;

		KickNode n = new KickNode(ctx, t, decK, clamp(kickStartHz, 50, 160), clamp(kickEndHz, 32, 90), atk, decK,
				clamp(ampK, 0.0, 1.0), clamp(kickDrive, 1.0, 3.5), clamp(kickToneMix, 0.0, 1.0));
		var kickSend = new com.imagedubstep.dynamics.SidechainSendNode(ctx, n, scKick, /* attack */6, /* release */110);

		var kEQ = new ParametricEQNode(ctx, kickSend).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
		busLow.mix.addSource(kEQ);

	}

	public void forceDubstepNow(SOMRuntime somRt) {
		synchronized (mixLock) {
			if (this.vectors == null || this.vectors.isEmpty())
				return;

			final double stepTime = 60.0 / tempo / 4.0;
			final double startTime = ctx.currentTime() + .15;

			// Convert ms sliders to seconds
			final double subNudgeS = mixSubNudgeMs / 1000.0;
			final double subDuckS = mixSubDuckMs / 1000.0;
			final double highStaggerS = mixHighStaggerMs / 1000.0;

			int kicks = 0, subs = 0, basses = 0, snares = 0, hats = 0, linep = 0, highs = 0, extras = 0;

			for (int i = 0; i < this.vectors.size(); i++) {
				Vector v = this.vectors.get(i);
				double t = startTime + i * stepTime;
				// Per-step feature mappings
				double subStereoFromSym = Math.max(0.0, Math.min(0.25, 0.25 * v.symLR)); // 0..0.25 for mono safety
				double liveBoost = 0.85 + 0.3 * v.motion; // 0.85..1.15

				boolean lowTaken = false, midTaken = false;

				// ---- LOW LANE: either blend Kick+Sub or exclusive selection
				double kickScore = (v.r * 0.7 + v.energy * 0.3);
				kickScore *= (0.85 + 0.3 * v.rmsContrast); // more contrast → a bit more kick

				double subScore = (v.b * (0.6 + 0.4 * (1.0 - v.brightness)));

				// ---- LOW LANE
				boolean wantKick = kickEnabled && (v.r > kickRedThreshold || v.energy > kickEnergyThreshold)
						&& allowThisStep(kickRate, kickPhase, i);
				boolean wantSub = subBassEnabled && (v.b > subBassBlueTreshold) && (v.brightness > subBassBrightnessThreshold)
						&& allowThisStep(subRate, subPhase, i);

				
				
				
				
				com.imagedubstep.som.BeatFeatureExtractor.fill(fvBuf,
					    wantKick,
					    Math.max(0.0, (v.r * redKickAmp + v.energy * energyKickAmp) * kickVolume),
					    kickStartHz, kickEndHz,
					    wantSub,
					    Math.max(0.0, (v.b * (blueBassAmp + blueBassAmpOverdrive * (1.0 - v.brightness))) * subBassVolume),
					    subBassFreq,
					    (bassEnabled ? Math.max(0.0, v.b * v.brightness * bassVolume) : 0.0),
					    bassFilterFreq, bassLfoRate,
					    v.lineDensity, v.brightness, v.saturation, v.energy,
					    v.hfEnergy, Math.max(0.0, Math.min(1.0, 0.25 * v.symLR)), v.motion
					);
					if (somRt != null) somRt.submit(fvBuf);   // copies into pooled buffer immediately

				
				
				
				if (mixLowBlend && wantKick && wantSub) {
					// Kick (trimmed for blend)
					double atk = Math.max(0.012, kickAttack);
					double decK = Math.max(0.22, kickDecay);

					double ampK = Math.max(0.0, (v.r * redKickAmp + v.energy * energyKickAmp) * kickVolume)
							* mixLowBlendTrim * liveBoost;

					KickNode n = new KickNode(ctx, t, decK, clamp(kickStartHz, 50, 160), clamp(kickEndHz, 32, 90), atk,
							decK, clamp(ampK, 0.0, 1.0), clamp(kickDrive, 1.0, 3.5), clamp(kickToneMix, 0.0, 1.0));
					var kickSend = new com.imagedubstep.dynamics.SidechainSendNode(ctx, n, scKick, /* attack */6,
							/* release */110);

					var kEQ = new ParametricEQNode(ctx, kickSend).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);
					kicks++;

					// Sub with nudge + duck segment + tail, trimmed for blend
					double decS = Math.max(0.65, subBassDecay);

					// Ducked segment
					double duckDur = Math.min(subDuckS, decS);
					double baseAmpS = Math.max(0.0,
							(v.b * (blueBassAmp + blueBassAmpOverdrive * (1.0 - v.brightness))) * subBassVolume)
							* mixLowBlendTrim * liveBoost;
					if (duckDur > 0.0) {
						SubBassNode n2 = new SubBassNode(ctx, t + subNudgeS, duckDur, clamp(subBassFreq, 30, 90),
								baseAmpS * bassAmpOverdrive, subBassAttack, subBassHarmonicMix, subBassDrive,
								subStereoFromSym // ⬅️ stereo from
													// symmetry
						);

						var subDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n2, scKick,
								/* depthDb */ -6.0, /* threshold */ 0.02, /* ratio */ 3.0, /* atk ms */ 3,
								/* rel ms */ 120, scParams);

						var kEQ2 = new ParametricEQNode(ctx, subDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
								-3.0);
						busLow.mix.addSource(kEQ2);
					}
					// Tail
					double tailDur = Math.max(0.12, decS - duckDur);
					if (tailDur > 1e-3) {
						SubBassNode n2 = new SubBassNode(ctx, t + subNudgeS + duckDur, tailDur,
								clamp(subBassFreq, 30, 90), baseAmpS, // full level (already motion-boosted)
								Math.max(0.0, subBassAttack - duckDur), subBassHarmonicMix, subBassDrive,
								subStereoFromSym);
						var subDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n2, scKick,
								/* depthDb */ -6.0, /* threshold */ 0.02, /* ratio */ 3.0, /* atk ms */ 3,
								/* rel ms */ 120, scParams);

						var kEQ2 = new ParametricEQNode(ctx, subDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
								-3.0);
						busLow.mix.addSource(kEQ2);
					}
					subs++;
					lowTaken = true;

				} else if (wantKick || wantSub) {
					// Original exclusive behavior (pick stronger)
					boolean pickKick = !wantSub || (wantKick && kickScore >= subScore);
					if (pickKick) {
						double atk = Math.max(0.012, kickAttack);
						double decK = Math.max(0.22, kickDecay);
						double ampK = Math.max(0.0, kickScore * kickVolume);
						KickNode n = new KickNode(ctx, t, decK, clamp(kickStartHz, 50, 160), clamp(kickEndHz, 32, 90),
								atk, decK, clamp(ampK, 0.0, 1.0), clamp(kickDrive, 1.0, 3.5),
								clamp(kickToneMix, 0.0, 1.0));
						var kickSend = new com.imagedubstep.dynamics.SidechainSendNode(ctx, n, scKick, /* attack */6,
								/* release */110);

						var kEQ = new ParametricEQNode(ctx, kickSend).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
								-3.0);
						busLow.mix.addSource(kEQ);
						kicks++;
					} else {
						double decS = Math.max(0.65, subBassDecay);
						double ampS = Math.max(0.0, subScore * subBassVolume);
						SubBassNode n = new SubBassNode(ctx, t, decS, clamp(subBassFreq, 30, 90), ampS, subBassAttack,
								subBassHarmonicMix, subBassDrive, subBassStereo);
						var subDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick,
								/* depthDb */ -6.0, /* threshold */ 0.02, /* ratio */ 3.0, /* atk ms */ 3,
								/* rel ms */ 120, scParams);

						var kEQ = new ParametricEQNode(ctx, subDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
								-3.0);

						busLow.mix.addSource(kEQ);
						subs++;
					}
					lowTaken = true;
				}

				// --- Double-time echoes (optional second hit half a 16th later)
				double halfStep = stepTime * 0.5;

				if (kickRate == Rate.DOUBLE && wantKick) {
					// smaller, quicker “ghost” kick
					double atk2 = Math.max(0.010, kickAttack * 0.7);
					double dec2 = Math.max(0.18, kickDecay * 0.75);
					double amp2 = kickAmpBase
							* Math.max(0.0, (v.r * redKickAmp + v.energy * energyKickAmp) * kickVolume);
					KickNode n = new KickNode(ctx, t + halfStep, dec2, clamp(kickStartHz, 50, 160),
							clamp(kickEndHz, 32, 90), atk2, dec2, clamp(amp2, 0.0, 1.0), clamp(kickDrive, 1.0, 3.5),
							clamp(kickToneMix, 0.0, 1.0));
					var kickSend = new com.imagedubstep.dynamics.SidechainSendNode(ctx, n, scKick, /* attack */6,
							/* release */110);

					var kEQ = new ParametricEQNode(ctx, kickSend).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);
				}

				if (subRate == Rate.DOUBLE && wantSub) {
					// subtle shorter sub tap, respecting your nudge/duck if blending
					double decS2 = Math.max(0.25, subBassDecay * subBaseDecayOverdrive);
					double ampS2 = 0.80 * Math.max(0.0,
							(v.b * (blueBassAmp2 + blueBassAmpOverdrive2 * (1.0 - v.brightness))) * subBassVolume);
					double tBase = t + ((mixLowBlend && wantKick) ? (subNudgeS) : 0.0);
					SubBassNode n = new SubBassNode(ctx, tBase + halfStep, decS2, clamp(subBassFreq, 30, 90), ampS2,
							subBassAttack, subBassHarmonicMix, subBassDrive, subBassStereo);
					var subDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick, /* depthDb */ -6.0,
							/* threshold */ 0.02, /* ratio */ 3.0, /* atk ms */ 3, /* rel ms */ 120, scParams);

					var kEQ = new ParametricEQNode(ctx, subDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);
				}

				boolean wantBassThisStep = (!midTaken && bassEnabled && v.b > wantBassBlueThreshold
						&& v.brightness > wantBassbrightnessThreshold && allowThisStep(bassRate, bassPhase, i));

				if (wantBassThisStep) {
					double when = (/* if you already stagger mid after low */ lowTaken ? (t + 0.010) : t);
					double freq = bassPitch + v.b * v.brightness * wantBassFrequencyBrightnessBase;
					double lfoDepth = lerp(50, 900, v.entropy);
					double amp = Math.max(0.0, v.b * v.brightness * bassVolume) * liveBoost;

					BassWobbleNode n = new BassWobbleNode(ctx, when, clamp(bassDuration, 0.2, 1.5), freq,
							bassFilterFreq, bassLfoRate, amp, lfoDepth, bassResonance, bassWaveform);
					var bwDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick, -4.0, 0.02, 2.5, 5,
							140, scParams);
					var kEQ = new ParametricEQNode(ctx, bwDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);

					basses++;
					midTaken = true;

					if (bassRate == Rate.DOUBLE) {
						BassWobbleNode n2 = new BassWobbleNode(ctx, when + stepTime * 0.5,
								clamp(bassDuration * 0.75, 0.15, 1.2), freq, bassFilterFreq, bassLfoRate, amp * 0.85,
								bassLfoDepth, bassResonance, bassWaveform);
						var bwDuck2 = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n2, scKick, -4.0, 0.02,
								2.5, 5, 140, scParams);
						var kEQ2 = new ParametricEQNode(ctx, bwDuck2).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
								-3.0);
						busLow.mix.addSource(kEQ2);
					}
				}

				// ---- MID LANE: Wobble (as before, tiny stagger if low already hit)
				if (!midTaken && bassEnabled && v.b > midTakenBlueThreshold
						&& v.brightness > midTakenBrightnessThreshold) {
					double amp = Math.max(0.0, v.b * v.brightness * bassVolume);
					double when = lowTaken ? (t + 0.010) : t;
					double freq = bassPitch + v.b * v.brightness * midTakenFreqBase;

					BassWobbleNode n = new BassWobbleNode(ctx, when, clamp(bassDuration, 0.2, 1.5), freq,
							bassFilterFreq, bassLfoRate, amp, bassLfoDepth, bassResonance, bassWaveform);
					var bwDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick, -4.0, 0.02, 2.5, 5,
							140, scParams);
					var kEQ = new ParametricEQNode(ctx, bwDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);
					basses++;
					midTaken = true;
				}

				// ---- SNARE: keep backbeat
				if (snareEnabled && v.g > snareGreenThreshold && (i % 8) == 4) {
					int samples = (int) (ctx.getSampleRate() * 0.10);
					double snrAmp = Math.max(0.08, v.g * snareVolume) * liveBoost;
					NoiseBurstNode n = new NoiseBurstNode(ctx, t, Math.max(0.08, snareDecay), samples,
							Biquad.Type.HIGHPASS, snareFilterFreq, 0.707, snrAmp);
					var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
					busHigh.mix.addSource(kEQ);
					snares++;
					midTaken = true;
				}

				// ---- HIGH LANE: either allow two layers (Line/High/Hat) or pick one
				boolean wantHat = hiHatEnabled && v.saturation > hiHatSaturationThreshold && (i % 2) == 1;
				boolean wantLine = linePercEnabled && v.lineDensity > lineLineDensityThreshold;
				boolean wantHigh = highFreqEnabled && v.lineDensity > freqLineDensityThreshold
						&& v.brightness > freqBrightnessThreshold;

				if (mixHighTwoLayers) {
					int scheduled = 0;

					if (wantHat && scheduled < 2) {
						int samples = (int) (ctx.getSampleRate() * 0.05);
						NoiseBurstNode n = new NoiseBurstNode(ctx, t + 0.002, 0.05, samples,
								com.imagedubstep.util.Biquad.Type.HIGHPASS, hiHatFilterFreq, 0.707,
								Math.max(0.0, v.saturation * hiHatVolume) * mixHatTrim);
						var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
						busHigh.mix.addSource(kEQ);
						hats++;
						scheduled++;
					}
					if (wantLine && scheduled < 2) {
						double toneFreq = linePercBaseFreq + v.lineOrientation * linePercFreqRange;
						double filterHz = toneFreq;
						double lineNoise = lerp(0.1, 0.9, v.cornerDensity);
						LinePercNode linePercNode = new LinePercNode(ctx, t + 0.003, Math.max(0.10, linePercDecay),
								toneFreq, filterHz, lineNoise, linePercQ, v.lineDensity,
								Math.max(0.0, v.lineDensity * linePercVolume) * mixHighTrim);
						var kEQ = new ParametricEQNode(ctx, linePercNode).addLowShelf(60, 0.7, +2.0).addHighShelf(4000,
								0.7, -3.0);
						busMid.mix.addSource(kEQ);
						linep++;
						scheduled++;
					}
					if (wantHigh && scheduled < 2) {
						double base = 800 + v.lineDensity * 1200;
						double hpCut = lerp(500, 2600, v.hfEnergy);
						HighFreqLineNode n = new HighFreqLineNode(ctx, t + highStaggerS, Math.max(0.06, highFreqDecay),
								base, highFreqDetune, highFreqOscMix, hpCut,
								Math.max(0.0, v.lineDensity * v.brightness * highFreqVolume) * mixHighTrim);
						var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
						busHigh.mix.addSource(kEQ);
						highs++;
						scheduled++;
					}
				} else {
					// Original single-winner selection (hat vs line vs high)
					double hatScore = wantHat ? v.saturation : -1;
					double lineScore = wantLine ? v.lineDensity : -1;
					double highScore = wantHigh ? v.lineDensity * v.brightness : -1;
					double best = -1;
					int which = -1;
					if (hatScore > best) {
						best = hatScore;
						which = 0;
					}
					if (lineScore > best) {
						best = lineScore;
						which = 1;
					}
					if (highScore > best) {
						best = highScore;
						which = 2;
					}

					if (which == 0) {
						int samples = (int) (ctx.getSampleRate() * 0.05);
						NoiseBurstNode n = new NoiseBurstNode(ctx, t + 0.002, 0.05, samples,
								com.imagedubstep.util.Biquad.Type.HIGHPASS, hiHatFilterFreq, 0.707,
								Math.max(0.0, v.saturation * hiHatVolume));
						var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
						busHigh.mix.addSource(kEQ);
						hats++;
					} else if (which == 1) {
						double toneFreq = linePercBaseFreq + v.lineOrientation * linePercFreqRange;
						double filterHz = toneFreq;
						LinePercNode n = new LinePercNode(ctx, t + 0.003, Math.max(0.10, linePercDecay), toneFreq,
								filterHz, linePercNoiseBlend, linePercQ, v.lineDensity,
								Math.max(0.0, v.lineDensity * linePercVolume));
						var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
						busMid.mix.addSource(kEQ);
						linep++;
					} else if (which == 2) {
						double base = 800 + v.lineDensity * 1200;
						double hpCut = lerp(500, 2600, v.hfEnergy);
						HighFreqLineNode n = new HighFreqLineNode(ctx, t + 0.004, Math.max(0.06, highFreqDecay), base,
								highFreqDetune, highFreqOscMix, hpCut,
								Math.max(0.0, v.lineDensity * v.brightness * highFreqVolume));
						var kEQ = new ParametricEQNode(ctx, n).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7, -3.0);
						busHigh.mix.addSource(kEQ);
						highs++;
					}
				}

				// Extra wobble only if low lane is free (keeps headroom)
				if (!lowTaken && extraBassEnabled && bassEnabled && v.energy > 0.78) {
					BassWobbleNode n = new BassWobbleNode(ctx, t + stepTime / 2.0, clamp(bassDuration, 0.2, 1.5),
							bassPitch, bassFilterFreq, bassLfoRate, Math.max(0.0, v.energy * extraBassVolume),
							bassLfoDepth, bassResonance, bassWaveform);
					var bwDuck = new com.imagedubstep.dynamics.SidechainDuckerNode(ctx, n, scKick, -4.0, 0.02, 2.5, 5,
							140, scParams);
					var kEQ = new ParametricEQNode(ctx, bwDuck).addLowShelf(60, 0.7, +2.0).addHighShelf(4000, 0.7,
							-3.0);
					busLow.mix.addSource(kEQ);
					extras++;
				}
			}

//			System.out.printf("Trig — K:%d Sub:%d Bass:%d Sn:%d Hat:%d Line:%d High:%d Xtra:%d%n", kicks, subs, basses,
//					snares, hats, linep, highs, extras);

			// capture current session
			final int sid = sessionId;

			if (loopEnabled) {
				long durationMs = (long) (this.vectors.size() * stepTime * 1000.0);
				loopFuture = loopExec.schedule(() -> {
					// only continue if we’re still in the same session
					if (sid == sessionId) {
						forceDubstepNow(somRt);
					}
				}, durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
			}

		}

	}

	private boolean allowThisStep(Rate rate, int phase01, int i16th) {
		switch (rate) {
		case HALF:
			// Fire on even or odd 16ths depending on phase
			return (i16th % 2) == (phase01 & 1);
		case NORMAL:
			return true; // as-is
		case DOUBLE:
			return true; // schedule main now; we’ll add an extra at +stepTime/2
		default:
			return true;
		}
	}

	private static List<Vector> computeVectorsFrom(Image img, double winX, double winY, double winSize, int W, int H,
			float[][] gradsOpt // may be null; if present must be [gx, gy] for this same W×H
			, float[] luma64) {
		List<Vector> out = new ArrayList<>(64);
		if (img == null)
			return out;

		final PixelReader pr = img.getPixelReader();
		final double imgW = img.getWidth(), imgH = img.getHeight();
		final double base = Math.min(imgW, imgH);
		final double winPx = Math.max(1.0, base * winSize);

		final double maxX = Math.max(0, imgW - winPx);
		final double maxY = Math.max(0, imgH - winPx);
		final double srcX = Math.max(0, Math.min(maxX, winX * maxX));
		final double srcY = Math.max(0, Math.min(maxY, winY * maxY));

		// Downsample the window to W×H using nearest-neighbor (fast, fine for features)
		float[] luma = new float[W * H];
		int[] rBuf = new int[W * H];
		int[] gBuf = new int[W * H];
		int[] bBuf = new int[W * H];

		for (int y = 0; y < H; y++) {
			double sy = srcY + (y + 0.5) * (winPx / H);
			int isy = (int) Math.max(0, Math.min(imgH - 1, Math.floor(sy)));
			for (int x = 0; x < W; x++) {
				double sx = srcX + (x + 0.5) * (winPx / W);
				int isx = (int) Math.max(0, Math.min(imgW - 1, Math.floor(sx)));

				int argb = pr.getArgb(isx, isy);
				int a = (argb >> 24) & 0xFF;
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = (argb) & 0xFF;

				int idx = y * W + x;
				rBuf[idx] = r;
				gBuf[idx] = g;
				bBuf[idx] = b;

				// premultiplied luma so transparent pixels don't dominate
				double ya = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 * (a / 255.0);
				luma[idx] = (float) ya;
			}
		}

		// Sobel gradients (or use provided grads if dimension matches)
		float[] gx = null, gy = null;
		if (gradsOpt != null && gradsOpt.length == 2 && gradsOpt[0] != null && gradsOpt[1] != null
				&& gradsOpt[0].length == W * H && gradsOpt[1].length == W * H) {
			gx = gradsOpt[0];
			gy = gradsOpt[1];
		} else {
			gx = new float[W * H];
			gy = new float[W * H];
			for (int y = 1; y < H - 1; y++) {
				for (int x = 1; x < W - 1; x++) {
					int i = y * W + x;
					float p00 = luma[i - W - 1], p01 = luma[i - W], p02 = luma[i - W + 1];
					float p10 = luma[i - 1], p11 =

							luma[i], p12 = luma[i + 1];
					float p20 = luma[i + W - 1], p21 = luma[i + W], p22 = luma[i + W + 1];

					gx[i] = (-p00 - 2f * p10 - p20) + (p02 + 2f * p12 + p22);
					gy[i] = (-p00 - 2f * p01 - p02) + (p20 + 2f * p21 + p22);
				}
			}
		}

		// Edge magnitude for density/orientation
		double[] edge = new double[W * H];
		for (int i = 0; i < W * H; i++)
			edge[i] = Math.hypot(gx[i], gy[i]);

		// Build 8×8 cells (each is 8×8 pixels in the 64×64 map)
		for (int cell = 0; cell < 64; cell++) {
			int bx = (cell % 8) * 8;
			int by = (cell / 8) * 8;

			long rs = 0, gs = 0, bs = 0;
			int count = 0;
			for (int dy = 0; dy < 8; dy++) {
				for (int dx = 0; dx < 8; dx++) {
					int x = bx + dx, y = by + dy;
					if (x < W && y < H) {
						int idx = y * W + x;
						rs += rBuf[idx];
						gs += gBuf[idx];
						bs += bBuf[idx];
						count++;
					}
				}
			}

			int rAvg = (int) (rs / Math.max(1, count));
//			System.out.println("rAvg: " + rAvg);
			int gAvg = (int) (gs / Math.max(1, count));
//			System.out.println("gAvg: " + gAvg);
			int bAvg = (int) (bs / Math.max(1, count));
//			System.out.println("bAvg: " + bAvg);

			double brightness = (rAvg + gAvg + bAvg) / (255.0 * 3.0);
			int max = Math.max(rAvg, Math.max(gAvg, bAvg));
			int min = Math.min(rAvg, Math.min(gAvg, bAvg));
			double saturation = max == 0 ? 0.0 : (max - min) / (double) max;
			double energy = Math.sqrt(rAvg * rAvg + gAvg * gAvg + bAvg * bAvg) / (255.0 * Math.sqrt(3));

			// line density (threshold in edge magnitude space)
			double lineDensity = 0;
			int total = 0;
			for (int dy = 0; dy < 8; dy++) {
				int y = by + dy;
				for (int dx = 0; dx < 8; dx++) {
					int x = bx + dx, i = y * W + x;
					if (edge[i] > 0.20)
						lineDensity++; // normalized threshold (~tweak)
					total++;
				}
			}
			lineDensity = total > 0 ? (lineDensity / total) : 0.0;

			// dominant orientation: average atan2(dy, dx) where magnitude is non-trivial
			double sumAng = 0.0;
			int cnt = 0;
			for (int dy = 1; dy < 7; dy++) {
				int y = by + dy;
				for (int dx = 1; dx < 7; dx++) {
					int x = bx + dx, i = y * W + x;
					double ex = gx[i], ey = gy[i];
					if (Math.abs(ex) > 1e-4 || Math.abs(ey) > 1e-4) {
						sumAng += Math.atan2(ey, ex);
						cnt++;
					}
				}
			}
			double lineOrientation = (cnt > 0) ? ((sumAng / cnt + Math.PI) / (2.0 * Math.PI)) : 0.0;

			Vector v = new Vector();
			v.r = rAvg / 255.0;
			v.g = gAvg / 255.0;
			v.b = bAvg / 255.0;
			v.brightness = brightness;
			v.saturation = saturation;
			v.energy = energy;
			v.lineDensity = lineDensity;
			v.lineOrientation = lineOrientation;
			v.cr = rAvg;
			v.cg = gAvg;
			v.cb = bAvg;

			out.add(v);
		}
		float[] sobelGx64 = gradsOpt[0], sobelGy64 = gradsOpt[1];
		fillExtraVectorFeatures(out, luma64, W, H, sobelGx64, sobelGy64);

		return out;
	}

	// inside your engine bootstrapping (e.g., VectorEngine or DubstepApp init)
	// inside VectorEngine
	public static final class Bus {
		public final MixerChannel mix; // where instruments are added
		public final FilterChainNode carve; // HP/BP/LP carving (existing)
		public final ParametricEQNode tone; // shelves/peaks

		public Bus(AudioContext ctx) {
			mix = new MixerChannel(ctx);
			carve = new FilterChainNode(ctx, mix); // mix -> carve
			tone = new ParametricEQNode(ctx, carve); // -> tone
		}
	}

	private static float[] makeLuma64FromImage(javafx.scene.image.Image img, int W, int H) {
		float[] out = new float[W * H];
		javafx.scene.image.PixelReader pr = img.getPixelReader();
		final int srcW = (int) img.getWidth();
		final int srcH = (int) img.getHeight();

		// Guard
		if (pr == null || srcW <= 0 || srcH <= 0)
			return out;

		// For each destination pixel, compute its footprint in source space:
		// x in [x0, x1), y in [y0, y1)
		for (int y = 0; y < H; y++) {
			// Source-space vertical window
			final double y0 = (double) y * srcH / (double) H;
			final double y1 = (double) (y + 1) * srcH / (double) H;
			final int iy0 = Math.max(0, (int) Math.floor(y0));
			final int iy1 = Math.min(srcH - 1, (int) Math.ceil(y1) - 1);

			for (int x = 0; x < W; x++) {
				// Source-space horizontal window
				final double x0 = (double) x * srcW / (double) W;
				final double x1 = (double) (x + 1) * srcW / (double) W;
				final int ix0 = Math.max(0, (int) Math.floor(x0));
				final int ix1 = Math.min(srcW - 1, (int) Math.ceil(x1) - 1);

				double accum = 0.0;
				double weightSum = 0.0;

				for (int sy = iy0; sy <= iy1; sy++) {
					// vertical overlap of this source row with [y0,y1)
					final double yy0 = Math.max(y0, sy);
					final double yy1 = Math.min(y1, sy + 1);
					final double vy = Math.max(0.0, yy1 - yy0);
					if (vy <= 0)
						continue;

					for (int sx = ix0; sx <= ix1; sx++) {
						// horizontal overlap of this source col with [x0,x1)
						final double xx0 = Math.max(x0, sx);
						final double xx1 = Math.min(x1, sx + 1);
						final double vx = Math.max(0.0, xx1 - xx0);
						final double w = vx * vy;
						if (w <= 0)
							continue;

						final int argb = pr.getArgb(sx, sy);
						final int a = (argb >>> 24) & 0xFF;
						final int r = (argb >>> 16) & 0xFF;
						final int g = (argb >>> 8) & 0xFF;
						final int b = (argb) & 0xFF;

						// Standard Rec. 709 luma, premultiplied by alpha so transparent pixels don't
						// dominate
						final double ya = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 * (a / 255.0);

						accum += ya * w;
						weightSum += w;
					}
				}

				// Normalize by window area (should equal (x1-x0)*(y1-y0), but weightSum is
				// robust at edges)
				final double avg = (weightSum > 0.0) ? (accum / weightSum) : 0.0;
				out[y * W + x] = (float) avg;
			}
		}
		return out;
	}

	private static void fillExtraVectorFeatures(java.util.List<Vector> vectors, float[] luma64, int w, int h,
			float[] sobelGx, float[] sobelGy // may be null if you don't have them; pass nulls to auto-compute simple
												// grads
	) {
		if (w != 64 || h != 64)
			throw new IllegalArgumentException("Expected 64x64 luma");

		if (sobelGx == null || sobelGy == null) {
			sobelGx = new float[w * h];
			sobelGy = new float[w * h];
			for (int y = 1; y < h - 1; y++) {
				for (int x = 1; x < w - 1; x++) {
					int i = y * w + x;
					sobelGx[i] = (luma64[i + 1] - luma64[i - 1]) * 0.5f;
					sobelGy[i] = (luma64[i + w] - luma64[i - w]) * 0.5f;
				}
			}
		}

		// Global grad mean for normalization
		double gradSumAll = 0.0;
		int countAll = 0;
		for (int i = 0; i < w * h; i++) {
			double gx = sobelGx[i], gy = sobelGy[i];
			gradSumAll += Math.hypot(gx, gy);
			countAll++;
		}
		final double globalGradMean = Math.max(1e-6, gradSumAll / countAll);

		// Threshold for corners relative to global gradient
		final double cornerTau = cornerTauScale * globalGradMean; // tweakable

		final int CELL = 8; // 64/8
		int vIdx = 0;

		for (int by = 0; by < 8; by++) {
			for (int bx = 0; bx < 8; bx++, vIdx++) {
				Vector v = vectors.get(vIdx);

				// Accumulators
				double sum = 0, sum2 = 0;
				int[] hist = new int[32];
				double gradSum = 0;
				int corners = 0;

				// symmetry (L<->R inside the 8x8 cell)
				double diffSum = 0, energySum = 0;

				// motion
				double motionSum = 0;
				boolean hasPrev = (prevLuma64 != null && prevLumaW == w && prevLumaH == h);

				// cell bounds in 64x64 space
				int x0 = bx * CELL, y0 = by * CELL;

				for (int dy = 0; dy < CELL; dy++) {
					int y = y0 + dy;
					for (int dx = 0; dx < CELL; dx++) {
						int x = x0 + dx;
						int i = y * w + x;

						double Y = luma64[i]; // 0..1
						sum += Y;
						sum2 += Y * Y;

						// entropy hist (32 bins)
						int bin = (int) Math.floor(Math.min(31, Math.max(0, (int) (Y * 32))));
						hist[bin]++;

						// gradients
						double gx = sobelGx[i], gy = sobelGy[i];
						double g = Math.hypot(gx, gy);
						gradSum += g;
						if (Math.abs(gx) > cornerTau && Math.abs(gy) > cornerTau)
							corners++;

						// symmetry L<->R in the 8x8 subimage
						int mirrorX = x0 + (CELL - 1 - dx);
						int j = y * w + mirrorX;
						double Ym = luma64[j];
						double d = (Y - Ym);
						diffSum += d * d;
						energySum += Y * Y + Ym * Ym;

						// motion vs previous luma
						if (hasPrev)
							motionSum += Math.abs(Y - prevLuma64[i]);
					}
				}

				// --- rmsContrast
				double N = CELL * CELL;
				double mean = sum / N;
				double var = Math.max(0, (sum2 / N) - (mean * mean));
				double std = Math.sqrt(var);
				// remove the old "if (abs(gx)>cornerTau && abs(gy)>cornerTau) corners++;"
				// first pass: keep gradSum as you already do, no corner counting

				// after computing gradSum and N for the cell:
				double localTau = cornerTauScale * 1.2 * (gradSum / N);
				double tau = Math.max(localTau, 0.5 * globalGradMean);

				int cHits = 0;
				for (int dy = 0; dy < CELL; dy++) {
					int y = y0 + dy;
					for (int dx = 0; dx < CELL; dx++) {
						int x = x0 + dx;
						int i = y * w + x;
						double gx = sobelGx[i], gy = sobelGy[i];
						if (Math.abs(gx) > tau && Math.abs(gy) > tau)
							cHits++;
					}
				}
				v.cornerDensity = clamp01(cHits / (double) N);

				v.rmsContrast = clamp01(std / 0.5);

				// --- entropy (normalize by log2(32)=5)
				double H = 0.0;
				for (int c : hist)
					if (c > 0) {
						double p = c / N;
						H -= p * (Math.log(p) / Math.log(2));
					}
				v.entropy = clamp01(H / 5.0);

				// --- cornerDensity
				v.cornerDensity = clamp01(corners / N);

				// --- hfEnergy: mean gradient vs global mean
				double gradMean = gradSum / N;
				v.hfEnergy = clamp01(gradMean / (globalGradMean + 1e-9));

				// --- symLR: 1 - sqrt( diff / energy )
				double sym = 1.0 - Math.sqrt(diffSum / (energySum + 1e-9));
				v.symLR = clamp01(sym);

				// --- motion: mean |Δluma| in this cell (if prev exists)
				if (hasPrev) {
					v.motion = clamp01(motionSum / N * 4.0); // *4 to expand typical small deltas
				} else {
					v.motion = 0.0;
				}
			}
		}

		if (prevLuma64 == null || prevLuma64.length != luma64.length) {
			prevLuma64 = new float[luma64.length];
		}
		System.arraycopy(luma64, 0, prevLuma64, 0, luma64.length);
		prevLumaW = w;
		prevLumaH = h;

	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : (x > 1 ? 1 : x);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * (t < 0 ? 0 : (t > 1 ? 1 : t));
	}

	public static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public void setImage(Image loadedImage2) {
		loadedImage = loadedImage2;
	}

	public void setImageView(ImageView imageView2) {
		imageView = imageView2;
	}
	
	public synchronized static Vector getVec(int i) {

		return vectors.get(i);
	}

	public AudioContext getCTX() {
		// TODO Auto-generated method stub
		return ctx;
	}








	


}
