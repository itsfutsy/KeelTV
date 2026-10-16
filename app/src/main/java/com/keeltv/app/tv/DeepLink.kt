package com.keeltv.app.tv

import android.net.Uri

/**
 * The links that come back into the app from outside it: the Google TV home row, and anything
 * else that can open a `keeltv://` url.
 *
 * `keeltv://play?item=movie:1234&episode=&at=903000` resumes where the row says it left off, and
 * `keeltv://search?q=heat` opens Search with the query in the field.
 */
object DeepLink {
  const val SCHEME = "keeltv"

  fun play(itemId: String, episodeId: String, atMs: Long): String =
    Uri.Builder().scheme(SCHEME).authority("play")
      .appendQueryParameter("item", itemId)
      .appendQueryParameter("episode", episodeId)
      .appendQueryParameter("at", atMs.toString())
      .build().toString()

  fun search(query: String): String =
    Uri.Builder().scheme(SCHEME).authority("search")
      .appendQueryParameter("q", query)
      .build().toString()
}
