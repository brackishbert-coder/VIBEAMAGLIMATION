package com.imagedubstep.util;

/** Time-variant Stable SVF (Zavalishin TPT). Double precision. */
public final class TptSvf {
    private final double fs;
    private double g = 0.0;     // tan(pi * fc / fs)
    private double R = 1.0;     // 1/Q
    private double ic1eq = 0.0; // integrator 1 state
    private double ic2eq = 0.0; // integrator 2 state

    public TptSvf(double sampleRate){
        this.fs = sampleRate;
    }

    public void reset(){
        ic1eq = 0.0; ic2eq = 0.0;
    }

    public void setCutoffQ(double fcHz, double q){
        // Clamp to safe range (leave some headroom below Nyquist)
        double ny = 0.45 * fs;
        double fc = fcHz < 10.0 ? 10.0 : (fcHz > ny ? ny : fcHz);
        double Q  = q < 0.2 ? 0.2 : (q > 20.0 ? 20.0 : q);
        g = Math.tan(Math.PI * fc / fs);
        R = 1.0 / Q;
    }

    /** Process a single sample as lowpass using the *current* cutoff/Q. */
    public double processLow(double x){
        // TPT SVF core (Zavalishin)
        double v1 = (x - ic2eq - R * ic1eq) / (1.0 + R * g + g * g);
        double v2 = v1 * g;
        double v3 = v2 * g;
        double low = v3 + ic2eq;
        double band = v2 + ic1eq;

        // Update states ("equalized" integrators)
        ic1eq = band + v2;
        ic2eq = low  + v3;
        return low;
    }

    /** Convenience: update cutoff/Q then process (handy for per-sample modulation). */
    public double processLowMod(double x, double fcHz, double q){
        setCutoffQ(fcHz, q);
        return processLow(x);
    }
}
