package com.saasno.utility;

// The brand colour as a #rrggbb string for emails (mail clients do not understand CSS variables or oklch). Same formula as the
// frontend's --color-brand-600: oklch(54.1% 0.281*chroma hue).
public final class BrandColor {

	private BrandColor() {
	}

	public static String hex(int hue, double chroma) {
		double l = 0.541;
		double c = 0.281 * chroma;
		double a = c * Math.cos(Math.toRadians(hue));
		double b = c * Math.sin(Math.toRadians(hue));

		double l_ = Math.pow(l + 0.3963377774 * a + 0.2158037573 * b, 3);
		double m_ = Math.pow(l - 0.1055613458 * a - 0.0638541728 * b, 3);
		double s_ = Math.pow(l - 0.0894841775 * a - 1.2914855480 * b, 3);

		double r = 4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_;
		double g = -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_;
		double bl = -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_;
		return String.format("#%02x%02x%02x", channel(r), channel(g), channel(bl));
	}

	private static int channel(double linear) {
		double clamped = Math.max(0, Math.min(1, linear));
		double srgb = clamped <= 0.0031308 ? 12.92 * clamped : 1.055 * Math.pow(clamped, 1 / 2.4) - 0.055;
		return (int) Math.round(Math.max(0, Math.min(1, srgb)) * 255);
	}

}
