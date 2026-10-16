package com.keeltv.app

import android.content.Context
import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.net.buildHttpClient
import com.keeltv.app.data.prefs.SettingsStore
import com.keeltv.app.data.repo.CatalogRepository
import com.keeltv.app.data.repo.EpgIngest
import com.keeltv.app.data.repo.EpgRepository
import com.keeltv.app.data.repo.LibraryRepository
import com.keeltv.app.data.repo.RefreshService
import com.keeltv.app.data.repo.SourceRepository
import com.keeltv.app.tv.WatchNext
import com.keeltv.app.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
  val http: OkHttpClient by lazy { buildHttpClient() }
  val db: KeelTvDatabase by lazy { KeelTvDatabase.open(context) }
  val settings: SettingsStore by lazy { SettingsStore(context) }

  val sources: SourceRepository by lazy { SourceRepository(db, settings, http) }
  val catalog: CatalogRepository by lazy { CatalogRepository(db, sources) }
  private val epgIngest: EpgIngest by lazy { EpgIngest(db, http) }
  val epg: EpgRepository by lazy { EpgRepository(db, epgIngest) }
  val library: LibraryRepository by lazy { LibraryRepository(db, WatchNext(context)) }
  private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  val refresh: RefreshService by lazy { RefreshService(db, sources, settings, http, epgIngest, background) }
  val updater: Updater by lazy { Updater(context, http) }
}
