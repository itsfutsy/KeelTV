package com.keeltv.app.core

fun splitGenres(genre: String?): List<String> =
  genre?.split(',', '/', '|')?.map { it.trim() }?.filter { it.length > 1 } ?: emptyList()

fun humanGenre(input: String): String {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed != trimmed.uppercase()) return trimmed
  return trimmed.lowercase().split(' ').joinToString(" ") { word ->
    word.replaceFirstChar { it.uppercase() }
  }
}

fun genreTags(genre: String?): String? {
  val names = splitGenres(genre).map(::humanGenre).filter { it.isNotEmpty() }.distinct()
  if (names.isEmpty()) return null
  return names.joinToString("|", prefix = "|", postfix = "|")
}

fun genresOf(tags: String?): List<String> =
  tags?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
