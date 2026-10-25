package net.hugosmp.auction;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix3x2fStack;

/** Kleine Zeichenhelfer. Alle Farben sind ARGB. */
final class Gfx {
    private Gfx() {}

    static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static double ease(double t) {
        t = clamp01(t);
        return 1 - Math.pow(1 - t, 3);
    }

    static int lerpColor(int c1, int c2, double t) {
        t = clamp01(t);
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }

    /** Rechteck mit leicht abgerundeten Ecken. */
    static void rect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1 + 1, y1, x2 - 1, y2, color);
        c.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    static Text styled(String s, boolean bold) {
        return bold ? Text.literal(s).formatted(Formatting.BOLD) : Text.literal(s);
    }

    static int width(TextRenderer tr, String s, boolean bold) {
        return tr.getWidth(styled(s, bold));
    }

    private static int fixAlpha(int color) {
        int a = color >>> 24;
        if (a == 0) return color | 0xFF000000;
        return color;
    }

    static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color, boolean bold) {
        if ((color >>> 24) != 0 && (color >>> 24) < 6) return;
        c.drawTextWithShadow(tr, styled(s, bold), x, y, fixAlpha(color));
    }

    static void centered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color, boolean bold) {
        text(c, tr, s, cx - width(tr, s, bold) / 2, y, color, bold);
    }

    static void right(DrawContext c, TextRenderer tr, String s, int rx, int y, int color, boolean bold) {
        text(c, tr, s, rx - width(tr, s, bold), y, color, bold);
    }

    static void scaledText(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int color, boolean bold) {
        if ((color >>> 24) != 0 && (color >>> 24) < 6) return;
        Matrix3x2fStack st = c.getMatrices();
        st.pushMatrix();
        st.translate(x, y);
        st.scale(scale, scale);
        c.drawTextWithShadow(tr, styled(s, bold), 0, 0, fixAlpha(color));
        st.popMatrix();
    }

    static void scaledItem(DrawContext c, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack st = c.getMatrices();
        st.pushMatrix();
        st.translate(x, y);
        st.scale(scale, scale);
        c.drawItem(stack, 0, 0);
        st.popMatrix();
    }

    static String trunc(TextRenderer tr, String s, int maxW, boolean bold) {
        if (width(tr, s, bold) <= maxW) return s;
        while (s.length() > 1 && width(tr, s + "..", bold) > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    static void gradientV(DrawContext c, int x1, int y1, int x2, int y2, int rgb1, int rgb2, int alpha) {
        int h = y2 - y1;
        if (h <= 0) return;
        for (int i = 0; i < h; i++) {
            double t = h == 1 ? 0 : i / (double) (h - 1);
            c.fill(x1, y1 + i, x2, y1 + i + 1, argb(alpha, lerpColor(rgb1, rgb2, t)));
        }
    }

    static void outline(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1, y1, x2, y1 + 1, color);
        c.fill(x1, y2 - 1, x2, y2, color);
        c.fill(x1, y1, x1 + 1, y2, color);
        c.fill(x2 - 1, y1, x2, y2, color);
    }

    /** Weicher Lichtfleck aus ineinander liegenden Rechtecken. */
    static void glow(DrawContext c, int cx, int cy, int radius, int rgb, int maxAlpha) {
        for (int r = radius; r > 0; r -= 2) {
            double k = 1 - (r / (double) radius);
            rect(c, cx - r, cy - r, cx + r, cy + r, argb((int) (maxAlpha * k * k * 0.5), rgb));
        }
    }

    static void bar(DrawContext c, int x1, int y, int x2, int h, double frac, int rgb) {
        c.fill(x1, y, x2, y + h, 0xFF22222C);
        int w = (int) ((x2 - x1) * clamp01(frac));
        if (w > 0) {
            c.fill(x1, y, x1 + w, y + h, argb(255, rgb));
            c.fill(x1, y, x1 + w, y + Math.max(1, h / 3), argb(70, 0xFFFFFF));
        }
    }

    /** Funkenstoß, rein zeitgesteuert (age in ms seit dem Auslösen). */
    static void burst(DrawContext c, int cx, int cy, long age, int count, int[] palette, double spread, long dur) {
        if (age < 0 || age >= dur) return;
        double t = age / (double) dur;
        double e = ease(t);
        for (int i = 0; i < count; i++) {
            double ang = Math.toRadians((i * 137.5) % 360);
            double dist = (0.4 + ((i * 7) % 10) / 10.0) * spread * e;
            int px = cx + (int) (Math.cos(ang) * dist);
            int py = cy + (int) (Math.sin(ang) * dist + e * e * 10);
            int a = (int) (255 * (1 - t));
            int size = (i % 3 == 0) ? 2 : 1;
            c.fill(px, py, px + size, py + size, argb(a, palette[i % palette.length]));
        }
    }

    /** Kleine Krone, 9x6 Pixel. */
    static void crown(DrawContext c, int x, int y, int color) {
        c.fill(x, y + 2, x + 1, y + 6, color);
        c.fill(x + 4, y, x + 5, y + 6, color);
        c.fill(x + 8, y + 2, x + 9, y + 6, color);
        c.fill(x, y + 4, x + 9, y + 6, color);
        c.fill(x + 2, y + 3, x + 3, y + 4, color);
        c.fill(x + 6, y + 3, x + 7, y + 4, color);
    }

    /** Wandernder Lichtstreifen. */
    static void shimmer(DrawContext c, int x1, int y1, int x2, int y2, long now, int period) {
        int w = x2 - x1;
        int bx = x1 + (int) ((now % period) / (double) period * (w + 40)) - 20;
        for (int i = 0; i < 12; i++) {
            int sx = bx + i;
            if (sx < x1 || sx >= x2) continue;
            int a = (int) (46 * (1 - Math.abs(i - 6) / 6.0));
            c.fill(sx, y1, sx + 1, y2, argb(a, 0xFFFFFF));
        }
    }

    static int barColor(double frac) {
        if (frac > 0.5) return lerpColor(0xFFAA00, 0x55FF55, (frac - 0.5) * 2);
        return lerpColor(0xFF5555, 0xFFAA00, frac * 2);
    }
}
