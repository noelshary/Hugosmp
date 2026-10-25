package net.hugosmp.auction;

import java.util.Locale;

/** Zahlen formatieren und einlesen (1k, 1,5m, 1.000.000 ...). */
public final class Fmt {
    private Fmt() {}

    public static String money(double v) {
        return "$" + shortNum(v);
    }

    public static String shortNum(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return trim(v / 1e12) + "T";
        if (a >= 1e9) return trim(v / 1e9) + "B";
        if (a >= 1e6) return trim(v / 1e6) + "M";
        if (a >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s.replace('.', ',');
    }

    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Liefert NaN, wenn der Text keine gültige Zahl ist. */
    public static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.toLowerCase(Locale.ROOT).replace("$", "").replace(" ", "").replace("_", "").trim();
        if (s.isEmpty()) return Double.NaN;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1e3;
            case 'm' -> mult = 1e6;
            case 'b' -> mult = 1e9;
            case 't' -> mult = 1e12;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return Double.NaN;

        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        String num;
        if (dot >= 0 && comma >= 0) {
            char dec = dot > comma ? '.' : ',';
            char grp = dec == '.' ? ',' : '.';
            num = s.replace(String.valueOf(grp), "").replace(dec, '.');
        } else if (dot >= 0 || comma >= 0) {
            char sep = dot >= 0 ? '.' : ',';
            String sepRegex = sep == '.' ? "\\." : ",";
            boolean grouped = s.matches("\\d{1,3}(" + sepRegex + "\\d{3})+");
            if (grouped && mult == 1) num = s.replace(String.valueOf(sep), "");
            else num = s.replace(sep, '.');
        } else {
            num = s;
        }
        try {
            double v = Double.parseDouble(num) * mult;
            return (v >= 0 && Double.isFinite(v)) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
