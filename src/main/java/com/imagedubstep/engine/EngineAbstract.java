package com.imagedubstep.engine;

import java.util.List;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.engine.VectorEngine.Rate;
import com.imagedubstep.engine.VectorEngine.Vector;
import com.imagedubstep.fx.FxRackNode;
import com.imagedubstep.nodes.BassWobbleNode.Waveform;

import javafx.scene.image.ImageView;

public class EngineAbstract {
	private AudioContext ctx = null;
	private double tempo = 0;
	private double bassPitch = 0;
	private boolean loopEnabled = false;
	private List<Vector> vectors = null;
	private double subBassDecay = 0;
	private double subBassFreq = 0;
	private double subBassVolume = 0;
	private double subBassAttack = 0;
	private double subBassHarmonicMix = 0;
	double subBassDrive = 0;
	double subBassStereo = 0;
	com.imagedubstep.fx.FxRackNode fx = null;
	boolean kickEnabled = false;
	double kickVolume = 0;
	double kickAttack = 0;
	double kickDecay = 0;
	double kickStartHz = 0;
	double kickEndHz = 0;
	double kickDrive = 0;
	double kickToneMix = 0;
	double redKickAmp = 0;
	double kickRedThreshold = 0;
	double energyKickAmp = 0;
	double kickEnergyThreshold = 0;
	Rate kickRate = null;
	int kickPhase = 0;
	boolean bassEnabled = false;
	Waveform bassWaveform = null;
	double bassVolume = 0;
	double bassFilterFreq = 0;
	double bassResonance = 0;
	double bassLfoRate = 0;
	double bassLfoDepth = 0;
	double bassDuration = 0;
	double wantBassBlueThreshold = 0;
	double midTakenBrightnessThreshold = 0;
	double wantBassbrightnessThreshold = 0;
	double midTakenBlueThreshold = 0;
	Rate bassRate = null;
	int bassPhase = 0;
	boolean subBassEnabled = false;
	Rate subRate = null;
	int subPhase = 0;
	boolean snareEnabled = false;
	double snareVolume = 0;
	double snareFilterFreq = 0;
	double snareDecay = 0;
	double snareGreenThreshold = 0;
	boolean hiHatEnabled = false;
	double hiHatVolume = 0;
	double hiHatFilterFreq = 0;
	double hiHatSaturationThreshold = 0;
	boolean linePercEnabled = false;
	double linePercVolume = 0;
	double linePercDecay = 0;
	double linePercBaseFreq = 0;
	double linePercFreqRange = 0;
	double linePercNoiseBlend = 0;
	double linePercQ = 0;
	double lineLineDensityThreshold = 0;
	boolean highFreqEnabled = false;
	double highFreqVolume = 0;
	double highFreqDecay = 0;
	double highFreqOscMix = 0;
	double highFreqHpCut = 0;
	double highFreqDetune = 0;
	double freqLineDensityThreshold = 0;

	double freqBrightnessThreshold = 0;
	boolean mixLowBlend = false;
	boolean mixHighTwoLayers = false;
	double mixSubNudgeMs = 0;
	double mixSubDuckMs = 0;
	double mixHighStaggerMs = 0;
	double mixLowBlendTrim = 0;
	double mixHighTrim = 0;
	double mixHatTrim = 0;
	ImageView imageView = null;
    public AudioContext getCTX() { return ctx; }
    public void setCtx(AudioContext ctx) { this.ctx = ctx; }

    public double getTempo() { return tempo; }
    public void setTempo(double tempo) { this.tempo = tempo; }

    public double getBassPitch() { return bassPitch; }
    public void setBassPitch(double bassPitch) { this.bassPitch = bassPitch; }

    public boolean isLoopEnabled() { return loopEnabled; }
    public void setLoopEnabled(boolean loopEnabled) { this.loopEnabled = loopEnabled; }

    public List<Vector> getVectors() { return vectors; }
    public void setVectors(List<Vector> vectors) { this.vectors = vectors; }

    public double getSubBassDecay() { return subBassDecay; }
    public void setSubBassDecay(double subBassDecay) { this.subBassDecay = subBassDecay; }

    public double getSubBassFreq() { return subBassFreq; }
    public void setSubBassFreq(double subBassFreq) { this.subBassFreq = subBassFreq; }

    public double getSubBassVolume() { return subBassVolume; }
    public void setSubBassVolume(double subBassVolume) { this.subBassVolume = subBassVolume; }

    public double getSubBassAttack() { return subBassAttack; }
    public void setSubBassAttack(double subBassAttack) { this.subBassAttack = subBassAttack; }

    public double getSubBassHarmonicMix() { return subBassHarmonicMix; }
    public void setSubBassHarmonicMix(double subBassHarmonicMix) { this.subBassHarmonicMix = subBassHarmonicMix; }

    public double getSubBassDrive() { return subBassDrive; }
    public void setSubBassDrive(double subBassDrive) { this.subBassDrive = subBassDrive; }

    public double getSubBassStereo() { return subBassStereo; }
    public void setSubBassStereo(double subBassStereo) { this.subBassStereo = subBassStereo; }

    public FxRackNode getFx() { return fx; }
    public void setFx(FxRackNode fx) { this.fx = fx; }

    public boolean isKickEnabled() { return kickEnabled; }
    public void setKickEnabled(boolean kickEnabled) { this.kickEnabled = kickEnabled; }

    public double getKickVolume() { return kickVolume; }
    public void setKickVolume(double kickVolume) { this.kickVolume = kickVolume; }

    public double getKickAttack() { return kickAttack; }
    public void setKickAttack(double kickAttack) { this.kickAttack = kickAttack; }

    public double getKickDecay() { return kickDecay; }
    public void setKickDecay(double kickDecay) { this.kickDecay = kickDecay; }

    public double getKickStartHz() { return kickStartHz; }
    public void setKickStartHz(double kickStartHz) { this.kickStartHz = kickStartHz; }

    public double getKickEndHz() { return kickEndHz; }
    public void setKickEndHz(double kickEndHz) { this.kickEndHz = kickEndHz; }

    public double getKickDrive() { return kickDrive; }
    public void setKickDrive(double kickDrive) { this.kickDrive = kickDrive; }

    public double getKickToneMix() { return kickToneMix; }
    public void setKickToneMix(double kickToneMix) { this.kickToneMix = kickToneMix; }
}
