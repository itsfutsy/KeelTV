package com.keeltv.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.keeltv.app.ui.AppIntents
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.KeelTvRoot
import com.keeltv.app.ui.theme.KeelTvTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    AppIntents.parse(intent)?.let(AppIntents::offer)

    val container = (application as KeelTvApp).container
    setContent {
      CompositionLocalProvider(LocalContainer provides container) {
        KeelTvTheme { KeelTvRoot() }
      }
    }
    window.setBackgroundDrawable(null)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    AppIntents.parse(intent)?.let(AppIntents::offer)
  }
}
