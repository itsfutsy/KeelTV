package com.keeltv.app.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Some providers gate their API and streams on a player-ish User-Agent. VLC is universally allowed. */
const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
  .connectTimeout(20, TimeUnit.SECONDS)
  .readTimeout(60, TimeUnit.SECONDS)
  .writeTimeout(20, TimeUnit.SECONDS)
  .callTimeout(0, TimeUnit.MILLISECONDS)
  .followRedirects(true)
  .followSslRedirects(true)
  .retryOnConnectionFailure(true)
  .build()
