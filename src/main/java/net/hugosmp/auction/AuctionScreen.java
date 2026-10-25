package net.hugosmp.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menü zum Erstellen einer Auktion. */
public class AuctionScreen extends Screen {
    private static final int PW = 244;
    private static final int PH = 240;
    private static final int[] PRESET_SECONDS = {30, 60, 180, 300, 600};
    private static final String[] PRESET_LABELS = {"30 s", "1 Min", "3 Min", "5 Min", "10 Min"};
    private static final int CARD = 0x16FFFFFF;

    private TextFieldWidget secondsField;
    private TextFieldWidget minField;
    private TextFieldWidget buyField;
    private TextFieldWidget payField;
    private ButtonWidget startButton;
    private ItemStack stack = ItemStack.EMPTY;
    private String error = "";
    private long openedAt;
    private long errorAt;

    public AuctionScreen() {
        super(Text.literal("Auktion erstellen"));
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        stack = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack().copy();
        AuctionConfig cfg = HugoAuctionClient.CONFIG;

        String sec = secondsField != null ? secondsField.getText() : String.valueOf(cfg.lastSeconds);
        String min = minField != null ? minField.getText() : cfg.lastMinBid;
        String buy = buyField != null ? buyField.getText() : cfg.lastBuyout;
        String pay = payField != null ? payField.getText() : cfg.payName;

        int left = (width - PW) / 2;
        int top = (height - PH) / 2;

        secondsField = new TextFieldWidget(textRenderer, left + 14, top + 69, 70, 18, Text.literal("Sekunden"));
        secondsField.setMaxLength(6);
        secondsField.setTextPredicate(s -> s.matches("\\d*"));
        secondsField.setText(sec);
        addDrawableChild(secondsField);

        for (int i = 0; i < PRESET_SECONDS.length; i++) {
            final int secs = PRESET_SECONDS[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(PRESET_LABELS[i]), b -> secondsField.setText(String.valueOf(secs)))
                    .dimensions(left + 14 + i * 44, top + 91, 40, 16).build());
        }

        minField = new TextFieldWidget(textRenderer, left + 14, top + 130, 102, 18, Text.literal("Mindestgebot"));
        minField.setMaxLength(16);
        minField.setTextPredicate(s -> s.matches("[0-9.,kKmMbBtT$ ]*"));
        minField.setText(min);
        addDrawableChild(minField);

        buyField = new TextFieldWidget(textRenderer, left + 128, top + 130, 102, 18, Text.literal("Sofortkauf"));
        buyField.setMaxLength(16);
        buyField.setTextPredicate(s -> s.matches("[0-9.,kKmMbBtT$ ]*"));
        buyField.setText(buy);
        addDrawableChild(buyField);

        payField = new TextFieldWidget(textRenderer, left + 14, top + 172, 216, 18, Text.literal("Name"));
        payField.setMaxLength(16);
        payField.setTextPredicate(s -> s.matches("[A-Za-z0-9_]*"));
        payField.setText(pay);
        addDrawableChild(payField);

        boolean running = HugoAuctionClient.MANAGER.isRunning();
        startButton = ButtonWidget.builder(
                        Text.literal("Auktion starten").formatted(Formatting.GREEN, Formatting.BOLD),
                        b -> onStart())
                .dimensions(left + 12, top + 214, 108, 20).build();
        startButton.active = !running;
        addDrawableChild(startButton);

        Text secondLabel = running
                ? Text.literal("Auktion abbrechen").formatted(Formatting.RED)
                : Text.literal("Schließen");
        addDrawableChild(ButtonWidget.builder(secondLabel, b -> {
            if (HugoAuctionClient.MANAGER.isRunning()) HugoAuctionClient.MANAGER.cancel();
            close();
        }).dimensions(left + 124, top + 214, 108, 20).build());
    }

    private void fail(String msg) {
        error = msg;
        errorAt = System.currentTimeMillis();
    }

    private void onStart() {
        error = "";
        AuctionConfig cfg = HugoAuctionClient.CONFIG;

        if (stack.isEmpty()) {
            fail("Halte ein Item in der Hand!");
            return;
        }
        int secs;
        try {
            secs = Integer.parseInt(secondsField.getText().trim());
        } catch (NumberFormatException e) {
            fail("Bitte Sekunden eingeben");
            return;
        }
        if (secs < 5 || secs > 86400) {
            fail("Dauer: 5 bis 86400 Sekunden");
            return;
        }
        String minText = minField.getText().trim();
        String buyText = buyField.getText().trim();
        double min = 0;
        double buy = 0;
        if (!minText.isEmpty()) {
            min = Fmt.parse(minText);
            if (Double.isNaN(min)) {
                fail("Mindestgebot ungültig");
                return;
            }
        }
        if (!buyText.isEmpty()) {
            buy = Fmt.parse(buyText);
            if (Double.isNaN(buy)) {
                fail("Sofortkauf ungültig");
                return;
            }
        }
        if (buy > 0 && buy < min) {
            fail("Sofortkauf muss mindestens so hoch sein wie das Mindestgebot");
            return;
        }
        String pay = payField.getText().trim();
        if (!pay.isEmpty()) cfg.payName = pay;
        cfg.lastSeconds = secs;
        cfg.lastMinBid = minText;
        cfg.lastBuyout = buyText;
        cfg.save();

        if (HugoAuctionClient.MANAGER.start(stack, secs, min, buy)) close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        int left = (width - PW) / 2;
        int top = (height - PH) / 2;
        int cx = left + PW / 2;
        double open = Gfx.ease((now - openedAt) / 380.0);

        // Rahmen mit sanftem Puls
        double pulse = 0.5 + 0.5 * Math.sin(now / 520.0);
        int border = Gfx.lerpColor(0xD4A017, 0xFFE27A, pulse);
        Gfx.glow(ctx, cx, top + PH / 2, PW / 2 + 14, 0xD4A017, (int) (70 * open));
        Gfx.rect(ctx, left - 1, top - 1, left + PW + 1, top + PH + 1, Gfx.argb(255, border));
        Gfx.gradientV(ctx, left, top, left + PW, top + PH, 0x16162A, 0x0C0C14, 250);
        ctx.fill(left, top, left + PW, top + 1, Gfx.argb(255, border));

        // Kopfzeile
        for (int i = 0; i < 22; i++) {
            int c = Gfx.lerpColor(0x4A360C, 0x1A1408, i / 21.0);
            int inset = i == 0 ? 1 : 0;
            ctx.fill(left + inset, top + i, left + PW - inset, top + i + 1, Gfx.argb(255, c));
        }
        Gfx.shimmer(ctx, left, top, left + PW, top + 22, now, 3000);
        Gfx.centered(ctx, textRenderer, "AUKTION ERSTELLEN", cx, top + 7, 0xFFFFD34D, true);
        ctx.fill(left + 40, top + 21, left + PW - 40, top + 22, Gfx.argb((int) (120 + 100 * pulse), 0xFFD34D));

        // schwebender Goldstaub
        for (int i = 0; i < 16; i++) {
            int dx = left + 6 + (i * 61) % (PW - 12);
            int dy = top + PH - (int) (((now / (26.0 + i * 3)) + i * 47) % PH);
            if (dy < top + 24) continue;
            int a = 28 + (int) (24 * Math.sin(now / 300.0 + i));
            ctx.fill(dx, dy, dx + (i % 4 == 0 ? 2 : 1), dy + (i % 4 == 0 ? 2 : 1), Gfx.argb(a, 0xFFD34D));
        }

        // Karten
        Gfx.rect(ctx, left + 8, top + 26, left + PW - 8, top + 50, CARD);
        Gfx.rect(ctx, left + 8, top + 54, left + PW - 8, top + 112, CARD);
        Gfx.rect(ctx, left + 8, top + 116, left + PW - 8, top + 154, CARD);
        Gfx.rect(ctx, left + 8, top + 158, left + PW - 8, top + 196, CARD);

        // Item
        if (stack.isEmpty()) {
            double blink = 0.6 + 0.4 * Math.sin(now / 200.0);
            Gfx.text(ctx, textRenderer, "Halte das Item in der Hand, das du versteigern willst.",
                    left + 14, top + 34, Gfx.argb((int) (255 * blink), 0xFF5555), false);
        } else {
            Gfx.glow(ctx, left + 25, top + 38, 15, 0xFFD34D, 90);
            float bob = (float) (Math.sin(now / 500.0) * 1.2);
            Gfx.scaledItem(ctx, stack, left + 15, top + 28 + bob, 1.25f);
            String name = stack.getName().getString();
            Gfx.text(ctx, textRenderer, Gfx.trunc(textRenderer, name, PW - 60, true), left + 44, top + 30, 0xFFFFFFFF, true);
            Gfx.text(ctx, textRenderer, "Menge: " + stack.getCount(), left + 44, top + 40, 0xFF9999AA, false);
        }

        // Dauer
        Gfx.text(ctx, textRenderer, "DAUER IN SEKUNDEN", left + 14, top + 58, 0xFFFFD34D, true);
        int secs = parseSeconds();
        if (secs > 0) {
            Gfx.text(ctx, textRenderer, "= " + Fmt.time(secs) + " Min", left + 94, top + 74, 0xFF55FF55, true);
        }
        for (int i = 0; i < PRESET_SECONDS.length; i++) {
            if (PRESET_SECONDS[i] == secs) {
                int bx = left + 14 + i * 44;
                ctx.fill(bx, top + 108, bx + 40, top + 110, Gfx.argb(255, 0xFFD34D));
            }
        }

        // Preise
        Gfx.text(ctx, textRenderer, "MINDESTGEBOT", left + 14, top + 120, 0xFFFFD34D, true);
        Gfx.text(ctx, textRenderer, "SOFORTKAUF", left + 128, top + 120, 0xFF55FFFF, true);

        // Name
        Gfx.text(ctx, textRenderer, "ZAHLUNGEN GEHEN AN", left + 14, top + 162, 0xFFFFD34D, true);

        // Vorschau oder Fehler
        if (!error.isEmpty()) {
            double shake = Math.max(0, 1 - (now - errorAt) / 400.0);
            int sx = (int) (Math.sin((now - errorAt) / 25.0) * 4 * shake);
            Gfx.centered(ctx, textRenderer, error, cx + sx, top + 201, 0xFFFF5555, true);
        } else {
            double min = Fmt.parse(minField == null ? "" : minField.getText());
            double buy = Fmt.parse(buyField == null ? "" : buyField.getText());
            String pm = Double.isNaN(min) || min <= 0 ? "kein Mindestgebot" : "Min. " + Fmt.money(min);
            String pb = Double.isNaN(buy) || buy <= 0 ? "kein Sofortkauf" : "Sofortkauf " + Fmt.money(buy);
            Gfx.right(ctx, textRenderer, pm, cx - 8, top + 201, 0xFFDDDDDD, false);
            Gfx.text(ctx, textRenderer, "|", cx - 2, top + 201, 0xFF555566, false);
            Gfx.text(ctx, textRenderer, pb, cx + 8, top + 201, 0xFF55FFFF, false);
        }
    }

    private int parseSeconds() {
        if (secondsField == null) return 0;
        try {
            return Integer.parseInt(secondsField.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
