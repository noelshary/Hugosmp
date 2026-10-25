package net.hugosmp.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Zeichnet die Auktionsanzeige oben in der Mitte. */
public final class AuctionHud {
    private static final int W = 214;
    private static final int TOP = 6;
    private static final int H_RUN = 100;
    private static final int H_RES = 78;

    private static final int GOLD = 0xD4A017;
    private static final int GREEN = 0x55FF55;
    private static final int YELLOW = 0xFFAA00;
    private static final int RED = 0xFF5555;
    private static final int[] RANK = {0xFFD700, 0xC8C8D2, 0xCD7F32};
    private static final int[] SPARK = {0xFFD700, 0xFFFFFF, 0xFFAA00, 0x55FF55};

    private AuctionHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        AuctionManager m = HugoAuctionClient.MANAGER;
        long now = System.currentTimeMillis();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();

        int panelBottom;
        if (m.isRunning()) {
            panelBottom = drawRunning(ctx, tr, m, sw, now);
        } else if (m.hasRecentResult(now)) {
            panelBottom = drawResult(ctx, tr, m, sw, now);
        } else {
            return;
        }
        drawToasts(ctx, tr, m, sw, now, panelBottom + 5);
    }

    // ---------- laufende Auktion ----------

    private static int drawRunning(DrawContext ctx, TextRenderer tr, AuctionManager m, int sw, long now) {
        int x = (sw - W) / 2;
        double slide = Gfx.ease((now - m.startedAt()) / 450.0);
        int y = (int) (-H_RUN + (TOP + H_RUN) * slide);

        long remMs = m.remainingMs();
        double frac = m.totalMs() > 0 ? Gfx.clamp01(remMs / (double) m.totalMs()) : 0;
        long remS = m.remainingSeconds();

        double flash = m.lastBidAt() > 0 ? 1 - Gfx.clamp01((now - m.lastBidAt()) / 700.0) : 0;
        int border = Gfx.lerpColor(GOLD, 0xFFFFFF, flash);

        // roter Puls in den letzten 10 Sekunden
        if (remS <= 10) {
            double pl = 0.5 + 0.5 * Math.sin(now / 90.0);
            Gfx.outline(ctx, x - 3, y - 3, x + W + 3, y + H_RUN + 3, Gfx.argb((int) (50 + 90 * pl), RED));
            Gfx.outline(ctx, x - 2, y - 2, x + W + 2, y + H_RUN + 2, Gfx.argb((int) (80 + 120 * pl), RED));
        }

        // Rahmen und Hintergrund
        Gfx.rect(ctx, x - 1, y - 1, x + W + 1, y + H_RUN + 1, Gfx.argb(255, border));
        Gfx.rect(ctx, x, y, x + W, y + H_RUN, 0xF0101018);

        // Kopfzeile mit Farbverlauf
        for (int i = 0; i < 15; i++) {
            int c = Gfx.lerpColor(0x40300A, 0x1A1408, i / 14.0);
            int inset = i == 0 ? 1 : 0;
            ctx.fill(x + inset, y + i, x + W - inset, y + i + 1, Gfx.argb(255, c));
        }
        Gfx.shimmer(ctx, x, y, x + W, y + 15, now, 2600);
        Gfx.text(ctx, tr, "HUGO AUKTION", x + 7, y + 4, Gfx.argb(255, 0xFFD34D), true);

        int timerColor = Gfx.lerpColor(YELLOW, RED, 0);
        if (frac > 0.5) timerColor = Gfx.lerpColor(YELLOW, GREEN, (frac - 0.5) * 2);
        else timerColor = Gfx.lerpColor(RED, YELLOW, frac * 2);
        if (remS <= 10) {
            double pulse = 0.5 + 0.5 * Math.sin(now / 90.0);
            timerColor = Gfx.lerpColor(RED, 0xFFFFFF, pulse);
        }
        Gfx.right(ctx, tr, Fmt.time(remS), x + W - 7, y + 4, Gfx.argb(255, timerColor), true);

        // Item
        ItemStack stack = m.item();
        float bob = (float) (Math.sin(now / 380.0) * 1.0);
        Gfx.scaledItem(ctx, stack, x + 6, y + 18 + bob, 1.0f);
        String name = stack.getName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
        Gfx.text(ctx, tr, Gfx.trunc(tr, name, W - 36, true), x + 28, y + 18, 0xFFFFFFFF, true);
        String minText = m.minBid() > 0 ? "Min. " + Fmt.money(m.minBid()) : "Kein Mindestgebot";
        Gfx.text(ctx, tr, minText, x + 28, y + 29, 0xFFAAAAAA, false);
        if (m.buyout() > 0) {
            Gfx.right(ctx, tr, "Sofortkauf " + Fmt.money(m.buyout()), x + W - 6, y + 29, 0xFF55FFFF, false);
        }

        // Zeitbalken
        int bx1 = x + 6;
        int bx2 = x + W - 6;
        int by = y + 42;
        ctx.fill(bx1, by, bx2, by + 3, 0xFF2A2A35);
        int fill = (int) ((bx2 - bx1) * frac);
        if (fill > 0) ctx.fill(bx1, by, bx1 + fill, by + 3, Gfx.argb(255, timerColor));

        // Top 3
        List<AuctionManager.Bid> top = m.top(3);
        for (int i = 0; i < 3; i++) {
            int ry = y + 49 + i * 12;
            if (i == 0 && !top.isEmpty()) {
                int hl = 32 + (int) (flash * 90);
                Gfx.rect(ctx, x + 4, ry - 1, x + W - 4, ry + 10, Gfx.argb(hl, 0xFFD700));
            }
            Gfx.text(ctx, tr, (i + 1) + ".", x + 8, ry, Gfx.argb(255, RANK[i]), true);
            if (i < top.size()) {
                AuctionManager.Bid b = top.get(i);
                Gfx.text(ctx, tr, Gfx.trunc(tr, b.player(), W - 90, i == 0), x + 22, ry,
                        i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, i == 0);
                double shown = i == 0 ? m.shownTop(now) : b.amount();
                Gfx.right(ctx, tr, Fmt.money(shown), x + W - 8, ry,
                        i == 0 ? Gfx.argb(255, GREEN) : 0xFFBBBBBB, i == 0);
            } else if (i == 0) {
                Gfx.text(ctx, tr, "Noch keine Gebote", x + 22, ry, 0xFF888899, false);
            } else {
                Gfx.text(ctx, tr, "-", x + 22, ry, 0xFF555566, false);
            }
        }

        // Hinweis links, Gesamtbetrag rechts
        String pre = "Bieten per /pay ";
        String pay = AuctionManager.payName();
        float s = 0.78f;
        float fy = y + 88;
        Gfx.scaledText(ctx, tr, pre, x + 8, fy, s, 0xFF9999AA, false);
        Gfx.scaledText(ctx, tr, pay, x + 8 + Gfx.width(tr, pre, false) * s, fy, s, Gfx.argb(255, 0xFFD34D), true);
        String tot = "Gesamt " + Fmt.money(m.totalReceived());
        float tw = Gfx.width(tr, tot, true) * s;
        Gfx.scaledText(ctx, tr, tot, x + W - 8 - tw, fy, s, Gfx.argb(255, GREEN), true);

        // Funken und aufsteigender Betrag bei jeder Zahlung
        if (m.lastBidAt() > 0) {
            Gfx.burst(ctx, x + W - 50, y + 55, now - m.lastBidAt(), 22, SPARK, 38, 900);
        }
        List<AuctionManager.Pay> recent = m.history(1);
        if (!recent.isEmpty()) {
            AuctionManager.Pay p = recent.get(0);
            long age = now - p.at();
            if (age < 1600) {
                double t = age / 1600.0;
                int floatY = (int) (y + 50 - Gfx.ease(t) * 22);
                int col = p.valid() ? GREEN : RED;
                Gfx.text(ctx, tr, "+" + Fmt.money(p.paid()), x + W + 6, floatY, Gfx.argb((int) (255 * (1 - t)), col), true);
            }
        }

        return y + H_RUN;
    }

    // ---------- Ergebnis ----------

    private static int drawResult(DrawContext ctx, TextRenderer tr, AuctionManager m, int sw, long now) {
        long age = now - m.resultAt();
        int x = (sw - W) / 2;
        double in = Gfx.ease(age / 400.0);
        double out = Gfx.ease((age - (AuctionManager.RESULT_MS - 450)) / 450.0);
        int y = (int) (-H_RES + (TOP + H_RES) * in - (TOP + H_RES) * out);

        boolean winner = !m.resultPlayer().isEmpty();
        double pulse = 0.5 + 0.5 * Math.sin(age / 140.0);
        int border = winner ? Gfx.lerpColor(GOLD, 0xFFFFFF, pulse * 0.7) : 0x666677;

        Gfx.rect(ctx, x - 1, y - 1, x + W + 1, y + H_RES + 1, Gfx.argb(255, border));
        Gfx.rect(ctx, x, y, x + W, y + H_RES, 0xF0101018);
        for (int i = 0; i < 15; i++) {
            int c = Gfx.lerpColor(winner ? 0x40300A : 0x2A2A30, winner ? 0x1A1408 : 0x18181C, i / 14.0);
            int inset = i == 0 ? 1 : 0;
            ctx.fill(x + inset, y + i, x + W - inset, y + i + 1, Gfx.argb(255, c));
        }
        String head = !winner ? "AUKTION BEENDET" : (m.resultWasBuyout() ? "SOFORTKAUF!" : "GEWONNEN!");
        int headColor = winner ? Gfx.lerpColor(0xFFD34D, 0xFFFFFF, pulse) : 0xAAAAAA;
        Gfx.centered(ctx, tr, head, x + W / 2, y + 4, Gfx.argb(255, headColor), true);

        if (winner) {
            Gfx.scaledItem(ctx, m.resultItem(), x + 10, y + 26, 1.75f);
            Gfx.text(ctx, tr, "Gewinner", x + 46, y + 20, 0xFF9999AA, false);
            String nm = Gfx.trunc(tr, m.resultPlayer(), (int) ((W - 54) / 1.5f), true);
            Gfx.scaledText(ctx, tr, nm, x + 46, y + 31, 1.5f, 0xFFFFFFFF, true);
            Gfx.scaledText(ctx, tr, Fmt.money(m.resultAmount()), x + 46, y + 48, 1.3f, Gfx.argb(255, GREEN), true);
            String item = m.resultItem().getName().getString();
            String tot = "Gesamt " + Fmt.money(m.totalReceived());
            Gfx.right(ctx, tr, tot, x + W - 8, y + 65, Gfx.argb(255, GREEN), true);
            Gfx.text(ctx, tr, Gfx.trunc(tr, item, W - 24 - Gfx.width(tr, tot, true), false), x + 8, y + 65, 0xFF888899, false);
            drawSparkles(ctx, x + W / 2, y + H_RES / 2, age);
        } else {
            Gfx.centered(ctx, tr, "Keine Gebote", x + W / 2, y + 34, 0xFFAAAAAA, false);
            Gfx.centered(ctx, tr, m.resultItem().getName().getString(), x + W / 2, y + 50, 0xFF777788, false);
            if (m.totalReceived() > 0) {
                Gfx.centered(ctx, tr, "Gesamt " + Fmt.money(m.totalReceived()), x + W / 2, y + 63, Gfx.argb(255, GREEN), true);
            }
        }
        return y + H_RES;
    }

    private static void drawSparkles(DrawContext ctx, int cx, int cy, long age) {
        double t = age / 1800.0;
        if (t >= 1) return;
        int[] palette = {0xFFD700, 0xFFFFFF, 0xFFAA00, 0x55FF55};
        double e = Gfx.ease(t);
        for (int i = 0; i < 32; i++) {
            double ang = Math.toRadians((i * 137.5) % 360);
            double dist = (28 + (i % 6) * 20) * e;
            int px = cx + (int) (Math.cos(ang) * dist * 1.7);
            int py = cy + (int) (Math.sin(ang) * dist) + (int) (e * e * 14);
            int a = (int) (255 * (1 - t));
            int size = i % 3 == 0 ? 2 : 1;
            ctx.fill(px, py, px + size, py + size, Gfx.argb(a, palette[i % palette.length]));
        }
    }

    // ---------- Gebote als kleine Meldungen ----------

    private static void drawToasts(DrawContext ctx, TextRenderer tr, AuctionManager m, int sw, long now, int baseY) {
        List<AuctionManager.Event> list = m.recentEvents(now, 3);
        for (int i = 0; i < list.size(); i++) {
            AuctionManager.Event ev = list.get(i);
            long age = now - ev.at();
            String line = ev.title() + "  " + ev.detail();
            int w = Math.max(120, Gfx.width(tr, line, true) + 18);
            double in = Gfx.ease(age / 300.0);
            int dx = (int) ((1 - in) * 70);
            long left = AuctionManager.EVENT_MS - age;
            int alpha = left < 600 ? (int) (255 * Math.max(0, left) / 600.0) : 255;
            if (alpha < 8) continue;
            int x = (sw - w) / 2 + dx;
            int y = baseY + i * 21;
            Gfx.rect(ctx, x, y, x + w, y + 18, Gfx.argb((int) (alpha * 0.85), 0x14141C));
            ctx.fill(x, y + 1, x + 2, y + 17, Gfx.argb(alpha, ev.color()));
            Gfx.text(ctx, tr, ev.title(), x + 7, y + 5, Gfx.argb(alpha, ev.color()), true);
            Gfx.right(ctx, tr, ev.detail(), x + w - 6, y + 5, Gfx.argb(alpha, 0xFFFFFF), false);
        }
    }
}
