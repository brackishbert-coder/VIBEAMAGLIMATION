package com.imagedubstep.util;

import com.imagedubstep.engine.VectorEngine.Vector;

import javafx.scene.paint.Color;

public class Util {

	public static ViewMode viewMode = ViewMode.RGB;

	// keep your enum ViewMode as-is (RGB, LUMA, ENERGY, SAT, CONTRAST, ENTROPY, HF,
	// CORNERS, SYM, MOTION, ORIENT):contentReference[oaicite:3]{index=3}
	// NEW: reusable helper so any canvas can render any mode
	public static Color colorFor(ViewMode mode, Vector v) {
		switch (mode) {
		case LUMA: {
			double y = v.brightness;
			return new Color(y, y, y, 1);
		}
		case ENERGY: {
			double e = v.energy;
			return new Color(e, 0, 0, 1);
		} // red heat
		case SAT: {
			double s = v.saturation;
			return new Color(0, s, 0, 1);
		} // green heat
		case CONTRAST: {
			double c = v.rmsContrast;
			return new Color(c, c, c, 1);
		}
		case ENTROPY: {
			double h = v.entropy;
			return new Color(h, 0, h, 1);
		} // magenta heat
		case HF: {
			double h = v.hfEnergy;
			return new Color(0, 0, h, 1);
		} // blue heat
		case CORNERS: {
			double c = v.cornerDensity;
			return new Color(c, c * 0.5, 0, 1);
		}
		case SYM: {
			double s = v.symLR;
			return new Color(0, s, s, 1);
		} // cyan-ish
		case MOTION: {
			double m = v.motion;
			return new Color(m, m * 0.2, 0, 1);
		}
		case ORIENT: {
			double a = v.lineOrientation;
			double d = v.lineDensity;
			return Color.hsb(a * 360.0, 1.0, Math.max(0.15, d));
		}
		case RGB:
		default:
			return new Color(map(v.cr, 0.0, 255.0, 0.0, 1.0), map(v.cg, 0.0, 255.0, 0.0, 1.0),
					map(v.cb, 0.0, 255.0, 0.0, 1.0), 1.0);
		}
	}

	public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
		return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
	}

	// and change your old single-view helper to:
	public static Color colorFor(Vector v) {
		return colorFor(viewMode, v); // uses the current drop-down selection for the small grid
	}
}
