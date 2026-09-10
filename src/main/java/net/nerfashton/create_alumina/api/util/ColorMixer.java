package net.nerfashton.create_alumina.api.util;

import java.util.List;

public class ColorMixer {

    public record ColorContribution(int color, int weight) {}

    /**
     * Mixes multiple ARGB colors together based on their weight (e.g., volume in mB).
     * @param contributions List of ColorContributions
     * @param defaultColor The fallback color if the list is empty
     * @return the blended ARGB color integer
     */
    public static int mixColors(List<ColorContribution> contributions, int defaultColor) {
        if (contributions == null || contributions.isEmpty()) {
            return defaultColor;
        }

        long totalWeight = 0;
        long a = 0, r = 0, g = 0, b = 0;

        for (ColorContribution contribution : contributions) {
            if (contribution.weight() <= 0) continue;

            totalWeight += contribution.weight();
            int c = contribution.color();
            a += ((c >> 24) & 0xFF) * (long) contribution.weight();
            r += ((c >> 16) & 0xFF) * (long) contribution.weight();
            g += ((c >> 8) & 0xFF) * (long) contribution.weight();
            b += (c & 0xFF) * (long) contribution.weight();
        }

        if (totalWeight <= 0) {
            return defaultColor;
        }

        int finalA = (int) (a / totalWeight) & 0xFF;
        int finalR = (int) (r / totalWeight) & 0xFF;
        int finalG = (int) (g / totalWeight) & 0xFF;
        int finalB = (int) (b / totalWeight) & 0xFF;

        return (finalA << 24) | (finalR << 16) | (finalG << 8) | finalB;
    }

    /**
     * Blends two ARGB colors together.
     */
    public static int blend(int colorA, int weightA, int colorB, int weightB) {
        return mixColors(List.of(
            new ColorContribution(colorA, weightA),
            new ColorContribution(colorB, weightB)
        ), 0xFFFFFFFF);
    }
}
