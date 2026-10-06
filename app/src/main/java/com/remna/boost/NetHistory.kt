package com.remna.boost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Ağ geçmişi: yalnız bölge bazında özet ölçümler (ortanca, dalgalanma, kayıp). IP adresi tutulmaz;
 * veriler yalnız bu cihazda saklanır ve Gizlilik'ten kapatılıp silinebilir.
 */
object NetHistory {
    private const val KEY = "net_hist"
    private const val MAX = 600

    data class RegionStat(
        val region: String,
        val count: Int,
        val avg: Int,
        val median: Int,
        val jitter: Int,
        val loss: Int,
    )

    @JvmStatic
    fun enabled(c: Context): Boolean = Boost.prefs(c).getBoolean("net_hist_on", true)

    @JvmStatic
    fun add(c: Context, region: String?, median: Int, jitter: Int, loss: Int, source: String) {
        if (region.isNullOrEmpty() || median <= 0 || !enabled(c)) return
        val p = Boost.prefs(c)
        val arr = read(c)
        arr.put(
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("r", region)
                .put("m", median)
                .put("j", jitter)
                .put("l", loss)
                .put("s", source)
        )
        val out = JSONArray()
        for (i in maxOf(0, arr.length() - MAX) until arr.length()) out.put(arr.get(i))
        p.edit().putString(KEY, out.toString()).apply()
    }

    /** days <= 0 → bugün. Bölgeler ortanca pinge göre sıralı. */
    @JvmStatic
    fun stats(c: Context, days: Int): List<RegionStat> {
        val arr = read(c)
        val since = if (days <= 0) startOfToday() else System.currentTimeMillis() - days * 86_400_000L
        val byRegion = LinkedHashMap<String, MutableList<JSONObject>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("t") < since) continue
            byRegion.getOrPut(o.optString("r")) { mutableListOf() }.add(o)
        }
        return byRegion.map { (region, list) ->
            val medians = list.map { it.optInt("m") }.sorted()
            RegionStat(
                region = region,
                count = list.size,
                avg = medians.average().toInt(),
                median = medians[medians.size / 2],
                jitter = list.map { it.optInt("j") }.average().toInt(),
                loss = list.map { it.optInt("l") }.average().toInt(),
            )
        }.sortedBy { it.median }
    }

    /** En stabil bölge: en az 3 ölçümü olanlar arasında en düşük dalgalanma (eşitse en düşük kayıp). */
    @JvmStatic
    fun mostStable(list: List<RegionStat>): RegionStat? =
        list.filter { it.count >= 3 }.minWithOrNull(compareBy<RegionStat> { it.jitter }.thenBy { it.loss })

    @JvmStatic
    fun count(c: Context): Int = read(c).length()

    @JvmStatic
    fun clear(c: Context) {
        Boost.prefs(c).edit().remove(KEY).apply()
    }

    private fun read(c: Context): JSONArray =
        try {
            JSONArray(Boost.prefs(c).getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
