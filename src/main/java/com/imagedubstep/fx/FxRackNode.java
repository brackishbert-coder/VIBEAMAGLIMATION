package com.imagedubstep.fx;

import com.imagedubstep.core.*;

public class FxRackNode extends AudioNode {
	private final AudioNode chainEnd;
	public final EchoNode echo;
	public final ChorusNode chorus;
	public final WidenerNode widen;
	public final ReverbNode reverb;
	public final AutoWahNode wah;
	public final PhaserNode phaser;
	public final FlangerNode flanger;
	public final TremoloPanNode tremPan;
	public final BitCrusherNode crusher;

	public volatile boolean onWah = false, onPhaser = false, onFlanger = false, onTrem = false, onCrush = false;

	public volatile boolean onEcho = false;
	public volatile boolean onChorus = false;
	public volatile boolean onWiden = false;
	public volatile boolean onReverb = false;

	public FxRackNode(AudioContext ctx, AudioNode source) {
		super(ctx);
		wah = new AutoWahNode(ctx, source);
		phaser = new PhaserNode(ctx, wah);
		flanger = new FlangerNode(ctx, phaser);
		tremPan = new TremoloPanNode(ctx, flanger);
		crusher = new BitCrusherNode(ctx, tremPan);
		echo = new EchoNode(ctx, crusher, 280, 0.35, 0.25);
		chorus = new ChorusNode(ctx, echo, 8.0, 0.6, 0.20);
		widen = new WidenerNode(ctx, chorus);
		reverb = new ReverbNode(ctx, widen);
		chainEnd = reverb;
	}

	@Override
	public void process(float[] L, float[] R, int n) {
		// enable/disable by bypass mixing
		AudioNode head = chainEnd; // always pulls entire chain
		float[] tL = new float[n], tR = new float[n];
		// temporarily toggle stages by changing wet/dry quickly:
		boolean saveEcho = onEcho;
		boolean saveChorus = onChorus;
		boolean saveWiden = onWiden;
		boolean saveReverb = onReverb;

		// crude but effective bypass: if off, set stage wet=0 (for echo/chorus/reverb),
		// widen=1.0
		float oldEchoWet = echo != null ? getField(echo, "wet", 0.25f) : 0;
		float oldChWet = chorus != null ? getField(chorus, "wet", 0.2f) : 0;

		if (!saveEcho)
			echo.setWet(0.0);
		if (!saveChorus)
			chorus.setWet(0.0);
		if (!saveReverb)
			reverb.setWet(0.0);
		if (!saveWiden)
			widen.setSideGain(1.0);
		boolean sWah = onWah, sPh = onPhaser, sFl = onFlanger, sTr = onTrem, sCr = onCrush;

		float saveWahWet = getField(wah, "wet", 1f);
		if (!sWah)
			wah.setWet(0);
		float savePhWet = getField(phaser, "wet", 0.35f);
		if (!sPh)
			phaser.setWet(0);
		float saveFlWet = getField(flanger, "wet", 0.4f);
		if (!sFl)
			flanger.setWet(0);
		double[] saveTP = { tremPan != null ? 1 : 1 }; // tremPan is multiplicative; bypass by depths=0
		if (!sTr) {
			tremPan.setTremolo(0, 4.0);
			tremPan.setPan(0, .25);
		}
		float saveCrWet = getField(crusher, "wet", 0.25f);
		if (!sCr)
			crusher.setWet(0);
		head.process(tL, tR, n);
		for (int i = 0; i < n; i++) {
			L[i] += tL[i];
			R[i] += tR[i];
		}

		// restore defaults (we don't want persistent changes from bypass)
		if (!saveEcho)
			echo.setWet(oldEchoWet);
		if (!saveChorus)
			chorus.setWet(oldChWet);
		if (!saveReverb)
			reverb.setWet(0.2);
		if (!saveWiden)
			widen.setSideGain(1.2);
		if (!sWah)
			wah.setWet(saveWahWet);
		if (!sPh)
			phaser.setWet(savePhWet);
		if (!sFl)
			flanger.setWet(saveFlWet);
		if (!sTr) {
			tremPan.setTremolo(0.35, 4.0);
			tremPan.setPan(0.5, 0.25);
		} // restore defaults
		if (!sCr)
			crusher.setWet(saveCrWet);
	}

	// helper to peek volatile field defaults via reflection; safe fallback if fails
	private static float getField(Object o, String name, float def) {
		try {
			var f = o.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.getFloat(o);
		} catch (Exception e) {
			return def;
		}
	}

	@Override
	public boolean isDone() {
		return chainEnd.isDone();
	}
}
