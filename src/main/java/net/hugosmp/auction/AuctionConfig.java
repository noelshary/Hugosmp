package net.hugosmp.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AuctionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hugoauction-neu.json");

    public static final int VERSION = 2;
    public static final String DEFAULT_PATTERN =
            "^.*Du hast \\$?(?<amount>[\\d.,]+ ?[kKmMbBtT]?) ?\\$? von (?<player>\\w{3,16}) erhalten\\.?\\s*$";

    /** 0 = alte Datei ohne Versionsfeld. */
    public int configVersion = 0;
    public String paymentPattern = DEFAULT_PATTERN;
    /** true: mehrere Zahlungen desselben Spielers werden addiert. */
    public boolean cumulative = true;
    /** true: Start und Ende werden öffentlich in den Chat geschrieben. */
    public boolean announceInChat = true;
    public boolean sounds = true;
    /** Name, der bei der Auktion als "/pay NAME" angezeigt wird. */
    public String payName = "Noel_shary";
    public int lastSeconds = 180;
    public String lastMinBid = "";
    public String lastBuyout = "";

    public transient Pattern pattern;

    public static AuctionConfig load() {
        AuctionConfig cfg = null;
        try {
            if (Files.exists(FILE)) {
                cfg = GSON.fromJson(Files.readString(FILE), AuctionConfig.class);
            }
        } catch (Exception e) {
            HugoAuctionClient.LOGGER.error("Config konnte nicht geladen werden", e);
        }
        if (cfg == null) cfg = new AuctionConfig();
        if (cfg.configVersion < VERSION) {
            cfg.paymentPattern = DEFAULT_PATTERN;
            cfg.configVersion = VERSION;
        }
        if (cfg.payName == null || cfg.payName.isBlank()) cfg.payName = "Noel_shary";
        if (cfg.lastMinBid == null) cfg.lastMinBid = "";
        if (cfg.lastBuyout == null) cfg.lastBuyout = "";
        if (cfg.lastSeconds < 5) cfg.lastSeconds = 180;
        cfg.compile();
        cfg.save();
        return cfg;
    }

    public void compile() {
        try {
            pattern = Pattern.compile(paymentPattern);
        } catch (PatternSyntaxException | NullPointerException e) {
            HugoAuctionClient.LOGGER.error("Ungültiges paymentPattern, Gebote sind deaktiviert", e);
            pattern = Pattern.compile("a^");
        }
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            HugoAuctionClient.LOGGER.error("Config konnte nicht gespeichert werden", e);
        }
    }
}
