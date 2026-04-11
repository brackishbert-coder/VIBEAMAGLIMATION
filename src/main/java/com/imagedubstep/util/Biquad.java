
package com.imagedubstep.util;

public class Biquad {
    public enum Type { LOWPASS, HIGHPASS, BANDPASS }
    private final double sr;
    private double a1,a2,b0,b1,b2;
    private float z1L=0,z2L=0,z1R=0,z2R=0;
    private Type type = Type.LOWPASS;
    private double lastF=1000, lastQ=0.707;

    public Biquad(double sr){ this.sr = sr; set(Type.LOWPASS, 1000, 0.707); }

    private void setCoeffs(double b0, double b1, double b2,
            double a0, double a1, double a2) {
this.b0 = b0 / a0;
this.b1 = b1 / a0;
this.b2 = b2 / a0;
this.a1 = a1 / a0;
this.a2 = a2 / a0;
}

    
    public void setPeak(double freqHz, double q, double gainDb) {
        double A  = Math.pow(10, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double alpha = Math.sin(w0) / (2.0 * q);

        double b0 = 1 + alpha * A;
        double b1 = -2 * Math.cos(w0);
        double b2 = 1 - alpha * A;
        double a0 = 1 + alpha / A;
        double a1 = -2 * Math.cos(w0);
        double a2 = 1 - alpha / A;

        setCoeffs(b0, b1, b2, a0, a1, a2);
    }

    public void setLowShelf(double freqHz, double q, double gainDb) {
        double A  = Math.pow(10, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cosw0 = Math.cos(w0);

        double b0 =    A*((A+1) - (A-1)*cosw0 + 2*Math.sqrt(A)*alpha);
        double b1 =  2*A*((A-1) - (A+1)*cosw0);
        double b2 =    A*((A+1) - (A-1)*cosw0 - 2*Math.sqrt(A)*alpha);
        double a0 =       (A+1) + (A-1)*cosw0 + 2*Math.sqrt(A)*alpha;
        double a1 =   -2*((A-1) + (A+1)*cosw0);
        double a2 =       (A+1) + (A-1)*cosw0 - 2*Math.sqrt(A)*alpha;

        setCoeffs(b0, b1, b2, a0, a1, a2);
    }

    public void setHighShelf(double freqHz, double q, double gainDb) {
        double A  = Math.pow(10, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cosw0 = Math.cos(w0);

        double b0 =    A*((A+1) + (A-1)*cosw0 + 2*Math.sqrt(A)*alpha);
        double b1 = -2*A*((A-1) + (A+1)*cosw0);
        double b2 =    A*((A+1) + (A-1)*cosw0 - 2*Math.sqrt(A)*alpha);
        double a0 =       (A+1) - (A-1)*cosw0 + 2*Math.sqrt(A)*alpha;
        double a1 =    2*((A-1) - (A+1)*cosw0);
        double a2 =       (A+1) - (A-1)*cosw0 - 2*Math.sqrt(A)*alpha;

        setCoeffs(b0, b1, b2, a0, a1, a2);
    }

    
    public void set(Type type, double cutoff, double q){
        this.type = type; this.lastF=cutoff; this.lastQ=q;
        double w0 = 2*Math.PI*cutoff/sr;
        double alpha = Math.sin(w0)/(2*q);
        double cos = Math.cos(w0);
        double b0_, b1_, b2_, a0_, a1_, a2_;

        if(type == Type.LOWPASS){
            b0_ = (1 - cos)/2.0; b1_ = 1 - cos; b2_ = (1 - cos)/2.0;
            a0_ = 1 + alpha; a1_ = -2 * cos; a2_ = 1 - alpha;
        } else if(type == Type.HIGHPASS){
            b0_ = (1 + cos)/2.0; b1_ = -(1 + cos); b2_ = (1 + cos)/2.0;
            a0_ = 1 + alpha; a1_ = -2 * cos; a2_ = 1 - alpha;
        } else {
            b0_ = Math.sin(w0)/2.0; b1_ = 0; b2_ = -Math.sin(w0)/2.0;
            a0_ = 1 + alpha; a1_ = -2 * cos; a2_ = 1 - alpha;
        }

        b0 = b0_/a0_; b1 = b1_/a0_; b2 = b2_/a0_;
        a1 = a1_/a0_; a2 = a2_/a0_;
    }

    public void updateIfChanged(Type type, double f, double q){
        if(type != this.type || Math.abs(f - lastF) > 1e-6 || Math.abs(q - lastQ) > 1e-6){
            set(type,f,q);
        }
    }

    public void process(float[] L, float[] R, int n){
        for(int i=0;i<n;i++){
            float xl=L[i], xr=R[i];
            float yl = (float)(b0*xl + z1L);
            float yr = (float)(b0*xr + z1R);
            z1L = (float)(b1*xl - a1*yl + z2L);
            z1R = (float)(b1*xr - a1*yr + z2R);
            z2L = (float)(b2*xl - a2*yl);
            z2R = (float)(b2*xr - a2*yr);
            L[i]=yl; R[i]=yr;
        }
    }
}
