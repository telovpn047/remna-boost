package com.remna.boost;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

/** PUBG'nin bağlandığı sunucuların geçmişi: kaydetme, konum zenginleştirme, bölgeye eşleme. */
final class ServerLog {
    private ServerLog() {}
    static final int MAX = 300;

    static JSONObject load(Context c) {
        try { return new JSONObject(Boost.prefs(c).getString("srv_log", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void save(Context c, JSONObject o) { Boost.prefs(c).edit().putString("srv_log", o.toString()).apply(); }

    static void clear(Context c) { Boost.prefs(c).edit().remove("srv_log").remove("srv_ip").apply(); }

    /** Görülen bağlantıları kaydeder. vpn: o an VPN açık mıydı. */
    static synchronized void record(Context c, List<String[]> conns, boolean vpn) {
        if (conns.isEmpty()) return;
        JSONObject log = load(c);
        long now = System.currentTimeMillis();
        try {
            for (String[] k : conns) {
                JSONObject e = log.optJSONObject(k[1]);
                if (e == null) { e = new JSONObject(); e.put("first", now); e.put("n", 0); }
                e.put("proto", k[0]);
                e.put("port", k[2]);
                e.put("last", now);
                e.put("n", e.optInt("n") + 1);
                e.put(vpn ? "vpn" : "direct", true);
                log.put(k[1], e);
            }
            // en eski görülenleri at
            while (log.length() > MAX) {
                String oldest = null;
                long t = Long.MAX_VALUE;
                for (Iterator<String> it = log.keys(); it.hasNext(); ) {
                    String ip = it.next();
                    long l = log.getJSONObject(ip).optLong("last");
                    if (l < t) { t = l; oldest = ip; }
                }
                log.remove(oldest);
            }
        } catch (Exception ignored) {}
        save(c, log);
    }

    /** Konumu olmayan IP'ler için ip-api.com toplu sorgu (en fazla 100). */
    static void enrich(Context c) {
        JSONObject log = load(c);
        JSONArray q = new JSONArray();
        for (Iterator<String> it = log.keys(); it.hasNext(); ) {
            String ip = it.next();
            if (!log.optJSONObject(ip).has("cc") && q.length() < 100) q.put(ip);
        }
        if (q.length() == 0) return;
        try {
            String js = Boost.httpVia("ip-api.com", "POST", "/batch?fields=query,status,countryCode,country,city,as&lang=tr", q.toString());
            JSONArray r = new JSONArray(js);
            for (int i = 0; i < r.length(); i++) {
                JSONObject g = r.getJSONObject(i);
                JSONObject e = log.optJSONObject(g.optString("query"));
                if (e == null || !"success".equals(g.optString("status"))) continue;
                e.put("cc", g.optString("countryCode"));
                e.put("country", g.optString("country"));
                e.put("city", g.optString("city"));
                e.put("as", g.optString("as"));
            }
            save(c, log);
        } catch (Exception e) {
            Boost.prefs(c).edit().putString("geo_err", e.getClass().getSimpleName() + ": " + e.getMessage()).apply();
        }
    }

    /** Konum alınamazsa: bilinen büyük ağ aralıklarından sağlayıcı tahmini. */
    static String knownNet(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) return "";
        int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
        if ((a == 101 && (b == 32 || b == 33)) || (a == 150 && b == 109) || (a == 119 && (b == 28 || b == 29))
                || (a == 162 && b == 62) || (a == 170 && b == 106) || (a == 43 && b >= 128) || (a == 129 && b == 226))
            return "Tencent Cloud";
        if (a == 20 || a == 40 || a == 52 || a == 13) return "Microsoft Azure / bulut";
        if ((a == 172 && b == 217) || (a == 142 && b == 250) || (a == 216 && b == 58)) return "Google";
        if (a == 23 || (a == 2 && b >= 16 && b <= 23)) return "Akamai CDN";
        if (a == 104 && b >= 16 && b <= 31) return "Cloudflare";
        return "";
    }

    /** Ülke koduna göre PUBG lobi bölgesi. */
    static String region(String cc) {
        if (cc == null || cc.isEmpty()) return "?";
        switch (cc) {
            case "AE": case "BH": case "SA": case "QA": case "KW": case "OM": case "TR": case "IR": case "IQ":
            case "JO": case "IL": case "EG": case "LB": case "AZ": case "GE": case "AM":
                return "Orta Doğu";
            case "SG": case "HK": case "MY": case "TH": case "ID": case "PH": case "VN": case "TW": case "MO": case "CN":
                return "Asya";
            case "JP": case "KR":
                return "KRJP";
            case "IN": case "PK": case "BD":
                return "Hindistan/Güney Asya";
            case "US": case "CA": case "MX":
                return "Kuzey Amerika";
            case "BR": case "AR": case "CL": case "CO": case "PE":
                return "Güney Amerika";
            case "KZ": case "UZ": case "TM": case "KG": case "TJ":
                return "Orta Asya";
            default:
                return "Avrupa";
        }
    }

    /** Analiz sonucu satırları için yardımcı yapı. */
    static final class Row {
        String ip, proto, where, as, region, how = "";
        int ping = -1, n;
        boolean vpn, direct;
    }

    static List<Row> rows(Context c) {
        JSONObject log = load(c);
        List<Row> out = new ArrayList<>();
        for (Iterator<String> it = log.keys(); it.hasNext(); ) {
            String ip = it.next();
            JSONObject e = log.optJSONObject(ip);
            Row r = new Row();
            r.ip = ip;
            r.proto = e.optString("proto");
            r.n = e.optInt("n");
            r.where = e.has("cc") ? (e.optString("city") + ", " + e.optString("country")) : "konum yok";
            r.as = e.optString("as");
            if (r.as.isEmpty()) r.as = knownNet(ip);
            r.region = region(e.optString("cc"));
            r.vpn = e.optBoolean("vpn");
            r.direct = e.optBoolean("direct");
            r.ping = e.optInt("ping", -1);
            out.add(r);
        }
        return out;
    }

    static void putPing(Context c, String ip, int ms) {
        JSONObject log = load(c);
        JSONObject e = log.optJSONObject(ip);
        if (e == null) return;
        try { e.put("ping", ms); e.put("pingAt", System.currentTimeMillis()); } catch (Exception ignored) {}
        save(c, log);
    }
}
