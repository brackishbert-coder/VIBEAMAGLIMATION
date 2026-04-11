package com.imagedubstep.engine;

import java.util.List;

import com.imagedubstep.core.AudioContext;
import com.imagedubstep.core.AudioNode;
import com.imagedubstep.engine.VectorEngine.Rate;
import com.imagedubstep.engine.VectorEngine.Vector;
import com.imagedubstep.fx.FxRackNode;
import com.imagedubstep.nodes.BassWobbleNode.Waveform;
import com.imagedubstep.nodes.SubBassNode;
import com.imagedubstep.som.SOMRuntime;

import javafx.animation.AnimationTimer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public interface EngineInterface {

	
	void setImage(Image loadedImage) ;

	void setImageView(ImageView imageView);

	void queueAnalysis();

	void startNewSession();

	void forceDubstepNow(SOMRuntime somRt);

	void cancelLoop() ;



	Vector getVec(int i);

	void addToMix(AudioNode voice);

	AudioContext getCTX();
    public void setCtx(AudioContext ctx) ;
    public double getTempo();
    public void setTempo(double tempo);

    public double getBassPitch();
    public void setBassPitch(double bassPitch) ;

    public boolean isLoopEnabled() ;
    public void setLoopEnabled(boolean loopEnabled);

    public List<Vector> getVectors() ;
    public void setVectors(List<Vector> vectors);

    public double getSubBassDecay() ;
    public void setSubBassDecay(double subBassDecay);

    public double getSubBassFreq() ;
    public void setSubBassFreq(double subBassFreq);

    public double getSubBassVolume();
    public void setSubBassVolume(double subBassVolume);

    public double getSubBassAttack();
    public void setSubBassAttack(double subBassAttack);

    public double getSubBassHarmonicMix() ;
    public void setSubBassHarmonicMix(double subBassHarmonicMix) ;

    public double getSubBassDrive();
    public void setSubBassDrive(double subBassDrive); 

    public double getSubBassStereo() ;
    public void setSubBassStereo(double subBassStereo) ;

    public FxRackNode getFx() ;
    public void setFx(FxRackNode fx); 

    public boolean isKickEnabled() ;
    public void setKickEnabled(boolean kickEnabled); 

    public double getKickVolume();
    public void setKickVolume(double kickVolume) ;

    public double getKickAttack();
    public void setKickAttack(double kickAttack); 

    public double getKickDecay() ;
    public void setKickDecay(double kickDecay);

    public double getKickStartHz(); 
    public void setKickStartHz(double kickStartHz); 

    public double getKickEndHz();
    public void setKickEndHz(double kickEndHz) ;

    public double getKickDrive() ;
    public void setKickDrive(double kickDrive);

    public double getKickToneMix(); 
    public void setKickToneMix(double kickToneMix); 
}
