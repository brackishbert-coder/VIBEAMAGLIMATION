// com/imagedubstep/dynamics/SidechainSignal.java
package com.imagedubstep.dynamics;

public final class SidechainSignal {
    // simple thread-safe envelope value (0..~1+)
    private volatile float env;
    public void set(float v) { env = v; }
    public float get() { return env; }
}
