package com.reelguard.app.proxy

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GraphQLAdStripper {
    private const val TAG = "GraphQLAdStripper"

    // Các trường dữ liệu quảng cáo chèn ngang video và bài viết tài trợ
    private val AD_KEYS_TO_NULLIFY = setOf(
        "in_stream_ad_break",
        "ad_breaks",
        "instream_video_ad",
        "sponsored_data",
        "commercial_break",
        "client_sponsored_data",
        "in_stream_ad",
        "video_ad_break",
        "ad_break_response",
        "ad_pod",
        "non_interruptive_ad",
        "overlay_ad"
    )

    fun stripAdsFromPayload(rawBytes: ByteArray, isGzip: Boolean): Pair<ByteArray, Boolean> {
        try {
            val jsonString = if (isGzip) {
                decompressGzip(rawBytes)
            } else {
                String(rawBytes, StandardCharsets.UTF_8)
            }

            if (!jsonString.trimStart().startsWith("{") && !jsonString.trimStart().startsWith("[")) {
                return Pair(rawBytes, false)
            }

            var modified = false

            if (jsonString.trimStart().startsWith("{")) {
                val jsonObject = JSONObject(jsonString)
                if (cleanJsonObject(jsonObject)) {
                    modified = true
                }
                val cleanedJsonStr = jsonObject.toString()
                val resultBytes = if (isGzip) compressGzip(cleanedJsonStr) else cleanedJsonStr.toByteArray(StandardCharsets.UTF_8)
                return Pair(resultBytes, modified)
            } else {
                val jsonArray = JSONArray(jsonString)
                if (cleanJsonArray(jsonArray)) {
                    modified = true
                }
                val cleanedJsonStr = jsonArray.toString()
                val resultBytes = if (isGzip) compressGzip(cleanedJsonStr) else cleanedJsonStr.toByteArray(StandardCharsets.UTF_8)
                return Pair(resultBytes, modified)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bỏ qua phân tích JSON non-GraphQL hoặc lỗi cú pháp: ${e.message}")
            return Pair(rawBytes, false)
        }
    }

    private fun cleanJsonObject(obj: JSONObject): Boolean {
        var changed = false
        val keys = obj.keys()
        val keysList = mutableListOf<String>()
        while (keys.hasNext()) {
            keysList.add(keys.next())
        }

        for (key in keysList) {
            if (AD_KEYS_TO_NULLIFY.contains(key.lowercase())) {
                obj.put(key, JSONObject.NULL)
                changed = true
                continue
            }

            val value = obj.opt(key)
            if (value is JSONObject) {
                if (cleanJsonObject(value)) {
                    changed = true
                }
            } else if (value is JSONArray) {
                // Nếu là mảng edges chứa danh sách video/feed stories
                if (key.equals("edges", ignoreCase = true)) {
                    if (cleanEdgesArray(value)) {
                        changed = true
                    }
                } else {
                    if (cleanJsonArray(value)) {
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun cleanJsonArray(arr: JSONArray): Boolean {
        var changed = false
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            if (item is JSONObject) {
                if (cleanJsonObject(item)) {
                    changed = true
                }
            } else if (item is JSONArray) {
                if (cleanJsonArray(item)) {
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * Lọc bỏ các card quảng cáo trong edges nhưng BẢO TỒN NGUYÊN VẸN cấu trúc mảng & phân trang
     */
    private fun cleanEdgesArray(edges: JSONArray): Boolean {
        var changed = false
        val indicesToRemove = mutableListOf<Int>()

        for (i in 0 until edges.length()) {
            val edgeObj = edges.optJSONObject(i) ?: continue
            val node = edgeObj.optJSONObject("node")

            if (node != null) {
                val typeName = node.optString("__typename")
                val isSponsored = node.optBoolean("is_sponsored", false) || node.optBoolean("is_ad", false)
                val hasSponsoredData = node.has("sponsored_data") && !node.isNull("sponsored_data")

                if (typeName.contains("Sponsored", ignoreCase = true) || isSponsored || hasSponsoredData) {
                    indicesToRemove.add(i)
                    changed = true
                } else {
                    if (cleanJsonObject(edgeObj)) {
                        changed = true
                    }
                }
            }
        }

        // Xóa từ cuối lên đầu để không làm sai lệch index
        for (i in indicesToRemove.reversed()) {
            edges.remove(i)
        }

        return changed
    }

    private fun decompressGzip(compressed: ByteArray): String {
        ByteArrayInputStream(compressed).use { bis ->
            GZIPInputStream(bis).use { gis ->
                return gis.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        }
    }

    private fun compressGzip(data: String): ByteArray {
        ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gos ->
                gos.write(data.toByteArray(StandardCharsets.UTF_8))
            }
            return bos.toByteArray()
        }
    }
}
