package com.keeltv.app.data.repo

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.keeltv.app.core.UNKNOWN_LANGUAGE
import com.keeltv.app.core.genresOf
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind

enum class SortKey(val wire: String, val label: String) {
  PROVIDER("provider", "Provider order"),
  NAME("name", "A to Z"),
  ADDED("added", "Recently added"),
  RATING("rating", "Top rated"),
  YEAR("year", "Newest first");

  companion object {
    fun of(wire: String?): SortKey = entries.firstOrNull { it.wire == wire } ?: PROVIDER
  }
}

data class Facets(
  val genres: List<String> = emptyList(),
  val decades: List<Int> = emptyList(),
  val minRating: Double = 0.0,
) {
  val active: Int get() = genres.size + decades.size + if (minRating > 0) 1 else 0

  val isEmpty: Boolean get() = active == 0

  fun wire(): String = listOf(
    genres.joinToString("|") { it.replace(';', ' ').replace('|', ' ') },
    decades.joinToString("|"),
    if (minRating > 0) minRating.toString() else "",
  ).joinToString(";")

  companion object {
    fun of(wire: String): Facets {
      val parts = wire.split(';')
      if (parts.size < 3) return Facets()
      return Facets(
        genres = parts[0].split('|').filter { it.isNotEmpty() },
        decades = parts[1].split('|').mapNotNull { it.toIntOrNull() },
        minRating = parts[2].toDoubleOrNull() ?: 0.0,
      )
    }
  }
}

data class BrowseSql(val sql: String, val args: List<Any?>) {
  fun asRoomQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

data class BrowseScope(
  val sourceId: String,
  val kind: MediaKind,
  val categoryId: String? = null,
  val language: String = "",
  val facets: Facets = Facets(),
  val sort: SortKey = SortKey.PROVIDER,
)

private fun BrowseScope.from(): String =
  if (categoryId == null) {
    "media_item i"
  } else {
    "media_item i JOIN media_category m ON m.sourceId = i.sourceId AND m.itemId = i.id"
  }

private fun BrowseScope.where(): Pair<String, List<Any?>> {
  val clauses = mutableListOf<String>()
  val args = mutableListOf<Any?>()

  clauses += "i.sourceId = ?"
  args += sourceId
  if (categoryId == null) {
    clauses += "i.kind = ?"
    args += kind.wire
  } else {
    clauses += "m.kind = ?"
    args += kind.wire
    clauses += "m.categoryId = ?"
    args += categoryId
  }

  languageFilter(language, clauses, args)

  if (facets.genres.isNotEmpty()) {
    clauses += facets.genres.joinToString(" OR ", prefix = "(", postfix = ")") { "i.genreTags LIKE ?" }
    facets.genres.forEach { args += "%|$it|%" }
  }

  if (facets.decades.isNotEmpty()) {
    clauses += facets.decades.joinToString(" OR ", prefix = "(", postfix = ")") { "(i.year >= ? AND i.year < ?)" }
    facets.decades.forEach { args += it; args += it + 10 }
  }

  if (facets.minRating > 0) {
    clauses += "i.rating >= ?"
    args += facets.minRating
  }

  return clauses.joinToString(" AND ") to args
}

internal fun languageFilter(language: String, clauses: MutableList<String>, args: MutableList<Any?>) {
  when {
    language.isEmpty() -> Unit
    language == UNKNOWN_LANGUAGE -> clauses += "i.languageTags IS NULL"
    else -> {
      clauses += "i.languageTags LIKE ?"
      args += "%|$language|%"
    }
  }
}

private fun BrowseScope.order(): String = when (sort) {
  SortKey.PROVIDER -> if (categoryId == null) "i.ordinal" else "m.ordinal"
  SortKey.NAME -> if (categoryId == null) "i.sortTitle" else "m.sortTitle"
  SortKey.ADDED -> "i.addedAt DESC"
  SortKey.RATING -> "i.rating DESC"
  SortKey.YEAR -> "i.year DESC"
}

fun browseQuery(scope: BrowseScope): BrowseSql {
  val (where, args) = scope.where()
  return BrowseSql("SELECT i.* FROM ${scope.from()} WHERE $where ORDER BY ${scope.order()}", args)
}

fun browseCountQuery(scope: BrowseScope): BrowseSql {
  val (where, args) = scope.where()
  return BrowseSql("SELECT COUNT(*) FROM ${scope.from()} WHERE $where", args)
}

fun facetSampleQuery(scope: BrowseScope, limit: Int): BrowseSql {
  val bare = scope.copy(facets = Facets(), sort = SortKey.PROVIDER)
  val (where, args) = bare.where()
  return BrowseSql(
    "SELECT i.genreTags AS genreTags, i.year AS year FROM ${bare.from()} " +
      "WHERE $where ORDER BY ${bare.order()} LIMIT ?",
    args + limit,
  )
}

data class FacetRow(val genreTags: String?, val year: Int?)

data class FacetOptions(
  val genres: List<String> = emptyList(),
  val decades: List<Int> = emptyList(),
  val sampled: Boolean = false,
)

fun deriveFacets(rows: List<FacetRow>): FacetOptions {
  val counts = HashMap<String, Int>()
  val decades = HashSet<Int>()
  for (row in rows) {
    for (name in genresOf(row.genreTags)) counts[name] = (counts[name] ?: 0) + 1
    row.year?.takeIf { it > 1000 }?.let { decades += (it / 10) * 10 }
  }
  return FacetOptions(
    sampled = true,
    genres = counts.entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .take(24).map { it.key },
    decades = decades.sortedDescending().take(8),
  )
}

fun refine(items: List<MediaItem>, facets: Facets, sort: SortKey): List<MediaItem> {
  val kept = if (facets.isEmpty) {
    items
  } else {
    items.filter { item ->
      (facets.genres.isEmpty() || facets.genres.any { item.genreTags?.contains("|$it|", ignoreCase = true) == true }) &&
        (facets.decades.isEmpty() || item.year?.let { year -> facets.decades.any { year >= it && year < it + 10 } } == true) &&
        (facets.minRating <= 0 || (item.rating ?: Double.NEGATIVE_INFINITY) >= facets.minRating)
    }
  }
  return when (sort) {
    SortKey.PROVIDER -> kept
    SortKey.NAME -> kept.sortedBy { it.sortTitle }
    SortKey.ADDED -> kept.sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
    SortKey.RATING -> kept.sortedByDescending { it.rating ?: Double.NEGATIVE_INFINITY }
    SortKey.YEAR -> kept.sortedByDescending { it.year ?: Int.MIN_VALUE }
  }
}

fun facetOptionsOf(items: List<MediaItem>): FacetOptions =
  deriveFacets(items.map { FacetRow(it.genreTags, it.year) })
