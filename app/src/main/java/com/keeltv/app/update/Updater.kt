package com.keeltv.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.keeltv.app.BuildConfig
import com.keeltv.app.data.net.long
import com.keeltv.app.data.net.openStream
import com.keeltv.app.data.net.readObject
import com.keeltv.app.data.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

private val RELEASES = BuildConfig.RELEASES_REPO?.let { "https://github.com/$it/releases/latest/download" }

data class Release(val versionCode: Long, val versionName: String)

class Updater(private val context: Context, private val http: OkHttpClient) {
  suspend fun check(): Release? = withContext(Dispatchers.IO) {
    val releases = RELEASES ?: return@withContext null
    val note = http.openStream("$releases/keeltv.json").use { readObject(it.source) }
    val code = note?.long("versionCode") ?: throw IOException("The release note has no version.")
    val installed = PackageInfoCompat.getLongVersionCode(
      context.packageManager.getPackageInfo(context.packageName, 0),
    )
    if (code > installed) Release(code, note.str("versionName") ?: "build $code") else null
  }

  suspend fun install() {
    val releases = RELEASES ?: return
    val apk = File(context.cacheDir, "updates/keeltv.apk")
    withContext(Dispatchers.IO) {
      apk.parentFile?.mkdirs()
      http.openStream("$releases/keeltv.apk").use { download ->
        apk.sink().buffer().use { it.writeAll(download.source) }
      }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    context.startActivity(
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
}
