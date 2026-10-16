package com.keeltv.app.ui.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.keeltv.app.ui.components.artlessWash
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.OptIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.R
import com.keeltv.app.core.LANGUAGE_CODES
import com.keeltv.app.core.LANGUAGE_FILTER_CHOICES
import com.keeltv.app.core.LANGUAGE_OPTIONS
import com.keeltv.app.data.model.EpgFeed
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceStats
import com.keeltv.app.data.model.SyncPhase
import com.keeltv.app.data.repo.SourceDraft
import com.keeltv.app.data.repo.toDraft
import com.keeltv.app.data.repo.toSource
import com.keeltv.app.playback.CaptionAppearance
import com.keeltv.app.playback.CaptionMode
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.components.TvTextField
import com.keeltv.app.ui.misc.ChoiceRow
import com.keeltv.app.ui.misc.Note
import com.keeltv.app.ui.misc.RowLabel
import com.keeltv.app.ui.misc.SpecRow
import com.keeltv.app.ui.misc.ToggleRow
import com.keeltv.app.ui.misc.TvChip
import com.keeltv.app.ui.misc.TvProgressBar
import com.keeltv.app.ui.movies.LanguagePicker
import com.keeltv.app.ui.theme.KeelTvTheme
import com.keeltv.app.update.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP: DateTimeFormatter =
  DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

private fun stamp(unixSeconds: Long?): String =
  if (unixSeconds == null || unixSeconds <= 0) "Never" else STAMP.format(Instant.ofEpochSecond(unixSeconds))

private fun tally(value: Int): String = "%,d".format(value)

private fun isUrl(value: String): Boolean =
  value.startsWith("http://") || value.startsWith("https://")

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(KeelTvTheme.radii.panel))
      .background(KeelTvTheme.colors.bgSurface)
      .padding(KeelTvTheme.space.s4),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    content()
  }
}

@Composable
private fun rememberStats(sourceId: String?, phase: SyncPhase): SourceStats? {
  val container = LocalContainer.current
  var stats by remember { mutableStateOf<SourceStats?>(null) }
  LaunchedEffect(sourceId, phase) {
    stats = sourceId?.let { runCatching { container.catalog.stats(it) }.getOrNull() }
  }
  return stats
}

@Composable
fun SourcesPane() {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val active = LocalActiveSource.current
  val sources by container.sources.sources.collectAsStateWithLifecycle(initialValue = emptyList())
  val sync by container.refresh.progress.collectAsStateWithLifecycle()
  val stats = rememberStats(active?.id, sync.phase)

  var editing by remember { mutableStateOf<String?>(null) }
  var confirming by remember { mutableStateOf<String?>(null) }
  var account by remember { mutableStateOf<Pair<String, String>?>(null) }
  var checking by remember { mutableStateOf(false) }
  var refreshing by remember { mutableStateOf(false) }

  PaneTitle("Provider")

  if (sources.isEmpty()) {
    Note(
      "No sources yet. Keel TV ships no catalogue of its own. Add an Xtream account or an M3U " +
        "playlist and everything else in the app fills in.",
    )
  }

  sources.forEach { source ->
    Panel {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
      ) {
        Text(source.name, style = KeelTvTheme.type.sub, color = KeelTvTheme.colors.textPrimary)
        Text(
          text = if (source.kind == SourceKind.XTREAM) "Xtream Codes" else "M3U playlist",
          style = KeelTvTheme.type.micro,
          color = KeelTvTheme.colors.textTertiary,
        )
        if (source.id == active?.id) {
          Text("In use", style = KeelTvTheme.type.micro, color = KeelTvTheme.colors.accent)
        }
      }
      Text(
        text = source.url,
        style = KeelTvTheme.type.micro,
        color = KeelTvTheme.colors.textTertiary,
        maxLines = 1,
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = KeelTvTheme.space.s1),
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
      ) {
        if (source.id != active?.id) {
          TvButton("Use this source", { scope.launch { container.sources.setActive(source.id) } })
        }
        TvButton(if (editing == source.id) "Close" else "Edit", {
          editing = if (editing == source.id) null else source.id
          confirming = null
        })
        TvButton(if (checking) "Checking" else "Check account", {
          checking = true
          account = null
          scope.launch {
            account = source.id to describeAccount(container, source)
            checking = false
          }
        })
        Spacer(Modifier.weight(1f))
        TvButton("Remove", { confirming = source.id; editing = null }, tone = ButtonTone.Danger)
      }

      val checked = account
      if (checked != null && checked.first == source.id) {
        Note(checked.second, tone = KeelTvTheme.colors.textSecondary)
      }

      if (confirming == source.id) {
        Note("Remove ${source.name}? Its catalogue and guide go with it.")
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
        ) {
          TvButton(
            text = "Remove",
            tone = ButtonTone.Danger,
            modifier = Modifier.weight(1f),
            onClick = {
              confirming = null
              scope.launch { container.sources.remove(source.id) }
            },
          )
          TvButton("Cancel", { confirming = null }, tone = ButtonTone.Plain, modifier = Modifier.weight(1f))
        }
      }
    }

    if (editing == source.id) {
      SourceForm(
        initial = source.toDraft(),
        submitLabel = "Save changes",
        onCancel = { editing = null },
        onSubmit = { draft ->
          container.sources.update(draft.toSource(source.id, source.createdAt))
          editing = null
        },
      )
    }
  }

  if (editing == "new") {
    SourceForm(
      initial = SourceDraft(),
      submitLabel = "Add source",
      showName = false,
      onCancel = { editing = null },
      onSubmit = { draft ->
        val added = container.sources.add(draft)
        editing = null
        scope.launch {
          refreshing = true
          runCatching { container.refresh.refresh(added.id) }
          refreshing = false
        }
      },
    )
  } else {
    TvButton("Add a source", { editing = "new"; confirming = null }, tone = ButtonTone.Ghost)
  }

  Panel {
    RowLabel("Catalogue")
    SpecRow("Channel categories", stats?.let { tally(it.liveCategories) } ?: "—")
    SpecRow("Movie categories", stats?.let { tally(it.movieCategories) } ?: "—")
    SpecRow("Series categories", stats?.let { tally(it.seriesCategories) } ?: "—")
    SpecRow("Channels", stats?.let { tally(it.liveItems) } ?: "—")
    SpecRow("Movies", stats?.let { tally(it.movieItems) } ?: "—")
    SpecRow("Series", stats?.let { tally(it.seriesItems) } ?: "—")
    SpecRow("Synced", stamp(stats?.lastSync))
  }

  TvButton(
    text = if (refreshing) "Refreshing" else "Refresh catalogue now",
    enabled = active != null && !refreshing,
    tone = ButtonTone.Ghost,
    onClick = {
      val id = active?.id ?: return@TvButton
      refreshing = true
      scope.launch {
        runCatching { container.refresh.refresh(id) }
        refreshing = false
      }
    },
  )

  if (refreshing || (sync.phase != SyncPhase.IDLE && sync.phase != SyncPhase.DONE)) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      TvProgressBar(sync.progress)
      Text(
        text = sync.error ?: sync.message.ifEmpty { "Working" },
        style = KeelTvTheme.type.micro,
        color = if (sync.error != null) KeelTvTheme.colors.danger else KeelTvTheme.colors.textTertiary,
      )
    }
  }
}

private suspend fun describeAccount(
  container: com.keeltv.app.AppContainer,
  source: Source,
): String = withContext(Dispatchers.IO) {
  if (source.kind != SourceKind.XTREAM) {
    return@withContext container.sources.test(source.toDraft()).message
  }
  val client = container.sources.clientFor(source) ?: return@withContext "No client for that source."
  val auth = runCatching { client.auth() }.getOrElse { return@withContext it.message ?: "Could not reach the provider." }
  listOfNotNull(
    auth.message,
    "Status: ${auth.status}",
    auth.expiresAtSeconds?.let { "Expires ${stamp(it)}" } ?: "No expiry date",
    "Max connections: ${auth.maxConnections}",
  ).joinToString(" · ")
}

@Composable
fun GuidePane() {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val source = LocalActiveSource.current
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
  val sync by container.refresh.progress.collectAsStateWithLifecycle()
  val stats = rememberStats(source?.id, sync.phase)
  val status by remember(source?.id) {
    container.epg.feedStatus(source?.id.orEmpty())
  }.collectAsStateWithLifecycle(initialValue = emptyList())

  var catalogue by remember { mutableStateOf<List<EpgFeed>>(emptyList()) }
  var autoIds by remember { mutableStateOf<List<String>>(emptyList()) }
  var showAll by remember { mutableStateOf(false) }
  var customUrl by remember { mutableStateOf("") }
  var refreshing by remember { mutableStateOf(false) }

  LaunchedEffect(source?.id) {
    val id = source?.id
    if (id == null) return@LaunchedEffect
    val (feeds, auto) = runCatching { container.epg.feedCatalogue(id) }
      .getOrDefault(emptyList<EpgFeed>() to emptyList())
    catalogue = feeds
    autoIds = auto
  }

  val current = settings ?: return
  fun isOn(id: String): Boolean =
    id !in current.epgFillDisabled &&
      (id in current.epgFillEnabled || (current.epgFillAuto && id in autoIds))

  val known = catalogue.map { it.id }.toSet()
  val custom = current.epgFillEnabled.filter { it !in known }
  val shown = if (showAll) catalogue else catalogue.filter { isOn(it.id) || it.id in autoIds }
  val on = catalogue.count { isOn(it.id) } + custom.size

  PaneTitle("Guide")
  Note(
    "The provider's guide is read first. Public guides then fill in channels it shipped without " +
      "listings; a channel that already has listings is never overwritten. Listings are kept from " +
      "three hours ago to two days ahead, which is what a box has room for, and as far back as " +
      "the archive reaches on a channel that keeps catch-up.",
  )

  Panel {
    SpecRow("Channels", stats?.let { tally(it.epgChannels) } ?: "—")
    SpecRow("Programmes", stats?.let { tally(it.epgProgrammes) } ?: "—")
    SpecRow("Last sync", stamp(stats?.lastSync))
  }

  ToggleRow(
    title = "Pick guides from channel names",
    help = "Reads the country tags on your channels (AU:, US |, UK ★) and ticks the four biggest " +
      "matching public guides, plus the sports ones. Tick more by hand below.",
    checked = current.epgFillAuto,
    onToggle = { value -> scope.launch { container.settings.setEpgFillAuto(value) } },
    modifier = Modifier.fillMaxWidth(),
  )

  ToggleRow(
    title = "Collapse duplicates",
    help = "Folds the HD, FHD and 4K feeds of one channel onto a single row of the Live TV list. " +
      "Right on the row steps through them.",
    checked = current.collapseDuplicates,
    onToggle = { value -> scope.launch { container.settings.setCollapseDuplicates(value) } },
    modifier = Modifier.fillMaxWidth(),
  )

  ChoiceRow(
    title = "Automatic refresh",
    help = "How stale the guide may get before Live TV reads XMLTV again.",
    value = current.epgAutoRefreshHours.toString(),
    options = listOf("0" to "Off", "6" to "6h", "12" to "12h", "24" to "24h"),
    onChange = { value -> scope.launch { container.settings.setEpgAutoRefreshHours(value.toInt()) } },
    modifier = Modifier.fillMaxWidth(),
  )

  TvButton(
    text = if (refreshing || sync.phase == SyncPhase.EPG) "Refreshing" else "Refresh guide now",
    enabled = source != null && !refreshing && sync.phase == SyncPhase.IDLE,
    tone = ButtonTone.Ghost,
    onClick = {
      val id = source?.id ?: return@TvButton
      refreshing = true
      scope.launch {
        runCatching { container.refresh.refreshEpg(id) }
        refreshing = false
      }
    },
  )

  if (refreshing || sync.phase == SyncPhase.EPG) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      TvProgressBar(sync.progress)
      Text(
        text = sync.error ?: sync.message.ifEmpty { "Contacting the guide server" },
        style = KeelTvTheme.type.micro,
        color = if (sync.error != null) KeelTvTheme.colors.danger else KeelTvTheme.colors.textTertiary,
      )
    }
  }

  TvTextField(
    label = "Add your own XMLTV guide",
    value = customUrl,
    onValueChange = { customUrl = it },
    placeholder = "https://example.com/guide.xml.gz",
    keyboardType = KeyboardType.Uri,
    modifier = Modifier.fillMaxWidth(),
  )
  Note("Plain, gzip, brotli or deflate XMLTV. It fills only channels no earlier guide covers.")
  TvButton(
    text = "Add guide",
    enabled = isUrl(customUrl.trim()) && customUrl.trim() !in current.epgFillEnabled,
    onClick = {
      val url = customUrl.trim()
      customUrl = ""
      scope.launch { container.settings.setFeedEnabled(url, true) }
    },
  )

  Note(
    if (on == 0) {
      "No extra guides. Only the provider feed is read."
    } else {
      "$on extra ${if (on == 1) "guide" else "guides"} fill channels the provider leaves blank."
    },
  )

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    (shown.map { it.id to it.label } + custom.map { it to it })
      .chunked(2)
      .forEach { chunk ->
        Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
          chunk.forEach { (id, label) ->
            TvChip(
              label = label,
              selected = isOn(id),
              onClick = { scope.launch { container.settings.setFeedEnabled(id, !isOn(id)) } },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    if (catalogue.size > shown.size || showAll) {
      TvChip(
        label = if (showAll) "Fewer" else "${catalogue.size - shown.size} more countries",
        selected = false,
        onClick = { showAll = !showAll },
      )
    }
  }

  if (status.isNotEmpty()) {
    Panel {
      RowLabel("Feed status")
      status.forEach { feed ->
        SpecRow(
          label = feed.label,
          value = feed.error ?: "${tally(feed.channels)} ch · ${tally(feed.programmes)} prog",
          bad = feed.error != null,
        )
      }
    }
  }
}

@Composable
fun PlaybackPane() {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val source = LocalActiveSource.current
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
  val watching by remember(source?.id) { container.library.continueWatching(source?.id.orEmpty()) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val current = settings ?: return
  val chosen = CaptionAppearance(current.captionSize, current.captionColor, current.captionBackground)
  var trying by remember { mutableStateOf<CaptionAppearance?>(null) }

  PaneTitle("Playback")
  CaptionPreview(trying ?: chosen, picture = watching.firstOrNull { !it.image.isNullOrBlank() }?.image)
  Spacer(Modifier.height(KeelTvTheme.space.s1))

  ChoiceRow(
    title = "Captions",
    help = "Automatic shows matching forced subtitles.",
    value = current.captionMode,
    options = listOf(
      CaptionMode.OFF to "Off",
      CaptionMode.AUTOMATIC to "Automatic",
      CaptionMode.ON to "On",
    ),
    onChange = { value -> scope.launch { container.settings.setCaptionMode(value) } },
    modifier = Modifier.fillMaxWidth(),
  )

  ChoiceRow(
    title = "Size",
    value = current.captionSize.toString(),
    options = listOf("75" to "Small", "100" to "Normal", "125" to "Large", "150" to "Larger", "200" to "Largest"),
    onChange = { value -> scope.launch { container.settings.setCaptionSize(value.toInt()) } },
    onFocusOption = { key -> trying = key?.let { chosen.copy(size = it.toInt()) } },
    modifier = Modifier.fillMaxWidth(),
  )

  ChoiceRow(
    title = "Colour",
    value = current.captionColor,
    options = listOf(CaptionAppearance.WHITE to "White", CaptionAppearance.YELLOW to "Yellow"),
    onChange = { value -> scope.launch { container.settings.setCaptionColor(value) } },
    onFocusOption = { key -> trying = key?.let { chosen.copy(color = it) } },
    modifier = Modifier.fillMaxWidth(),
  )

  ChoiceRow(
    title = "Background",
    help = "The box behind the words.",
    value = String.format(Locale.ROOT, "%.1f", current.captionBackground),
    options = listOf("0.0" to "None", "0.4" to "Light", "0.6" to "Normal", "0.9" to "Solid"),
    onChange = { value -> scope.launch { container.settings.setCaptionBackground(value.toFloat()) } },
    onFocusOption = { key -> trying = key?.let { chosen.copy(background = it.toFloat()) } },
    modifier = Modifier.fillMaxWidth(),
  )

  Note(
    "These apply to every title. Picking a track in the player changes that title only. The " +
      "languages to prefer are under Language.",
  )
}

@Composable
private fun CaptionPreview(appearance: CaptionAppearance, picture: String?) {
  val size = (16 * appearance.size / 100f)
  Box(
    Modifier
      .fillMaxWidth()
      .height(150.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(artlessWash("captions")),
  ) {
    if (picture != null) {
      AsyncImage(
        model = picture,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
    Text(
      text = "This is how captions will look.",
      style = TextStyle(
        fontSize = size.sp,
        lineHeight = (size * 1.35f).sp,
        fontWeight = FontWeight.Medium,
        shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 4f),
      ),
      color = if (appearance.color == CaptionAppearance.YELLOW) Color(0xFFFFE14D) else Color.White,
      maxLines = 1,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 16.dp)
        .background(Color.Black.copy(alpha = appearance.background.coerceIn(0f, 1f)), RoundedCornerShape(3.dp))
        .padding(horizontal = 9.dp, vertical = 1.dp),
    )
  }
}

@Composable
fun LanguagePane() {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
  val current = settings ?: return
  var picking by remember { mutableStateOf<String?>(null) }

  PaneTitle("Language")
  PickerRow("Audio language", trackLanguageName(current.audioLanguage)) { picking = AUDIO }
  PickerRow("Caption language", trackLanguageName(current.captionLanguage)) { picking = CAPTIONS }
  Note("Source default plays whatever the stream starts with. Captions follow the audio when left on it.")

  Spacer(Modifier.height(KeelTvTheme.space.s2))
  Note("The language filter is remembered across live TV, browse and search.")

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    LANGUAGE_FILTER_CHOICES.chunked(2).forEach { chunk ->
      Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
        chunk.forEach { (value, label) ->
          TvChip(
            label = label,
            selected = value == current.languageFilter,
            onClick = { scope.launch { container.settings.setLanguageFilter(value) } },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }


  val which = picking
  if (which != null) {
    LanguagePicker(
      title = if (which == AUDIO) "Audio language" else "Caption language",
      selected = if (which == AUDIO) current.audioLanguage else current.captionLanguage,
      options = TRACK_LANGUAGES,
      onPick = { code ->
        picking = null
        scope.launch {
          if (which == AUDIO) container.settings.setAudioLanguage(code)
          else container.settings.setCaptionLanguage(code)
        }
      },
      onDismiss = { picking = null },
    )
  }
}

private const val AUDIO = "audio"
private const val CAPTIONS = "captions"

private val TRACK_LANGUAGES: List<Pair<String, String>> =
  listOf("" to "Source default") +
    LANGUAGE_OPTIONS
      .mapNotNull { label -> LANGUAGE_CODES[label]?.lowercase(Locale.ROOT)?.takeIf { it.length <= 3 }?.let { it to label } }
      .filter { (code, _) -> code != "lat" }

private fun trackLanguageName(code: String): String =
  if (code.isEmpty()) "Source default" else TRACK_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

@Composable
private fun PickerRow(title: String, value: String, onClick: () -> Unit) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = onClick,
    resting = colors.bgSurface,
    shape = RoundedCornerShape(KeelTvTheme.radii.panel),
    modifier = Modifier.fillMaxWidth(),
  ) { focused ->
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = KeelTvTheme.space.s3, vertical = KeelTvTheme.space.s3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = KeelTvTheme.type.control.copy(fontSize = KeelTvTheme.type.body.fontSize),
        color = if (focused) colors.onFocus else colors.textPrimary,
        modifier = Modifier.weight(1f),
      )
      Text(text = value, style = KeelTvTheme.type.control, color = if (focused) colors.onFocusMuted else colors.textSecondary, maxLines = 1)
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = if (focused) colors.onFocusMuted else colors.textTertiary,
        modifier = Modifier.padding(start = KeelTvTheme.space.s1).size(18.dp),
      )
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
fun AboutPane() {
  val context = LocalContext.current
  val version = remember {
    runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"
  }
  val ffmpeg = remember { runCatching { FfmpegLibrary.getVersion() }.getOrNull() }
  val updater = LocalContainer.current.updater
  val scope = rememberCoroutineScope()
  var release by remember { mutableStateOf<Release?>(null) }
  var updateNote by remember { mutableStateOf("Checking for updates.") }
  var downloading by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    runCatching { updater.check() }
      .onSuccess {
        release = it
        updateNote = if (it == null) "This is the latest build." else "Keel TV ${it.versionName} is ready to install."
      }
      .onFailure { updateNote = "Could not check for updates." }
  }

  PaneTitle("About")
  // Side by side, because the pane only scrolls as focus moves and nothing here takes focus: a
  // card stacked underneath would sit off screen where the remote can never reach it.
  Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s5)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3)) {
      Panel {
        SpecRow("Keel TV", version)
        SpecRow("Android", Build.VERSION.RELEASE)
        SpecRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        SpecRow("Audio decoder", ffmpeg?.let { "FFmpeg $it" } ?: "This device's own")
      }
      release?.let { newer ->
        TvButton(
          text = if (downloading) "Downloading" else "Update to ${newer.versionName}",
          tone = ButtonTone.Primary,
          onClick = {
            if (!downloading) {
              downloading = true
              scope.launch {
                runCatching { updater.install() }
                  .onFailure { updateNote = "The update failed. Try again." }
                downloading = false
              }
            }
          },
        )
      }
      Note(updateNote)
      Note(
        "Keel TV plays what your provider ships and nothing else. There is no account, no telemetry " +
          "and no catalogue of its own. The source list is the whole product.",
      )
      if (ffmpeg != null) {
        Note(
          "Audio your TV cannot decode on its own, such as MP2, AC-3 and DTS, is decoded by FFmpeg, " +
            "used unmodified under the LGPL v2.1.",
        )
      }
      Note(
        "Some film and series artwork is loaded from TMDB. Keel TV is not endorsed, certified or " +
          "otherwise approved by TMDB.",
      )
    }
    SupportCard()
  }
}

@Composable
private fun SupportCard() {
  val colors = KeelTvTheme.colors
  Column(
    modifier = Modifier
      .width(216.dp)
      .clip(RoundedCornerShape(KeelTvTheme.radii.panel))
      .background(colors.bgSurface)
      .padding(KeelTvTheme.space.s4),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    Image(
      painter = painterResource(R.drawable.support_qr),
      contentDescription = "QR code for buymeacoffee.com/futsy",
      modifier = Modifier
        .size(152.dp)
        .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
        .background(Color.White)
        .padding(10.dp),
    )
    Spacer(Modifier.height(KeelTvTheme.space.s1))
    Text("Support Keel TV", style = KeelTvTheme.type.heading, color = colors.textPrimary)
    Text(
      "Keel TV is free. If it has earned a place on your TV, scan to buy me a coffee and help keep " +
        "the updates coming.",
      style = KeelTvTheme.type.small,
      color = colors.textSecondary,
      textAlign = TextAlign.Center,
    )
    Text("buymeacoffee.com/futsy", style = KeelTvTheme.type.micro, color = colors.accent)
  }
}
