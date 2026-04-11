package com.imagedubstep.engine2;

public final class BeatPattern {
    public final int steps;
    public final boolean[] kick, snare, hihat, bass;
    public final double[] kickVel, snareVel, hihatVel, bassVel;

    public BeatPattern(int steps) {
        this.steps = steps;
        this.kick  = new boolean[steps];
        this.snare = new boolean[steps];
        this.hihat = new boolean[steps];
        this.bass  = new boolean[steps];
        this.kickVel  = new double[steps];
        this.snareVel = new double[steps];
        this.hihatVel = new double[steps];
        this.bassVel  = new double[steps];
    }

    public boolean[] get(Instrument i) {
        switch (i) {
            case KICK:
                return kick;
            case SNARE:
                return snare;
            case HIHAT:
                return hihat;
            case BASS:
                return bass;
            default:
                throw new IllegalArgumentException("Unknown instrument: " + i);
        }
    }


    public double[] getVel(Instrument i) {
       switch (i) {
        case KICK:
            return kickVel;
        case SNARE:
            return snareVel;
        case HIHAT:
            return hihatVel;
        case BASS:
            return bassVel;
        default:
            throw new IllegalArgumentException("Unknown instrument: " + i);
    }
}}