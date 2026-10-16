package com.keeltv.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceTest
import com.keeltv.app.data.model.SyncPhase
import com.keeltv.app.data.repo.SourceDraft
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.KeelLogo
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.SourceFields
import com.keeltv.app.ui.components.SourceTestResult
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.launch

private enum class Step { WELCOME, KIND, CONNECT, SYNC }

private val PHASES = listOf(
  SyncPhase.CATEGORIES to "Categories",
  SyncPhase.LIVE to "Channels",
  SyncPhase.MOVIES to "Movies",
  SyncPhase.SERIES to "Series",
  SyncPhase.EPG to "Guide",
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val sync by container.refresh.progress.collectAsStateWithLifecycle()

  var step by remember { mutableStateOf(Step.WELCOME) }
  var draft by remember { mutableStateOf(SourceDraft()) }
  var test by remember { mutableStateOf<SourceTest?>(null) }
  var testing by remember { mutableStateOf<String?>(null) }
  var adding by remember { mutableStateOf(false) }
  var added by remember { mutableStateOf<Source?>(null) }
  var failed by remember { mutableStateOf<String?>(null) }
  var finished by remember { mutableStateOf(false) }
  var guideStarted by remember { mutableStateOf(false) }
  val stepFocus = remember { FocusRequester() }

  LaunchedEffect(sync.phase) { if (sync.phase == SyncPhase.EPG) guideStarted = true }

  LaunchedEffect(step) { runCatching { stepFocus.requestFocus() } }

  Box(
    Modifier.fillMaxSize().background(KeelTvTheme.colors.bgApp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .width(560.dp)
        .verticalScroll(rememberScrollState())
        .padding(vertical = KeelTvTheme.space.s4),
    ) {
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
          .background(KeelTvTheme.colors.bgSurface)
          .padding(KeelTvTheme.space.s5)
          .focusRequester(stepFocus)
          .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
      ) {
        if (step != Step.WELCOME) StepRail(step)

        when (step) {
          Step.WELCOME -> Welcome(onStart = { step = Step.KIND })

          Step.KIND -> PickKind { kind ->
            draft = draft.copy(kind = kind)
            test = null
            step = Step.CONNECT
          }

          Step.CONNECT -> Connect(
            draft = draft,
            onDraft = { draft = it; test = null },
            test = test,
            testing = testing,
            adding = adding,
            onBack = { step = Step.KIND },
            onTest = {
              testing = "Testing"
              scope.launch {
                test = container.sources.test(draft) { testing = it }
                testing = null
              }
            },
            onAdd = {
              adding = true
              scope.launch {
                val source = container.sources.add(draft)
                added = source
                adding = false
                step = Step.SYNC
                failed = null
                runCatching { container.refresh.refresh(source.id) }
                  .onFailure { failed = it.message ?: "The refresh did not finish." }
                  .onSuccess { finished = true }
              }
            },
          )

          Step.SYNC -> Landing(
            name = added?.name ?: draft.name,
            phase = sync.phase,
            message = sync.message,
            failure = failed ?: sync.error,
            done = finished,
            guideDone = guideStarted && sync.phase == SyncPhase.DONE,
            onFinish = onDone,
            onEdit = { failed = null; step = Step.CONNECT },
          )
        }
      }
    }
  }
}

@Composable
private fun StepRail(step: Step) {
  val index = listOf(Step.KIND, Step.CONNECT, Step.SYNC).indexOf(step)
  Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s1)) {
    repeat(3) { i ->
      Box(
        Modifier
          .weight(1f)
          .height(3.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(if (i <= index) KeelTvTheme.colors.accent else KeelTvTheme.colors.bgActive),
      )
    }
  }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
  KeelLogo(Modifier.width(184.dp).height(46.dp))
  Spacer(Modifier.height(KeelTvTheme.space.s4))
  Text("Welcome to Keel TV", style = KeelTvTheme.type.micro, color = KeelTvTheme.colors.textSecondary)
  Text("Bring your own provider.", style = KeelTvTheme.type.display, color = KeelTvTheme.colors.textPrimary)
  Text(
    "Keel TV carries no catalogue of its own. Point it at your Xtream account or an M3U playlist " +
      "and it plays live TV, films and series, with the guide alongside them.",
    style = KeelTvTheme.type.body,
    color = KeelTvTheme.colors.textSecondary,
  )
  Spacer(Modifier.height(KeelTvTheme.space.s4))
  TvButton("Set up your provider", onStart, tone = ButtonTone.Primary)
}

@Composable
private fun PickKind(onPick: (SourceKind) -> Unit) {
  Text("How do you connect?", style = KeelTvTheme.type.title, color = KeelTvTheme.colors.textPrimary)
  Text(
    "Pick the kind of account your provider gave you.",
    style = KeelTvTheme.type.body,
    color = KeelTvTheme.colors.textSecondary,
  )
  Spacer(Modifier.height(KeelTvTheme.space.s3))

  val options = listOf(
    Triple(
      SourceKind.XTREAM,
      "Xtream Codes",
      "A server, a username and a password. Brings live TV, movies, series and the guide in one pass.",
    ),
    Triple(
      SourceKind.M3U,
      "M3U playlist",
      "A playlist URL, with an optional XMLTV guide.",
    ),
  )
  options.forEach { (kind, title, body) ->
    FocusRow(
      onClick = { onPick(kind) },
      resting = KeelTvTheme.colors.bgRaised,
      shape = RoundedCornerShape(KeelTvTheme.radii.panel),
      modifier = Modifier.fillMaxWidth().padding(bottom = KeelTvTheme.space.s2),
    ) { focused ->
      Column(
        modifier = Modifier.padding(KeelTvTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(title, style = KeelTvTheme.type.sub, color = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.textPrimary)
        Text(body, style = KeelTvTheme.type.small, color = if (focused) KeelTvTheme.colors.onFocusMuted else KeelTvTheme.colors.textSecondary)
      }
    }
  }
}

@Composable
private fun Connect(
  draft: SourceDraft,
  onDraft: (SourceDraft) -> Unit,
  test: SourceTest?,
  testing: String?,
  adding: Boolean,
  onBack: () -> Unit,
  onTest: () -> Unit,
  onAdd: () -> Unit,
) {
  val xtream = draft.kind == SourceKind.XTREAM
  Text(
    if (xtream) "Your Xtream account." else "Your playlist.",
    style = KeelTvTheme.type.title,
    color = KeelTvTheme.colors.textPrimary,
  )
  Text(
    if (xtream) "Type what your provider sent you. It is kept on this device."
    else "Point at a playlist URL. Add a guide if you have one.",
    style = KeelTvTheme.type.small,
    color = KeelTvTheme.colors.textSecondary,
  )

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
  ) {
    SourceFields(draft = draft, onDraft = onDraft, showName = false)
    SourceTestResult(test)
  }

  Spacer(Modifier.height(KeelTvTheme.space.s2))
  Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3)) {
    TvButton("Back", onBack, tone = ButtonTone.Plain)
    Spacer(Modifier.weight(1f))
    TvButton(
      text = testing ?: "Test connection",
      onClick = onTest,
      enabled = draft.complete && testing == null,
    )
    TvButton(
      text = if (adding) "Adding" else "Continue",
      onClick = onAdd,
      tone = ButtonTone.Primary,
      enabled = draft.complete && !adding,
    )
  }
}

@Composable
private fun Landing(
  name: String,
  phase: SyncPhase,
  message: String,
  failure: String?,
  done: Boolean,
  guideDone: Boolean,
  onFinish: () -> Unit,
  onEdit: () -> Unit,
) {
  Text(
    if (done) "Your catalogue is ready." else "Reading your provider.",
    style = KeelTvTheme.type.title,
    color = KeelTvTheme.colors.textPrimary,
  )
  Text(name, style = KeelTvTheme.type.small, color = KeelTvTheme.colors.textSecondary)
  Spacer(Modifier.height(KeelTvTheme.space.s3))

  val index = PHASES.indexOfFirst { it.first == phase }
  val states = PHASES.mapIndexed { i, (p, _) ->
    val guide = p == SyncPhase.EPG
    when {
      failure != null && (index == i || (guide && done)) -> "bad"
      guide -> if (guideDone) "done" else if (phase == SyncPhase.EPG) "busy" else "pending"
      done || (index >= 0 && i < index) -> "done"
      index == i -> "busy"
      else -> "pending"
    }
  }

  val reached = states.count { it == "done" } + if (states.any { it == "busy" }) 1 else 0
  val progress by animateFloatAsState(
    targetValue = reached.toFloat() / PHASES.size,
    animationSpec = tween(520),
    label = "syncProgress",
  )
  Box(
    Modifier
      .fillMaxWidth()
      .height(3.dp)
      .clip(RoundedCornerShape(2.dp))
      .background(KeelTvTheme.colors.bgActive),
  ) {
    Box(
      Modifier
        .fillMaxWidth(progress)
        .height(3.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(if (failure != null) KeelTvTheme.colors.danger else KeelTvTheme.colors.accent),
    )
  }
  Spacer(Modifier.height(KeelTvTheme.space.s2))

  PHASES.forEachIndexed { i, (_, label) ->
    val state = states[i]
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PhaseDot(state)
      Spacer(Modifier.width(KeelTvTheme.space.s3))
      Text(
        label,
        style = KeelTvTheme.type.small,
        color = when (state) {
          "done" -> KeelTvTheme.colors.textPrimary
          "busy" -> KeelTvTheme.colors.accent
          "bad" -> KeelTvTheme.colors.danger
          else -> KeelTvTheme.colors.textTertiary
        },
      )
      Spacer(Modifier.weight(1f))
      Text(
        when (state) {
          "done" -> "Done"
          "busy" -> "Reading"
          "bad" -> "Failed"
          else -> "Waiting"
        },
        style = KeelTvTheme.type.label,
        color = KeelTvTheme.colors.textTertiary,
      )
    }
  }

  if (failure != null) {
    Text(failure, style = KeelTvTheme.type.small, color = KeelTvTheme.colors.danger)
  } else if (message.isNotEmpty()) {
    Text(message, style = KeelTvTheme.type.micro, color = KeelTvTheme.colors.textTertiary)
  }
  if (done && !guideDone) {
    Text(
      "The guide keeps loading while you watch.",
      style = KeelTvTheme.type.micro,
      color = KeelTvTheme.colors.textTertiary,
    )
  }

  Spacer(Modifier.height(KeelTvTheme.space.s4))
  Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3)) {
    if (failure != null) {
      TvButton("Edit source", onEdit, tone = ButtonTone.Ghost)
    }
    TvButton(
      text = if (done) "Start watching" else "Reading the catalogue",
      onClick = onFinish,
      tone = ButtonTone.Primary,
      enabled = done,
    )
  }
}

@Composable
private fun PhaseDot(state: String) {
  val colors = KeelTvTheme.colors
  val pulse = rememberInfiniteTransition(label = "phaseDot")
  val beat by pulse.animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
    label = "beat",
  )
  val tint = when (state) {
    "done" -> colors.accent
    "busy" -> colors.accent
    "bad" -> colors.danger
    else -> colors.bgActive
  }
  Box(
    Modifier
      .size(8.dp)
      .graphicsLayer { alpha = if (state == "busy") beat else 1f }
      .clip(RoundedCornerShape(4.dp))
      .background(if (state == "pending") colors.bgActive else tint),
  )
}
