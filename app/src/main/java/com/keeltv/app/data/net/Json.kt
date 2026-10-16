package com.keeltv.app.data.net

import com.squareup.moshi.JsonReader
import okio.BufferedSource

typealias Row = Map<String, Any?>

/**
 * Accepts numbers because providers mix types in the same field: `stream_id` arrives as `12345`
 * on one endpoint and `"12345"` on another.
 */
fun Row.str(key: String): String? = when (val v = this[key]) {
  is String -> v.trim().ifEmpty { null }
  is Double -> if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
  is Int -> v.toString()
  is Long -> v.toString()
  else -> null
}

fun Row.num(key: String): Double? = when (val v = this[key]) {
  is Double -> if (v.isFinite()) v else null
  is Int -> v.toDouble()
  is Long -> v.toDouble()
  is String -> v.trim().toDoubleOrNull()
  else -> null
}

fun Row.int(key: String): Int? = num(key)?.toInt()
fun Row.long(key: String): Long? = num(key)?.toLong()

fun Row.bool(key: String): Boolean = when (val v = this[key]) {
  is Boolean -> v
  is Double -> v == 1.0
  is String -> v == "1" || v.equals("true", ignoreCase = true)
  else -> false
}

fun Row.row(key: String): Row? = this[key] as? Map<String, Any?>

fun Row.list(key: String): List<Any?>? = this[key] as? List<Any?>

class JsonShapeException(message: String) : Exception(message)

/**
 * Stream a top-level JSON array of objects, one row at a time, so a large catalogue dump is never
 * materialised as a tree.
 *
 * The provider answers an unknown category with `[]`, an expired login with
 * `{"error":"Authentication failed"}`, and some deployments answer with the `{"user_info":...}`
 * auth blob whatever the action. An object where an array was expected is reported, never crashed
 * on.
 */
fun readRows(source: BufferedSource, context: String, onRow: (Row) -> Unit): Int {
  JsonReader.of(source).use { reader ->
    reader.isLenient = true
    return when (reader.peek()) {
      JsonReader.Token.BEGIN_ARRAY -> {
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
          val value = reader.readJsonValue()
          if (value is Map<*, *>) {
            onRow(value as Row)
            count++
          }
        }
        reader.endArray()
        count
      }

      JsonReader.Token.BEGIN_OBJECT -> {
        val obj = reader.readJsonValue() as? Row ?: emptyMap()
        val err = obj.str("error") ?: obj.str("message")
        throw JsonShapeException(
          if (err != null) "$context: provider answered \"$err\"" else "$context: expected a list, got an object",
        )
      }

      JsonReader.Token.NULL, JsonReader.Token.END_DOCUMENT -> 0
      else -> throw JsonShapeException("$context: expected a list")
    }
  }
}

fun readObject(source: BufferedSource): Row? {
  JsonReader.of(source).use { reader ->
    reader.isLenient = true
    return reader.readJsonValue() as? Row
  }
}
