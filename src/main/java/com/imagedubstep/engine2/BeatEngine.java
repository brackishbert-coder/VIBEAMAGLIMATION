package com.imagedubstep.engine2;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;




public  class BeatEngine {
    private final BeatGenerator generator;
    private final BeatSequencer sequencer;

    public BeatEngine(NoteSink sink) {
        this.generator = new BeatGenerator();
        this.sequencer = new BeatSequencer(sink);
    }

    /** Generate a pattern from SOM neurons and apply it to the sequencer. */
    public BeatPattern buildAndSetPattern(List<SOMNeuron> neurons, int steps) {
        BeatPattern p = generator.generate(neurons, steps);
        sequencer.setPattern(p);
        return p;
    }

    public void setBpm(int bpm) { sequencer.setBpm(bpm); }
    /** 4 => 16th notes like your JS engine’s step (recommended) */
    public void setSubdivision(int subdivision) { sequencer.setSubdivision(subdivision); }
    public void start() { sequencer.start(); }
    public void stop() { sequencer.stop(); }
    public BeatSequencer getSequencer() { return sequencer; }
}
