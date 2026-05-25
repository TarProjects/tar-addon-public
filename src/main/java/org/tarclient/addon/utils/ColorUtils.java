package org.tarclient.addon.utils;

import meteordevelopment.meteorclient.utils.render.color.Color;

public class ColorUtils {
    public static Color lerp(Color start, Color end, double t) {
        int r = (int) Math.round(start.r + (end.r - start.r) * t);
        int g = (int) Math.round(start.g + (end.g - start.g) * t);
        int b = (int) Math.round(start.b + (end.b - start.b) * t);
        int a = (int) Math.round(start.a + (end.a - start.a) * t);

        // clamp for safety
        r = Math.clamp(r, 0, 255);
        g = Math.clamp(g, 0, 255);
        b = Math.clamp(b, 0, 255);
        a = Math.clamp(a, 0, 255);

        return new Color(r, g, b, a);
    }
}
