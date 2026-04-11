// com/imagedubstep/dynamics/SidechainParams.java
package com.imagedubstep.dynamics;

public final class SidechainParams {
    public volatile float depthDb   = -6.0f;   // how much to duck (dB)
    public volatile float threshold = 0.02f;   // envelope threshold to start ducking
    public volatile float ratio     = 3.0f;    // knee-ish strength
    public volatile float attackMs  = 3.0f;    // gain attack
    public volatile float releaseMs = 120.0f;  // gain release
}
