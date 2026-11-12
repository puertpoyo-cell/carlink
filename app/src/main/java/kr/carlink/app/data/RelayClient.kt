package kr.carlink.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Supabase(PostgREST) 를 이용한 메시지 중계.
 *
 * 프로토타입은 외부 라이브러리 없이 HTTPS 요청만 사용하고, 1초 간격으로 새 메시지를 조회한다.
 * 접속 정보가 없으면 [isConfigured] 가 false 이고 앱은 로컬 데모 모드로 동작한다.
 */
class RelayClient(baseUrl: String, private val anonKey: String) {

    private val endpoint = baseUrl.trimEnd('/') + "/rest/v1/messages"

    val isConfigured: Boolean = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    /** 지금까지 저장된 마지막 메시지 번호. 앱을 켜기 전의 메시지를 건너뛰는 데 쓴다. */
    suspend fun latestId(): Long = withContext(Dispatchers.IO) {
        val body = request("GET", "$endpoint?select=id&order=id.desc&limit=1")
        val arr = JSONArray(body)
        if (arr.length() == 0) 0L else arr.getJSONObject(0).getLong("id")
    }

    suspend fun send(m: WireMessage): Unit = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("segment", m.segment)
            .put("from_vehicle", m.fromVehicle)
            .put("from_order", m.fromOrder)
            .put("target", m.target.name)
            .put("sentence_id", m.sentenceId)
            .put("tier", m.tier.name)
            .put("trust", m.trust.name)
        request("POST", endpoint, json.toString())
        Unit
    }

    /** [afterId] 보다 뒤에 저장된, 같은 도로 구간의 메시지를 번호 순으로 가져온다. */
    suspend fun fetchAfter(afterId: Long, segment: String): List<WireMessage> = withContext(Dispatchers.IO) {
        val seg = URLEncoder.encode(segment, "UTF-8")
        val body = request("GET", "$endpoint?select=*&segment=eq.$seg&id=gt.$afterId&order=id.asc&limit=50")
        val arr = JSONArray(body)
        val out = ArrayList<WireMessage>(arr.length())
        for (i in 0 until arr.length()) {
            parse(arr.getJSONObject(i))?.let { out.add(it) }
        }
        out
    }

    private fun parse(o: JSONObject): WireMessage? = try {
        WireMessage(
            id = o.getLong("id"),
            segment = o.getString("segment"),
            fromVehicle = o.getString("from_vehicle"),
            fromOrder = o.getInt("from_order"),
            target = SendTarget.valueOf(o.getString("target")),
            sentenceId = o.getString("sentence_id"),
            tier = Tier.valueOf(o.getString("tier")),
            trust = runCatching { Trust.valueOf(o.optString("trust", "NONE")) }.getOrDefault(Trust.NONE),
            createdAt = o.optString("created_at", "")
        )
    } catch (e: Exception) {
        null // 형식이 맞지 않는 행은 무시한다.
    }

    private fun request(method: String, url: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("서버 응답 $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
