package com.imagedubstep.som;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Simple Canvas renderer for SOM (U-Matrix or single weight dim). */
public final class SOMView extends Canvas {
    public enum Mode { U_MATRIX, WEIGHT_DIM }

    private final SOM som;
    private Mode mode = Mode.U_MATRIX;
    private int dimIndex = 0;
    private volatile int lastBMUx = -1, lastBMUy = -1;
    private final float[] tmp;
    public SOMView(SOM som, double w, double h) {
        super(w, h);
        this.som = som;
        this.tmp = new float[som.dim];
    }

    public void setMode(Mode m){ this.mode = m; redraw(); }
    public void setDimIndex(int i){
        this.dimIndex = Math.max(0, Math.min(som.dim-1, i));
        redraw();
    }
    /** Optional: outline a cell for a frame. */
    public void pingBMU(int x, int y){ lastBMUx = x; lastBMUy = y; }

    public void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double W = getWidth(), H = getHeight();
        g.setFill(Color.BLACK); g.fillRect(0,0,W,H);

        int gw = som.w, gh = som.h;
        double cw = W / gw, ch = H / gh;

        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                double v;
                switch (mode) {
                    case U_MATRIX:
                        v = normalizeUMatrix(x, y);
                        break;
                    case WEIGHT_DIM:
                        v = getWeightNormalized(x, y, dimIndex);
                        break;
                    default:
                        v = 0.0;
                }
                Color c = heat(v);
                g.setFill(c);
                g.fillRect(x*cw, y*ch, cw+1, ch+1);
            }
        }

        if (lastBMUx >= 0) {
            g.setStroke(Color.WHITE);
            g.setLineWidth(2.0);
            g.strokeRect(lastBMUx*cw+0.5, lastBMUy*ch+0.5, cw-1, ch-1);
            // fade the highlight quickly
            lastBMUx = -1; lastBMUy = -1;
        }
    }

    private double normalizeUMatrix(int x, int y) {
        double v = som.uDistance(x,y);
        return clamp01(v * 3.0); // boost contrast; adjust to taste
    }
    private double getWeightNormalized(int x, int y, int k) {
        som.get(x, y, tmp);              // reuses tmp (no new array)
        double v = tmp[k];
        return clamp01(v + 0.5);         // same visualization
    }

    private static double clamp01(double x){ return x<0?0:(x>1?1:x); }
    private static Color heat(double t){
        // blue→green→yellow→red
        double r = clamp01(1.5*t - 0.5);
        double g = clamp01(1.5 - Math.abs(2*t - 1.0));
        double b = clamp01(1.5*(1.0 - t) - 0.5);
        return new Color(r, g, b, 1.0);
    }
}
