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
        if (conns.isEmpty() || !Boost.prefs(c).getBoolean("srv_record", true)) return;
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

    /**
     * Yerel (cihaz içi) GeoIP: Türkmenistan'dan yapılan analizlerde doğrulanmış PUBG veri merkezi aralıkları.
     * Hiçbir veri dışarı gönderilmez. {ülkeKodu, ülke, şehir, ağ}
     */
    static final String[][] LOCAL_GEO = {
            {"49.51.130.", "DE", "Almanya", "Frankfurt", "AS132203 Tencent"},
            {"162.62.", "DE", "Almanya", "Frankfurt", "AS132203 Tencent"},
            {"101.32.", "SG", "Singapur", "Singapur", "AS132203 Tencent"},
            {"150.109.", "SG", "Singapur", "Singapur", "AS132203 Tencent"},
            {"119.28.121.", "SG", "Singapur", "Singapur", "AS132203 Tencent"},
            {"119.28.149.", "KR", "Güney Kore", "Seul", "AS132203 Tencent"},
            {"43.129.", "HK", "Hong Kong", "Hong Kong", "AS132203 Tencent"},
            {"170.106.", "US", "ABD", "Santa Clara", "AS132203 Tencent"},
            {"20.74.", "AE", "BAE", "Dubai", "AS8075 Microsoft Azure"},
    };

    static String[] localGeo(String ip) {
        for (String[] g : LOCAL_GEO) if (ip.startsWith(g[0])) return g;
        return null;
    }

    /** Konumu olmayan kayıtları doldurur: önce yerel tablo; kullanıcı izin verdiyse HTTPS (ipwho.is), en fazla 30. */
    static void enrich(Context c) {
        JSONObject log = load(c);
        boolean online = Boost.prefs(c).getBoolean("geo_online", false);
        int asked = 0;
        boolean changed = false;
        for (Iterator<String> it = log.keys(); it.hasNext(); ) {
            String ip = it.next();
            JSONObject e = log.optJSONObject(ip);
            if (e == null || e.has("cc")) continue;
            try {
                String[] g = localGeo(ip);
                if (g != null) {
                    e.put("cc", g[1]); e.put("country", g[2]); e.put("city", g[3]); e.put("as", g[4]); e.put("geo", "yerel");
                    changed = true;
                } else if (online && asked < 30) {
                    asked++;
                    HttpURLConnection h = (HttpURLConnection) new URL("https://ipwho.is/" + ip + "?fields=success,country_code,country,city,connection&lang=tr").openConnection();
                    h.setConnectTimeout(6000);
                    h.setReadTimeout(6000);
                    String js;
                    try (InputStream in = h.getInputStream()) { js = new Scanner(in, "UTF-8").useDelimiter("\\A").next(); }
                    JSONObject g2 = new JSONObject(js);
                    if (!g2.optBoolean("success")) continue;
                    JSONObject con = g2.optJSONObject("connection");
                    e.put("cc", g2.optString("country_code"));
                    e.put("country", g2.optString("country"));
                    e.put("city", g2.optString("city"));
                    if (con != null) e.put("as", "AS" + con.optInt("asn") + " " + con.optString("org"));
                    e.put("geo", "ipwho.is");
                    changed = true;
                }
            } catch (Exception ex) {
                Boost.prefs(c).edit().putString("geo_err", ex.getClass().getSimpleName()).apply();
            }
        }
        if (changed) save(c, log);
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

    /**
     * Panel pingi için hedef: kayıtlı PUBG veri merkezi IP'lerinden (Tencent/Azure) VPN'siz ICMP yanıtı vermiş olanlar.
     * Tercih edilen bölgede en düşük pingli olan; yoksa genel en iyisi. {ip, bölge} ya da null.
     */
    static String[] pingTarget(Context c, String preferRegion) {
        JSONObject log = load(c);
        String bestIp = null, bestReg = null, anyIp = null, anyReg = null;
        int best = Integer.MAX_VALUE, any = Integer.MAX_VALUE;
        for (Iterator<String> it = log.keys(); it.hasNext(); ) {
            String ip = it.next();
            JSONObject e = log.optJSONObject(ip);
            String as = e.optString("as");
            boolean dc = as.contains("132203") || as.contains("8075") || "Tencent Cloud".equals(knownNet(ip));
            int p = e.optInt("ping", -1);
            if (!dc || p <= 0 || "tcp".equals(e.optString("pingHow", e.optString("how")))) continue;
            String reg = region(e.optString("cc"));
            if (preferRegion != null && preferRegion.equals(reg) && p < best) { best = p; bestIp = ip; bestReg = reg; }
            if (p < any) { any = p; anyIp = ip; anyReg = reg; }
        }
        if (bestIp != null) return new String[]{bestIp, bestReg};
        return anyIp != null ? new String[]{anyIp, anyReg} : null;
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
        boolean stale;
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
            String k = vpnMode ? "pingVpn" : "ping";
            r.ping = e.optInt(k, -1);
            r.how = e.optString(k + "How", e.optString("how", ""));
            out.add(r);
        }
        return out;
    }

    static boolean vpnMode;

    static void putPing(Context c, String ip, int ms, String how) {
        JSONObject log = load(c);
        JSONObject e = log.optJSONObject(ip);
        if (e == null) return;
        if (ms < 0 && e.optInt("ping", -1) > 0) return; // başarısız ölçüm eski iyi değeri silmesin
        if (ms <= 0) return; // başarısız ölçüm eski iyi değeri silmesin
        String k = vpnMode ? "pingVpn" : "ping";
        try { e.put(k, ms); e.put(k + "How", how); e.put(k + "At", System.currentTimeMillis()); } catch (Exception ignored) {}
        save(c, log);
    }
}
