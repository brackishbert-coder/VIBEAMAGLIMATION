
package com.imagedubstep.util;
public class FastSin {
    public static float sin(double x){
        x = x % (2*Math.PI);
        if (x > Math.PI) x -= 2*Math.PI;
        if (x < -Math.PI) x += 2*Math.PI;
        double x2 = x*x;
        double s = x * (1 - x2/6 + x2*x2/120 - x2*x2*x2/5040);
        return (float)s;
    }
}
