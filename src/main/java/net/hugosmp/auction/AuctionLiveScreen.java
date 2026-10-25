package net.hugosmp.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live-Ansicht der laufenden Auktion (und des Ergebnisses danach). */
public class AuctionLiveScreen extends Screen {
    private static final int PW = 290;
    private static final int PH = 238;
    private static final int CARD = 0x16FFFFFF;
    private static final int GOLD = 0xD4A017;
    private static final int GREEN = 0x55FF55;
    private static final int RED = 0xFF5555;
    private static final int AQUA = 0x55FFFF;
    private static final int[] RANK = {0xFFD700, 0xC8C8D2, 0xCD7F32, 0x7A8AA0, 0x7A8AA0};
    private static final int[] GOOD = {0xFFD700, 0xFFFFFF, 0xFFAA00, 0x55FF55};
    private static final int[] BAD = {0xFF5555, 0xAA0000, 0xFFAAAA};
    private static final int[] CONFETTI = {0xFFD700, 0xFF5555, 0x55FF55, 0x55FFFF, 0xFF55FF, 0xFFFFFF};

    private final Map<String, Double> bars = new HashMap<>();
    private final Map<String, Long> seen = new HashMap<>();
    private ButtonWidget cancelButton;
    private ButtonWidget newButton;
    private ButtonWidget closeButton;
    private ButtonWidget copyButton;
    private long copiedAt = -1_000_000L;
    private long openedAt;
    private long lastFrame;
    private int lastPayCount = -1;
    private long burstAt = -1_000_000L;
    private boolean burstGood = true;

    public AuctionLiveScreen() {
        super(Text.literal("Live-Auktion"));
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        int left = (width - PW) / 2;
        int top = (height - PH) / 2;

        cancelButton = ButtonWidget.builder(Text.literal("Auktion abbrechen").formatted(Formatting.RED), b -> {
            HugoAuctionClient.MANAGER.cancel();
            close();
        }).dimensions(left + 12, top + 210, 126, 20).build();
        addDrawableChild(cancelButton);

        newButton = ButtonWidget.builder(Text.literal("Neue Auktion").formatted(Formatting.GREEN, Formatting.BOLD),
                b -> MinecraftClient.getInstance().setScreen(new AuctionScreen()))
                .dimensions(left + 12, top + 210, 126, 20).build();
        addDrawableChild(newButton);

        copyButton = ButtonWidget.builder(Text.literal("Namen kopieren").formatted(Formatting.AQUA), b -> {
            String n = HugoAuctionClient.MANAGER.resultPlayer();
            if (n.isEmpty()) return;
            MinecraftClient.getInstance().keyboard.setClipboard(n);
            copiedAt = System.currentTimeMillis();
            b.setMessage(Text.literal("Kopiert: " + n).formatted(Formatting.GREEN));
        }).dimensions(left + 75, top + 184, 140, 18).build();
        addDrawableChild(copyButton);

        closeButton = ButtonWidget.builder(Text.literal("Schließen"), b -> close())
                .dimensions(left + 152, top + 210, 126, 20).build();
        addDrawableChild(closeButton);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        AuctionManager m = HugoAuctionClient.MANAGER;
        boolean running = m.isRunning();
        cancelButton.visible = running;
        newButton.visible = !running;
        copyButton.visible = !running && m.hasResult() && !m.resultPlayer().isEmpty();

        double dt = lastFrame == 0 ? 0.016 : Math.min(100, now - lastFrame) / 1000.0;
        lastFrame = now;

        int pc = m.paymentCount();
        if (lastPayCount >= 0 && pc > lastPayCount) {
            List<AuctionManager.Pay> h = m.history(1);
            burstAt = now;
            burstGood = h.isEmpty() || h.get(0).valid();
        }
        lastPayCount = pc;

        if (running) drawLive(ctx, m, now, dt);
        else drawResult(ctx, m, now);
    }

    // ---------- Rahmen ----------

    private void frame(DrawContext ctx, int left, int top, long now, int borderRgb, boolean goldHeader) {
        double open = Gfx.ease((now - openedAt) / 380.0);
        int cx = left + PW / 2;
        Gfx.glow(ctx, cx, top + PH / 2, PW / 2 + 16, borderRgb, (int) (70 * open));
        Gfx.rect(ctx, left - 1, top - 1, left + PW + 1, top + PH + 1, Gfx.argb(255, borderRgb));
        Gfx.gradientV(ctx, left, top, left + PW, top + PH, 0x16162A, 0x0C0C14, 250);
        for (int i = 0; i < 22; i++) {
            int c = Gfx.lerpColor(goldHeader ? 0x4A360C : 0x2A2A30, goldHeader ? 0x1A1408 : 0x18181C, i / 21.0);
            int inset = i == 0 ? 1 : 0;
            ctx.fill(left + inset, top + i, left + PW - inset, top + i + 1, Gfx.argb(255, c));
        }
        Gfx.shimmer(ctx, left, top, left + PW, top + 22, now, 3000);
    }

    // ---------- laufende Auktion ----------

    private void drawLive(DrawContext ctx, AuctionManager m, long now, double dt) {
        int left = (width - PW) / 2;
        int top = (height - PH) / 2;
        long remS = m.remainingSeconds();
        double frac = m.totalMs() > 0 ? Gfx.clamp01(m.remainingMs() / (double) m.totalMs()) : 0;
        int timerColor = Gfx.barColor(frac);
        double pulse = 0.5 + 0.5 * Math.sin(now / 520.0);
        int border = Gfx.lerpColor(GOLD, 0xFFE27A, pulse);
        if (remS <= 10) border = Gfx.lerpColor(RED, 0xFFFFFF, 0.5 + 0.5 * Math.sin(now / 90.0));

        frame(ctx, left, top, now, border, true);
        Gfx.centered(ctx, textRenderer, "LIVE-AUKTION", left + PW / 2, top + 7, 0xFFFFD34D, true);

        // Status
        int dotA = (int) (140 + 115 * Math.sin(now / 260.0));
        ctx.fill(left + PW - 58, top + 8, left + PW - 52, top + 14, Gfx.argb(dotA, GREEN));
        Gfx.text(ctx, textRenderer, "LÄUFT", left + PW - 48, top + 7, Gfx.argb(255, GREEN), true);

        double open = Gfx.ease((now - openedAt) / 380.0);

        // ----- linke Karte: Item und Timer -----
        int cardL = left + 10;
        int cardR = left + 112;
        Gfx.rect(ctx, cardL, top + 28, cardR, top + 146, CARD);
        int icx = (cardL + cardR) / 2;
        int icy = top + 60;
        Gfx.glow(ctx, icx, icy, 34, 0xFFD34D, 100);
        ItemStack stack = m.item();
        float size = 48f * (float) open;
        float bob = (float) (Math.sin(now / 450.0) * 2.0);
        Gfx.scaledItem(ctx, stack, icx - size / 2f, icy - size / 2f + bob, 3f * (float) open);

        String name = stack.getName().getString();
        Gfx.centered(ctx, textRenderer, Gfx.trunc(textRenderer, name, 96, true), icx, top + 90, 0xFFFFFFFF, true);
        Gfx.centered(ctx, textRenderer, "Menge: " + stack.getCount(), icx, top + 101, 0xFF9999AA, false);

        String ts = Fmt.time(remS);
        int tcol = remS <= 10 ? Gfx.lerpColor(RED, 0xFFFFFF, 0.5 + 0.5 * Math.sin(now / 90.0)) : timerColor;
        float tscale = 2.0f + (remS <= 10 ? (float) (0.12 * Math.sin(now / 90.0)) : 0f);
        float tw = Gfx.width(textRenderer, ts, true) * tscale;
        Gfx.scaledText(ctx, textRenderer, ts, icx - tw / 2f, top + 113, tscale, Gfx.argb(255, tcol), true);
        Gfx.centered(ctx, textRenderer, "verbleibend", icx, top + 131, 0xFF777788, false);
        Gfx.bar(ctx, cardL + 6, top + 141, cardR - 6, 3, frac, timerColor);

        // ----- rechte Seite: Top-Bieter -----
        int x0 = left + 120;
        int x1 = left + PW - 10;
        Gfx.text(ctx, textRenderer, "TOP BIETER", x0, top + 30, 0xFFFFD34D, true);
        String pay = "/pay " + AuctionManager.payName();
        Gfx.right(ctx, textRenderer, pay, x1, top + 31, 0xFF8888A0, false);

        List<AuctionManager.Bid> top5 = m.top(5);
        double best = top5.isEmpty() ? 1 : top5.get(0).amount();
        for (int i = 0; i < 5; i++) {
            int ry = top + 42 + i * 19;
            if (i < top5.size()) {
                AuctionManager.Bid b = top5.get(i);
                seen.putIfAbsent(b.player(), now);
                double cur = bars.getOrDefault(b.player(), 0.0);
                cur += (b.amount() - cur) * (1 - Math.exp(-dt * 8));
                bars.put(b.player(), cur);

                double slide = Gfx.ease((now - seen.get(b.player())) / 420.0);
                int dx = (int) ((1 - slide) * 34);
                int a = (int) (255 * slide);
                int rx0 = x0 + dx;
                int rx1 = x1 + dx;
                Gfx.rect(ctx, rx0, ry, rx1, ry + 17, Gfx.argb(i == 0 ? 52 : 24, i == 0 ? 0xFFD700 : 0xFFFFFF));
                int bw = (int) ((rx1 - rx0 - 2) * Gfx.clamp01(cur / best));
                if (bw > 0) ctx.fill(rx0 + 1, ry + 15, rx0 + 1 + bw, ry + 16, Gfx.argb((int) (200 * slide), RANK[i]));
                ctx.fill(rx0 + 3, ry + 2, rx0 + 15, ry + 14, Gfx.argb(a, RANK[i]));
                Gfx.centered(ctx, textRenderer, String.valueOf(i + 1), rx0 + 9, ry + 4, Gfx.argb(a, 0x101018), true);
                int nx = rx0 + 19;
                if (i == 0) {
                    Gfx.crown(ctx, nx, ry + 5, Gfx.argb(a, 0xFFD700));
                    nx += 12;
                }
                Gfx.text(ctx, textRenderer, Gfx.trunc(textRenderer, b.player(), 62, i == 0), nx, ry + 4,
                        Gfx.argb(a, i == 0 ? 0xFFFFFF : 0xDDDDDD), i == 0);
                double shown = i == 0 ? m.shownTop(now) : b.amount();
                Gfx.right(ctx, textRenderer, Fmt.money(shown), rx1 - 5, ry + 4,
                        Gfx.argb(a, i == 0 ? GREEN : 0xBBBBBB), i == 0);
            } else {
                Gfx.rect(ctx, x0, ry, x1, ry + 17, Gfx.argb(10, 0xFFFFFF));
            }
        }
        if (top5.isEmpty()) {
            int dots = (int) ((now / 400) % 4);
            String wait = "Warte auf Gebote" + ".".repeat(dots);
            Gfx.centered(ctx, textRenderer, wait, (x0 + x1) / 2, top + 76, 0xFF9999AA, false);
            String mn = m.minBid() > 0 ? "ab " + Fmt.money(m.minBid()) : "kein Mindestgebot";
            Gfx.centered(ctx, textRenderer, mn, (x0 + x1) / 2, top + 88, 0xFF666678, false);
        }

        // ----- Sofortkauf-Karte -----
        Gfx.rect(ctx, left + 10, top + 150, left + PW - 10, top + 172, CARD);
        double topAmt = top5.isEmpty() ? 0 : top5.get(0).amount();
        if (m.buyout() > 0) {
            double bf = Gfx.clamp01(topAmt / m.buyout());
            Gfx.text(ctx, textRenderer, "SOFORTKAUF", left + 16, top + 153, 0xFF55FFFF, true);
            Gfx.text(ctx, textRenderer, Fmt.money(m.buyout()), left + 82, top + 153, 0xFFFFFFFF, true);
            int pcol = bf > 0.8 ? Gfx.lerpColor(AQUA, 0xFFD700, 0.5 + 0.5 * Math.sin(now / 150.0)) : AQUA;
            Gfx.bar(ctx, left + 16, top + 164, left + PW - 16, 4, bf, pcol);
            String pct = (int) (bf * 100) + "%";
            Gfx.right(ctx, textRenderer, pct, left + PW - 16, top + 153, Gfx.argb(255, pcol), true);
        } else {
            Gfx.text(ctx, textRenderer, "Kein Sofortkauf", left + 16, top + 153, 0xFF777788, false);
        }
        String minLine = m.minBid() > 0 ? "Min. " + Fmt.money(m.minBid()) : "";
        if (m.buyout() > 0 && !minLine.isEmpty()) {
            Gfx.centered(ctx, textRenderer, minLine, left + PW / 2 + 22, top + 153, 0xFFAAAAAA, false);
        } else if (!minLine.isEmpty()) {
            Gfx.right(ctx, textRenderer, minLine, left + PW - 16, top + 153, 0xFFAAAAAA, false);
        }

        // ----- letzte Zahlungen -----
        Gfx.scaledText(ctx, textRenderer, "LETZTE ZAHLUNGEN", left + 12, top + 177, 0.8f, 0xFF8888A0, true);
        String totLabel = "Gesamt erhalten: ";
        String totValue = Fmt.money(m.totalReceived());
        float tvw = Gfx.width(textRenderer, totValue, true) * 0.9f;
        float tlw = Gfx.width(textRenderer, totLabel, false) * 0.8f;
        float tx = left + PW - 12 - tvw;
        Gfx.scaledText(ctx, textRenderer, totLabel, tx - tlw, top + 177, 0.8f, 0xFF8888A0, false);
        Gfx.scaledText(ctx, textRenderer, totValue, tx, top + 176.5f, 0.9f, Gfx.argb(255, GREEN), true);
        List<AuctionManager.Pay> feed = m.history(2);
        if (feed.isEmpty()) {
            Gfx.text(ctx, textRenderer, "Noch keine Zahlungen", left + 12, top + 190, 0xFF555566, false);
        }
        for (int i = 0; i < feed.size(); i++) {
            AuctionManager.Pay p = feed.get(i);
            int fy = top + 187 + i * 11;
            long age = now - p.at();
            if (age < 700) {
                int fa = (int) (70 * (1 - age / 700.0));
                Gfx.rect(ctx, left + 10, fy - 1, left + PW - 10, fy + 9, Gfx.argb(fa, p.valid() ? GREEN : RED));
            }
            Gfx.text(ctx, textRenderer, Gfx.trunc(textRenderer, p.player(), 90, true), left + 14, fy,
                    p.valid() ? 0xFFFFFFFF : 0xFFFF9999, true);
            String paid = "+" + Fmt.money(p.paid()) + (p.valid() ? "" : "  zu niedrig");
            Gfx.text(ctx, textRenderer, paid, left + 110, fy, Gfx.argb(255, p.valid() ? GREEN : RED), false);
            Gfx.right(ctx, textRenderer, "vor " + (age / 1000) + "s", left + PW - 14, fy, 0xFF777788, false);
        }

        Gfx.burst(ctx, (x0 + x1) / 2, top + 60, now - burstAt, 30, burstGood ? GOOD : BAD, 70, 1000);
    }

    // ---------- Ergebnis ----------

    private void drawResult(DrawContext ctx, AuctionManager m, long now) {
        int left = (width - PW) / 2;
        int top = (height - PH) / 2;
        int cx = left + PW / 2;
        boolean winner = m.hasResult() && !m.resultPlayer().isEmpty();
        long age = now - m.resultAt();
        double pulse = 0.5 + 0.5 * Math.sin(now / 260.0);
        int border = winner ? Gfx.lerpColor(GOLD, 0xFFFFFF, pulse * 0.7) : 0x666677;

        frame(ctx, left, top, now, border, winner);
        String head = !m.hasResult() ? "KEINE AUKTION" : (!winner ? "AUKTION BEENDET" : (m.resultWasBuyout() ? "SOFORTKAUF!" : "GEWONNEN!"));
        int headColor = winner ? Gfx.lerpColor(0xFFD34D, 0xFFFFFF, pulse) : 0xAAAAAA;
        Gfx.centered(ctx, textRenderer, head, cx, top + 7, Gfx.argb(255, headColor), true);

        if (!m.hasResult()) {
            Gfx.centered(ctx, textRenderer, "Es läuft gerade keine Auktion.", cx, top + 100, 0xFFAAAAAA, false);
            return;
        }

        double open = Gfx.ease((now - openedAt) / 450.0);
        int icy = top + 70;
        if (winner) {
            Gfx.glow(ctx, cx, icy, 48, 0xFFD34D, 120);
            for (int i = 0; i < 10; i++) {
                double ang = now / 900.0 + i * (Math.PI * 2 / 10);
                int px = cx + (int) (Math.cos(ang) * 46);
                int py = icy + (int) (Math.sin(ang) * 30);
                ctx.fill(px, py, px + 2, py + 2, Gfx.argb(200, i % 2 == 0 ? 0xFFD700 : 0xFFFFFF));
            }
        }
        float size = 64f * (float) open;
        float bob = (float) (Math.sin(now / 450.0) * 2.5);
        Gfx.scaledItem(ctx, m.resultItem(), cx - size / 2f, icy - size / 2f + bob, 4f * (float) open);

        if (winner) {
            Gfx.centered(ctx, textRenderer, "GEWINNER", cx, top + 106, 0xFF9999AA, false);
            String nm = Gfx.trunc(textRenderer, m.resultPlayer(), 120, true);
            float nw = Gfx.width(textRenderer, nm, true) * 2f;
            Gfx.scaledText(ctx, textRenderer, nm, cx - nw / 2f, top + 117, 2f, 0xFFFFFFFF, true);
            String amt = Fmt.money(m.resultAmount());
            float aw = Gfx.width(textRenderer, amt, true) * 1.6f;
            Gfx.scaledText(ctx, textRenderer, amt, cx - aw / 2f, top + 139, 1.6f, Gfx.argb(255, GREEN), true);
            String it = m.resultItem().getName().getString() + (m.resultWasBuyout() ? "  |  Sofortkauf" : "");
            Gfx.centered(ctx, textRenderer, Gfx.trunc(textRenderer, it, PW - 30, false), cx, top + 156, 0xFF888899, false);
            String zahl = m.paymentCount() + " Zahlung" + (m.paymentCount() == 1 ? "" : "en");
            Gfx.centered(ctx, textRenderer, "Insgesamt erhalten: " + Fmt.money(m.totalReceived()) + "  (" + zahl + ")",
                    cx, top + 169, Gfx.argb(255, GOLD + 0x2A2A00), true);
            Gfx.burst(ctx, cx, top + 193, now - copiedAt, 22, GOOD, 60, 900);
            confetti(ctx, age);
        } else {
            Gfx.centered(ctx, textRenderer, "Keine Gebote", cx, top + 118, 0xFFAAAAAA, true);
            Gfx.centered(ctx, textRenderer, Gfx.trunc(textRenderer, m.resultItem().getName().getString(), PW - 30, false),
                    cx, top + 134, 0xFF777788, false);
            if (m.totalReceived() > 0) {
                Gfx.centered(ctx, textRenderer, "Insgesamt erhalten: " + Fmt.money(m.totalReceived()),
                        cx, top + 150, Gfx.argb(255, GREEN), true);
            }
        }
    }

    private void confetti(DrawContext ctx, long age) {
        if (age < 0 || age > 6500) return;
        double fade = age > 5500 ? 1 - (age - 5500) / 1000.0 : 1;
        for (int i = 0; i < 90; i++) {
            double sp = 0.05 + ((i * 13) % 7) * 0.012;
            double px = ((i * 83) % Math.max(1, width)) + Math.sin(age / 400.0 + i) * 8;
            double py = age * sp - ((i * 29) % Math.max(1, height));
            if (py < -4 || py > height) continue;
            ctx.fill((int) px, (int) py, (int) px + 3, (int) py + 2, Gfx.argb((int) (230 * fade), CONFETTI[i % CONFETTI.length]));
        }
    }
}
