
package com.imagedubstep.util;
public class SoftClip {
    public static float clip(float x){
        return (float)(x / (1.0 + Math.abs(x)));
    }
}
