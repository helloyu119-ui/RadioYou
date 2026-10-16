package com.example.radioyou

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val favicon: String,
    val tags: String,
    val country: String,
    val bitrate: Int,
)

object RadioApi {
    private const val BASE = "https://de1.api.radio-browser.info/json"

    fun search(name: String, countryCode: String, limit: Int = 60): List<Station> {
        val q = buildString {
            append("$BASE/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=$limit")
            if (name.isNotBlank()) append("&name=" + URLEncoder.encode(name, "UTF-8"))
            if (countryCode.isNotBlank()) append("&countrycode=$countryCode")
        }
        val conn = (URL(q).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "RadioYou/1.0")
            connectTimeout = 10_000; readTimeout = 15_000
        }
        return try {
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Station(
                    uuid = o.optString("stationuuid"),
                    name = o.optString("name").trim(),
                    url = o.optString("url_resolved").ifBlank { o.optString("url") },
                    favicon = o.optString("favicon"),
                    tags = o.optString("tags"),
                    country = o.optString("countrycode"),
                    bitrate = o.optInt("bitrate"),
                )
            }.filter { it.url.isNotBlank() && it.name.isNotBlank() }
        } finally { conn.disconnect() }
    }
}
