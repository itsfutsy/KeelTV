package com.keeltv.app.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay

private const val MISS_MS = 2_500L

@Composable
fun voiceSearchAvailable(): Boolean {
  val context = LocalContext.current
  return remember { SpeechRecognizer.isRecognitionAvailable(context) }
}

@Composable
fun VoiceButton(
  onText: (String) -> Unit,
  modifier: Modifier = Modifier,
  onHint: (String?) -> Unit = {},
  onLight: Boolean = false,
) {
  val context = LocalContext.current
  if (!voiceSearchAvailable()) return

  val colors = KeelTvTheme.colors
  val latestText by rememberUpdatedState(onText)
  var listening by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  // The recogniser reports loudness in dB over roughly -2 to 10, which is the mic's pulse.
  var level by remember { mutableFloatStateOf(0f) }

  val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
  val request = remember {
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
  }

  DisposableEffect(recognizer) {
    recognizer.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) { listening = true }
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() { level = 0f }
      override fun onEvent(eventType: Int, params: Bundle?) {}

      override fun onError(error: Int) {
        listening = false
        level = 0f
        failure = when (error) {
          SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing heard. Try again."
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic blocked"
          SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No connection"
          SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> null
          else -> "Try again"
        }
      }

      override fun onResults(results: Bundle?) {
        listening = false
        level = 0f
        val said = results.firstSpoken()
        if (said == null) failure = "Nothing heard. Try again." else latestText(said)
      }

      override fun onPartialResults(partial: Bundle?) {
        partial.firstSpoken()?.let(latestText)
      }
    })
    onDispose {
      recognizer.destroy()
    }
  }

  val hint = if (listening) "Listening" else failure
  LaunchedEffect(hint) { onHint(hint) }
  LaunchedEffect(failure) {
    if (failure != null) {
      delay(MISS_MS)
      failure = null
    }
  }

  fun listen() {
    failure = null
    listening = true
    latestText("")
    recognizer.startListening(request)
  }

  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) listen() else failure = "Mic blocked"
  }

  val pulse by animateFloatAsState(
    targetValue = if (listening) 1f + level * 0.35f else 1f,
    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
    label = "micPulse",
  )

  FocusRow(
    onClick = {
      when {
        listening -> recognizer.stopListening()
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED -> listen()
        else -> permission.launch(Manifest.permission.RECORD_AUDIO)
      }
    },
    shape = CircleShape,
    modifier = modifier.size(26.dp),
  ) { focused ->
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Outlined.Mic,
        contentDescription = "Search by voice",
        tint = when {
          listening -> colors.accent
          focused -> colors.onFocus
          onLight -> colors.onFocusMuted
          else -> colors.textTertiary
        },
        modifier = Modifier.size(16.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
      )
    }
  }
}

private fun Bundle?.firstSpoken(): String? =
  this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    ?.firstOrNull()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
