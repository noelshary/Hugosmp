package net.hugosmp.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class AuctionManager {
    public static final long RESULT_MS = 10_000L;
    public static final long EVENT_MS = 4_500L;

    public record Bid(String player, double amount) {}

    public record Event(String title, String detail, int color, long at) {}

    /** Eine eingegangene Zahlung (gültig = Gesamtbetrag erreicht das Mindestgebot). */
    public record Pay(String player, double paid, double total, boolean valid, long at) {}

    private boolean running;
    private long startMs;
    private long endMs;
    private long totalMs;
    private ItemStack item = ItemStack.EMPTY;
    private double minBid;
    private double buyout;
    private final Map<String, Double> totals = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Pay> history = new ArrayList<>();
    private int payCount;
    private double totalReceived;

    private double prevTop;
    private double curTop;
    private long topChangedMs;
    private long lastBidMs;

    private boolean hasResult;
    private boolean resultBuyout;
    private long resultMs;
    private String resultPlayer = "";
    private double resultAmount;
    private ItemStack resultItem = ItemStack.EMPTY;

    // ---------- Zustand für HUD und Menü ----------

    public boolean isRunning() { return running; }
    public long startedAt() { return startMs; }
    public long totalMs() { return totalMs; }
    public long remainingMs() { return Math.max(0, endMs - System.currentTimeMillis()); }
    public long remainingSeconds() { return (remainingMs() + 999) / 1000; }
    public ItemStack item() { return item; }
    public double minBid() { return minBid; }
    public double buyout() { return buyout; }
    public long lastBidAt() { return lastBidMs; }

    public boolean hasResult() { return hasResult; }
    public int paymentCount() { return payCount; }
    /** Summe aller Zahlungen dieser Auktion (auch zu niedrige). */
    public double totalReceived() { return totalReceived; }
    public int bidderCount() { return top(Integer.MAX_VALUE).size(); }

    /** Neueste Zahlungen zuerst. */
    public List<Pay> history(int n) {
        List<Pay> out = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && out.size() < n; i--) out.add(history.get(i));
        return out;
    }

    public boolean hasRecentResult(long now) { return hasResult && now - resultMs < RESULT_MS; }
    public long resultAt() { return resultMs; }
    public boolean resultWasBuyout() { return resultBuyout; }
    public String resultPlayer() { return resultPlayer; }
    public double resultAmount() { return resultAmount; }
    public ItemStack resultItem() { return resultItem; }

    /** Gültige Gebote (mindestens Mindestgebot), höchstes zuerst. */
    public List<Bid> top(int n) {
        List<Bid> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (e.getValue() >= minBid) list.add(new Bid(e.getKey(), e.getValue()));
        }
        list.sort((a, b) -> Double.compare(b.amount(), a.amount()));
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    /** Animierter Wert des höchsten Gebots (zählt hoch). */
    public double shownTop(long now) {
        double t = Math.min(1.0, Math.max(0.0, (now - topChangedMs) / 500.0));
        t = 1 - Math.pow(1 - t, 3);
        return prevTop + (curTop - prevTop) * t;
    }

    /** Neueste zuerst, höchstens n Stück, nur frische. */
    public List<Event> recentEvents(long now, int n) {
        List<Event> out = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && out.size() < n; i--) {
            Event e = events.get(i);
            if (now - e.at() < EVENT_MS) out.add(e);
        }
        return out;
    }

    // ---------- Steuerung ----------

    public boolean start(ItemStack stack, int seconds, double min, double buy) {
        if (running || stack == null || stack.isEmpty()) return false;
        long now = System.currentTimeMillis();
        item = stack.copy();
        minBid = min;
        buyout = buy;
        totalMs = seconds * 1000L;
        startMs = now;
        endMs = now + totalMs;
        totals.clear();
        events.clear();
        history.clear();
        payCount = 0;
        totalReceived = 0;
        prevTop = 0;
        curTop = 0;
        topChangedMs = 0;
        lastBidMs = 0;
        hasResult = false;
        running = true;

        AuctionConfig cfg = HugoAuctionClient.CONFIG;
        String name = itemName(item);
        local(Text.literal("[Auktion] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(name).formatted(Formatting.WHITE))
                .append(Text.literal(" gestartet (" + Fmt.time(seconds) + " Min)").formatted(Formatting.GRAY)));
        sound(true);
        if (cfg.announceInChat) {
            StringBuilder sb = new StringBuilder("Auktion: ").append(name);
            if (min > 0) sb.append(" | Mindestgebot ").append(Fmt.money(min));
            if (buy > 0) sb.append(" | Sofortkauf ").append(Fmt.money(buy));
            sb.append(" | Dauer ").append(Fmt.time(seconds));
            sb.append(" | Bieten: /pay ").append(payName());
            say(sb.toString());
        }
        return true;
    }

    public void cancel() {
        if (!running) return;
        running = false;
        local(Text.literal("[Auktion] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Auktion abgebrochen.").formatted(Formatting.RED)));
    }

    public void tick() {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            running = false;
            return;
        }
        if (System.currentTimeMillis() >= endMs) finishByTime();
    }

    public static String payName() {
        String n = HugoAuctionClient.CONFIG.payName;
        if (n != null && !n.isBlank()) return n.trim();
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getName().getString() : "";
    }

    // ---------- Chat auswerten ----------

    public void onSystemMessage(String text) {
        if (!running || text == null) return;
        AuctionConfig cfg = HugoAuctionClient.CONFIG;
        Matcher m = cfg.pattern.matcher(text);
        if (!m.matches()) return;

        String player;
        double paid;
        try {
            player = m.group("player");
            paid = Fmt.parse(m.group("amount").trim());
        } catch (Exception e) {
            return;
        }
        if (player == null || Double.isNaN(paid) || paid <= 0) return;

        long now = System.currentTimeMillis();
        List<Bid> before = top(1);
        String oldLeader = before.isEmpty() ? null : before.get(0).player();

        double total = cfg.cumulative
                ? totals.merge(player, paid, (a, b) -> a + b)
                : totals.merge(player, paid, (a, b) -> Math.max(a, b));

        history.add(new Pay(player, paid, total, total >= minBid, now));
        payCount++;
        totalReceived += paid;
        while (history.size() > 40) history.remove(0);

        if (total < minBid) {
            pushEvent("Zu niedrig", player + " " + Fmt.money(total) + " / min. " + Fmt.money(minBid), 0xFF5555, now);
            sound(false);
            return;
        }

        if (buyout > 0 && total >= buyout) {
            lastBidMs = now;
            finish(player, total, true);
            return;
        }

        List<Bid> after = top(1);
        if (after.isEmpty()) return;
        Bid lead = after.get(0);
        lastBidMs = now;
        if (lead.amount() != curTop) {
            prevTop = curTop;
            curTop = lead.amount();
            topChangedMs = now;
        }
        boolean isLeader = lead.player().equals(player);
        if (isLeader && !player.equals(oldLeader)) {
            pushEvent("Neuer Führender", player + " " + Fmt.money(total), 0x55FF55, now);
        } else if (isLeader) {
            pushEvent("Gebot erhöht", player + " " + Fmt.money(total), 0x55FF55, now);
        } else {
            pushEvent("Gebot", player + " " + Fmt.money(total), 0xAAAAAA, now);
        }
        sound(true);
    }

    // ---------- Ende ----------

    private void finishByTime() {
        List<Bid> top = top(1);
        if (top.isEmpty()) finish(null, 0, false);
        else finish(top.get(0).player(), top.get(0).amount(), false);
    }

    private void finish(String player, double amount, boolean buy) {
        running = false;
        hasResult = true;
        resultMs = System.currentTimeMillis();
        resultPlayer = player == null ? "" : player;
        resultAmount = amount;
        resultBuyout = buy;
        resultItem = item.copy();

        AuctionConfig cfg = HugoAuctionClient.CONFIG;
        String name = itemName(item);
        if (player == null) {
            local(Text.literal("[Auktion] ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal("Beendet: keine Gebote.").formatted(Formatting.GRAY)));
            localTotal();
            if (cfg.announceInChat) say("Auktion beendet: keine Gebote.");
            return;
        }
        local(Text.literal("[Auktion] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(buy ? "SOFORTKAUF! " : "Beendet! ").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(player).formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(" gewinnt " + name + " für ").formatted(Formatting.GRAY))
                .append(Text.literal(Fmt.money(amount)).formatted(Formatting.GREEN, Formatting.BOLD))
                .append(copyLink(player)));
        localTotal();
        if (cfg.announceInChat) {
            say(buy
                    ? "SOFORTKAUF! " + player + " gewinnt " + name + " für " + Fmt.money(amount)
                    : "Auktion beendet! Gewinner: " + player + " mit " + Fmt.money(amount) + " (" + name + ")");
        }
        fanfare();
    }

    // ---------- Hilfen ----------

    private void pushEvent(String title, String detail, int color, long now) {
        events.add(new Event(title, detail, color, now));
        while (events.size() > 8) events.remove(0);
    }

    private static String itemName(ItemStack stack) {
        String n = stack.getName().getString();
        return stack.getCount() > 1 ? n + " x" + stack.getCount() : n;
    }

    /** Anklickbarer Text im Chat, der den Namen in die Zwischenablage kopiert. */
    private static Text copyLink(String name) {
        return Text.literal("  [Namen kopieren]")
                .styled(st -> st.withClickEvent(new ClickEvent.CopyToClipboard(name))
                        .withColor(Formatting.AQUA)
                        .withUnderline(true));
    }

    private void localTotal() {
        local(Text.literal("[Auktion] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Insgesamt erhalten: ").formatted(Formatting.GRAY))
                .append(Text.literal(Fmt.money(totalReceived)).formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" aus " + payCount + " Zahlung" + (payCount == 1 ? "" : "en")).formatted(Formatting.GRAY)));
    }

    private static void local(Text t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(t, false);
    }

    private static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (s.length() > 250) s = s.substring(0, 250);
        mc.player.networkHandler.sendChatMessage(s);
    }

    private static void sound(boolean good) {
        if (!HugoAuctionClient.CONFIG.sounds) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(good ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_VILLAGER_NO,
                1.0f, good ? 1.3f : 1.0f);
    }

    private static void fanfare() {
        if (!HugoAuctionClient.CONFIG.sounds) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
}
