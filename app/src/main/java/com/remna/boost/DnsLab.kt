package com.remna.boost

import android.content.Context
import android.provider.Settings
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * DNS laboratuvarı: sağlayıcıları gerçek DNS sorgularıyla ölçer (UDP 53 ve DNS-over-TLS 853), filtreli yanıtları
 * tespit eder ve Android "Özel DNS" ayarını güvenli şekilde uygular (doğrulama başarısızsa geri alır).
 * DNS gecikmesi oyun içi pingi DEĞİŞTİRMEZ; yalnız alan adı çözümleme süresidir.
 */
object DnsLab {

    data class Provider(val name: String, val ip1: String, val ip2: String, val dot: String?, val note: String)

    @JvmField
    val PROVIDERS = listOf(
        Provider("Cloudflare", "1.1.1.1", "1.0.0.1", "one.one.one.one", "Hızlı, düşük gecikme"),
        Provider("Google", "8.8.8.8", "8.8.4.4", "dns.google", "Yüksek kararlılık"),
        Provider("Quad9", "9.9.9.9", "149.112.112.112", "dns.quad9.net", "Zararlı alan adı koruması"),
        Provider("AdGuard", "94.140.14.14", "94.140.15.15", "dns.adguard-dns.com", "Reklam ve izleyici engeli"),
        Provider("OpenDNS", "208.67.222.222", "208.67.220.220", null, "Cisco · Özel DNS desteği yok"),
        Provider("Yerel (TM)", "217.174.238.141", "217.174.239.141", null, "Doğrulanmamış · yalnız test"),
    )

    data class Result(
        val provider: Provider,
        val sent: Int,
        val ok: Int,
        val min: Int,
        val avg: Int,
        val max: Int,
        val filtered: Boolean,
        val dotMs: Int,
        val status: String,
    ) {
        val lossPct: Int get() = if (sent == 0) 100 else (sent - ok) * 100 / sent
        /** Sıralama puanı (düşük iyi): ortalama + kayıp ve kararsızlık cezası. */
        val rank: Int get() = if (ok == 0) Int.MAX_VALUE else avg + lossPct * 5 + (max - min) / 4
    }

    private val TEST_DOMAINS = listOf("example.com", "wikipedia.org", "cloudflare.com", "github.com", "microsoft.com")
    private const val CANARY = "googleads.g.doubleclick.net" // Türkmenistan'da yerel DNS'in 127.0.0.1'e çevirdiği bilinen alan adı

    /** Tek bir A sorgusu (UDP 53). Dönüş: (ms, IPv4 yanıtları); başarısızsa ms = -1. */
    @JvmStatic
    fun query(server: String, host: String, timeoutMs: Int): Pair<Int, List<String>> {
        val id = Random.nextInt(0, 65535)
        val q = build(id, host)
        return try {
            DatagramSocket().use { s ->
                s.soTimeout = timeoutMs
                val addr = InetAddress.getByName(server)
                val t = System.nanoTime()
                s.send(DatagramPacket(q, q.size, addr, 53))
                val buf = ByteArray(1500)
                val p = DatagramPacket(buf, buf.size)
                while (true) {
                    s.receive(p)
                    if (p.length >= 12 && ((buf[0].toInt() and 0xff) shl 8 or (buf[1].toInt() and 0xff)) == id) break
                }
                val ms = ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
                Pair(ms, parseA(buf, p.length))
            }
        } catch (e: Exception) {
            Pair(-1, emptyList())
        }
    }

    /** DNS-over-TLS (853) testi: TLS el sıkışma + sertifika/ad doğrulama + bir sorgu. ms ya da -1. */
    @JvmStatic
    fun dotTest(ip: String, host: String, timeoutMs: Int): Int {
        return try {
            val t = System.nanoTime()
            val plain = Socket()
            plain.connect(InetSocketAddress(ip, 853), timeoutMs)
            plain.soTimeout = timeoutMs
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, 853, true) as SSLSocket
            ssl.use { s ->
                s.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)) return -1
                val q = build(Random.nextInt(0, 65535), "example.com")
                val out = DataOutputStream(s.outputStream)
                out.writeShort(q.size)
                out.write(q)
                out.flush()
                val inp = DataInputStream(s.inputStream)
                val len = inp.readUnsignedShort()
                val resp = ByteArray(len)
                inp.readFully(resp)
                ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
            }
        } catch (e: Exception) {
            -1
        }
    }

    /** Bir sağlayıcıyı ölçer: 2 sunucu × 5 alan adı UDP sorgusu, filtre kontrolü ve DoT. */
    @JvmStatic
    fun test(p: Provider): Result {
        val times = ArrayList<Int>()
        var sent = 0
        for (i in TEST_DOMAINS.indices) {
            val server = if (i % 2 == 0) p.ip1 else p.ip2
            sent++
            val (ms, _) = query(server, TEST_DOMAINS[i], 1500)
            if (ms > 0) times.add(ms)
        }
        val (cms, ans) = query(p.ip1, CANARY, 1500)
        val filtered = cms > 0 && ans.any { it.startsWith("127.") || it == "0.0.0.0" }
        val dot = if (p.dot != null) dotTest(p.ip1, p.dot, 3000) else -1
        val ok = times.size
        val min = times.minOrNull() ?: -1
        val max = times.maxOrNull() ?: -1
        val avg = if (ok == 0) -1 else times.sum() / ok
        val status = when {
            ok == 0 -> "✕ Erişilemiyor"
            ok < sent - 1 || (min > 0 && max > min * 3 && max - min > 60) -> "⚠ Kararsız"
            avg > 150 -> "◐ Yavaş"
            else -> "✓ Çalışıyor"
        }
        return Result(p, sent, ok, min, avg, max, filtered, dot, status)
    }

    /* ---------------- Android Özel DNS (DoT) ---------------- */

    /** Şu anki Özel DNS durumu: {mod, sağlayıcı adı}. */
    @JvmStatic
    fun current(c: Context): Array<String> {
        val cr = c.contentResolver
        val mode = Settings.Global.getString(cr, "private_dns_mode") ?: "opportunistic"
        val spec = Settings.Global.getString(cr, "private_dns_specifier") ?: ""
        return arrayOf(mode, spec)
    }

    /**
     * Özel DNS'i uygular (hostname modu). Önceki değer saklanır. host = null → "Otomatik" (opportunistic).
     * WRITE_SECURE_SETTINGS gerekir. Başarı: true.
     */
    @JvmStatic
    fun apply(c: Context, host: String?): Boolean {
        val p = Boost.prefs(c)
        val cur = current(c)
        if (!p.contains("pdns_prev_mode")) p.edit().putString("pdns_prev_mode", cur[0]).putString("pdns_prev_spec", cur[1]).apply()
        return try {
            val cr = c.contentResolver
            if (host == null) {
                Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
            } else {
                Settings.Global.putString(cr, "private_dns_specifier", host)
                Settings.Global.putString(cr, "private_dns_mode", "hostname")
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Uygulamadan önceki Özel DNS ayarına döner. */
    @JvmStatic
    fun revert(c: Context): Boolean {
        val p = Boost.prefs(c)
        val mode = p.getString("pdns_prev_mode", "opportunistic") ?: "opportunistic"
        val spec = p.getString("pdns_prev_spec", "") ?: ""
        return try {
            val cr = c.contentResolver
            if (spec.isNotEmpty()) Settings.Global.putString(cr, "private_dns_specifier", spec)
            Settings.Global.putString(cr, "private_dns_mode", mode)
            p.edit().remove("pdns_prev_mode").remove("pdns_prev_spec").apply()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Sistem çözücüsüyle doğrulama: alan adı 6 sn içinde çözülürse true. */
    @JvmStatic
    fun verifySystemDns(): Boolean {
        var ok = false
        val th = Thread {
            ok = try { InetAddress.getAllByName("example.com").isNotEmpty() } catch (e: Exception) { false }
        }
        th.start()
        th.join(6000)
        return ok
    }

    /* ---------------- DNS paketi ---------------- */

    private fun build(id: Int, host: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(id shr 8); out.write(id and 0xff)
        out.write(0x01); out.write(0x00) // özyinelemeli sorgu
        out.write(0); out.write(1)       // 1 soru
        repeat(6) { out.write(0) }       // an/ns/ar = 0
        for (label in host.split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            out.write(b.size); out.write(b)
        }
        out.write(0)
        out.write(0); out.write(1) // A
        out.write(0); out.write(1) // IN
        return out.toByteArray()
    }

    private fun parseA(b: ByteArray, len: Int): List<String> {
        val res = ArrayList<String>()
        fun u16(i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
        val qd = u16(4)
        val an = u16(6)
        var i = 12
        fun skipName() {
            while (i < len) {
                val l = b[i].toInt() and 0xff
                if (l == 0) { i += 1; return }
                if (l and 0xC0 == 0xC0) { i += 2; return }
                i += l + 1
            }
        }
        repeat(qd) { skipName(); i += 4 }
        repeat(an) {
            if (i >= len) return res
            skipName()
            if (i + 10 > len) return res
            val type = u16(i)
            val rdlen = u16(i + 8)
            i += 10
            if (type == 1 && rdlen == 4 && i + 4 <= len)
                res.add("${b[i].toInt() and 0xff}.${b[i + 1].toInt() and 0xff}.${b[i + 2].toInt() and 0xff}.${b[i + 3].toInt() and 0xff}")
            i += rdlen
        }
        return res
    }
}
