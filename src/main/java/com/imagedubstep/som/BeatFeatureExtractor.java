package com.imagedubstep.som;

/** Builds a compact per-step feature vector (normalize to 0..1 where possible). */
public final class BeatFeatureExtractor {
    public static final int DIM = 16;

    public static float[] make(
        boolean wantKick, double kickAmp01, double kickStartHz, double kickEndHz,
        boolean wantSub,  double subAmp01,  double subFreqHz,
        double wobbleAmp01, double bassFilterHz, double bassLfoRate,
        double lineDensity, double brightness, double saturation, double energy,
        double hfEnergy, double subStereo01, double motion01
    ){
        float[] v = new float[DIM];
        int i=0;
        v[i++] = wantKick ? 1f:0f;
        v[i++] = c01(kickAmp01);
        v[i++] = c01((kickStartHz-32) / (160-32));  // ~32..160 Hz
        v[i++] = c01((kickEndHz  -20) / (120-20));  // ~20..120 Hz
        v[i++] = wantSub ? 1f:0f;
        v[i++] = c01(subAmp01);
        v[i++] = c01((subFreqHz-30) / (120-30));
        v[i++] = c01(wobbleAmp01);
        v[i++] = c01(bassFilterHz / 8000.0);        // assume 0..8k
        v[i++] = c01(bassLfoRate / 12.0);           // 0..12 Hz
        v[i++] = c01(lineDensity);
        v[i++] = c01(brightness);
        v[i++] = c01(saturation);
        v[i++] = c01(energy);
        v[i++] = c01(hfEnergy);
        v[i++] = c01(0.5*subStereo01 + 0.5*motion01);
        return v;
    }
    public static void fill(float[] out,
    	    boolean wantKick, double kickAmp01, double kickStartHz, double kickEndHz,
    	    boolean wantSub,  double subAmp01,  double subFreqHz,
    	    double wobbleAmp01, double bassFilterHz, double bassLfoRate,
    	    double lineDensity, double brightness, double saturation, double energy,
    	    double hfEnergy, double subStereo01, double motion01
    	){
    	    int i=0;
    	    out[i++] = wantKick ? 1f:0f;
    	    out[i++] = c01(kickAmp01);
    	    out[i++] = c01((kickStartHz-32) / (160-32));
    	    out[i++] = c01((kickEndHz  -20) / (120-20));
    	    out[i++] = wantSub ? 1f:0f;
    	    out[i++] = c01(subAmp01);
    	    out[i++] = c01((subFreqHz-30) / (120-30));
    	    out[i++] = c01(wobbleAmp01);
    	    out[i++] = c01(bassFilterHz / 8000.0);
    	    out[i++] = c01(bassLfoRate / 12.0);
    	    out[i++] = c01(lineDensity);
    	    out[i++] = c01(brightness);
    	    out[i++] = c01(saturation);
    	    out[i++] = c01(energy);
    	    out[i++] = c01(hfEnergy);
    	    out[i++] = c01(0.5*subStereo01 + 0.5*motion01);
    	}

    private static float c01(double x){
        if (x < 0.0) return 0f;
        if (x > 1.0) return 1f;
        return (float)x;
    }

    private BeatFeatureExtractor(){}
}
