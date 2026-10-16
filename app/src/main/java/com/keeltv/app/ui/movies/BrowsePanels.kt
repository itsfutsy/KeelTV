package com.keeltv.app.ui.movies

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.core.LANGUAGE_FILTER_CHOICES
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.repo.FacetOptions
import com.keeltv.app.data.repo.Facets
import com.keeltv.app.data.repo.SortKey
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.misc.TvChip
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BrowseRequest {
  private val _pending = MutableStateFlow<Pair<MediaKind, String>?>(null)

  val pending: StateFlow<Pair<MediaKind, String>?> = _pending.asStateFlow()

  fun open(kind: MediaKind, genre: String) {
    _pending.value = kind to genre
  }

  fun take(request: Pair<MediaKind, String>) {
    _pending.compareAndSet(request, null)
  }
}

private val RATINGS = listOf(0.0 to "Any", 6.0 to "6+", 7.0 to "7+", 8.0 to "8+", 9.0 to "9+")

private const val NAMED_PICKS = 2

fun languageName(value: String): String =
  LANGUAGE_FILTER_CHOICES.firstOrNull { it.first == value }?.second ?: value

internal fun refinePicks(language: String, facets: Facets, sort: SortKey): List<String> = buildList {
  if (language.isNotEmpty()) add(languageName(language))
  addAll(facets.genres)
  facets.decades.forEach { add("${it}s") }
  if (facets.minRating > 0) add(RATINGS.firstOrNull { it.first == facets.minRating }?.second ?: "${facets.minRating}+")
  if (sort != SortKey.PROVIDER) add(sort.label)
}

@Composable
fun RefineChip(picks: List<String>, onClick: () -> Unit, enabled: Boolean = true) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = onClick,
    enabled = enabled,
    resting = colors.bgControl,
    shape = RoundedCornerShape(KeelTvTheme.radii.control),
    modifier = Modifier.height(KeelTvTheme.dimens.controlHeight),
  ) { focused ->
    val quiet = when {
      !enabled -> colors.textDisabled
      focused -> colors.onFocus
      else -> colors.textSecondary
    }
    val chosen = when {
      !enabled -> colors.textDisabled
      focused -> colors.onFocus
      else -> colors.accent
    }
    Row(
      modifier = Modifier.padding(start = KeelTvTheme.space.s3, end = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.FilterList, contentDescription = null, tint = quiet, modifier = Modifier.size(17.dp))
      Text(text = "Refine", style = KeelTvTheme.type.control, color = quiet, maxLines = 1)
      picks.take(NAMED_PICKS).forEach { pick ->
        Text(text = pick, style = KeelTvTheme.type.control, color = chosen, maxLines = 1, softWrap = false)
      }
      if (picks.size > NAMED_PICKS) {
        Text(
          text = "+${picks.size - NAMED_PICKS}",
          style = KeelTvTheme.type.control,
          color = if (focused) colors.onFocusMuted else chosen.copy(alpha = 0.7f),
          maxLines = 1,
        )
      }
    }
  }
}

private val SHEET_WIDTH = 400.dp

private val SHEET_START = 28.dp
private val SHEET_END = 40.dp

private val SCRIM = Brush.horizontalGradient(0f to Color(0x330A0A0C), 0.7f to Color(0xB80A0A0C))

private val SETTLE = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun RefineSheet(
  summary: String?,
  entry: FocusRequester,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }

    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(320, easing = SETTLE)) }
    LaunchedEffect(Unit) { settleFocus(entry) }
    var said by remember { mutableStateOf(summary) }
    LaunchedEffect(summary) { if (summary != null) said = summary }

    val colors = KeelTvTheme.colors
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(SCRIM))
      Column(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .fillMaxHeight()
          .width(SHEET_WIDTH)
          .graphicsLayer { translationX = (1f - shown.value) * size.width }
          .background(colors.bgSurface)
          .padding(top = 26.dp, bottom = 22.dp)
          .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = SHEET_START, end = SHEET_END, bottom = 2.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Text(
            text = "Refine",
            style = KeelTvTheme.type.section,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
          )
          said?.let {
            Text(
              text = it,
              style = KeelTvTheme.type.small,
              color = colors.textTertiary,
              maxLines = 1,
              modifier = Modifier.padding(bottom = 2.dp),
            )
          }
        }
        content()
      }
    }
  }
}

@Composable
fun BrowseRefine(
  summary: String?,
  sort: SortKey,
  onSort: (SortKey) -> Unit,
  language: String,
  onLanguage: (String) -> Unit,
  options: FacetOptions,
  facets: Facets,
  onFacets: (Facets) -> Unit,
  onDismiss: () -> Unit,
) {
  val entry = remember { FocusRequester() }
  val languages = remember { LANGUAGE_FILTER_CHOICES.sortedBy { it.first.isNotEmpty() && it.first != language } }

  RefineSheet(summary = summary, entry = entry, onDismiss = onDismiss) {
    ChoiceRow("Sort", SortKey.entries, { it.label }, { it == sort }, onSort, entry = entry)
    ChoiceRow(
      label = "Language",
      note = "All sections",
      options = languages,
      name = { it.second },
      chosen = { it.first == language },
      onPick = { onLanguage(it.first) },
    )
    if (options.genres.isNotEmpty()) {
      ChoiceRow("Genre", options.genres, { it }, { it in facets.genres }, { genre ->
        onFacets(facets.copy(genres = facets.genres.toggle(genre)))
      })
    } else if (options.sampled) {
      Column(verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s1)) {
        SheetLabel("Genre")
        Text(
          text = "Your provider does not list a genre for anything here.",
          style = KeelTvTheme.type.small,
          color = KeelTvTheme.colors.textTertiary,
          modifier = Modifier.padding(start = SHEET_START, end = SHEET_END),
        )
      }
    }
    if (options.decades.isNotEmpty()) {
      ChoiceRow("Year", options.decades, { "${it}s" }, { it in facets.decades }, { decade ->
        onFacets(facets.copy(decades = facets.decades.toggle(decade)))
      })
    }
    ChoiceRow("Rating", RATINGS, { it.second }, { facets.minRating == it.first }, { (floor) ->
      onFacets(facets.copy(minRating = floor))
    })
    Spacer(Modifier.weight(1f))
    Row(
      modifier = Modifier.padding(start = SHEET_START, end = SHEET_END),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      TvButton("Clear filters", { onFacets(Facets()) }, enabled = !facets.isEmpty)
      TvButton("Done", onDismiss, tone = ButtonTone.Primary)
    }
  }
}

@Composable
fun LanguageSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
  val entry = remember { FocusRequester() }
  RefineSheet(summary = null, entry = entry, onDismiss = onDismiss) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
      SheetLabel("Language", note = "All sections")
      LanguageList(
        selected = selected,
        onPick = onPick,
        options = LANGUAGE_FILTER_CHOICES,
        first = entry,
        modifier = Modifier.weight(1f).padding(start = SHEET_START - 12.dp, end = SHEET_END - 12.dp),
      )
    }
  }
}

@Composable
private fun SheetLabel(label: String, note: String? = null) {
  Row(
    modifier = Modifier.padding(start = SHEET_START, end = SHEET_END),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    Text(
      text = label,
      style = KeelTvTheme.type.small.copy(fontWeight = FontWeight.Medium),
      color = KeelTvTheme.colors.textSecondary,
    )
    if (note != null) {
      Text(text = note, style = KeelTvTheme.type.small, color = KeelTvTheme.colors.textTertiary)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> ChoiceRow(
  label: String,
  options: List<T>,
  name: (T) -> String,
  chosen: (T) -> Boolean,
  onPick: (T) -> Unit,
  note: String? = null,
  entry: FocusRequester? = null,
) {
  val home = entry ?: remember { FocusRequester() }
  val homeAt = remember(options) { options.indexOfFirst(chosen).coerceAtLeast(0) }
  val state = rememberLazyListState(initialFirstVisibleItemIndex = (homeAt - 1).coerceAtLeast(0))

  Column(verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
    SheetLabel(label, note)
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberWithinMargins()) {
      LazyRow(
        state = state,
        contentPadding = PaddingValues(start = SHEET_START, end = SHEET_END),
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
        modifier = Modifier.fillMaxWidth().focusRestorer(home),
      ) {
        items(options.size, key = { name(options[it]) }) { index ->
          val option = options[index]
          TvChip(
            label = name(option),
            selected = chosen(option),
            onClick = { onPick(option) },
            modifier = if (index == homeAt) Modifier.focusRequester(home) else Modifier,
          )
        }
      }
    }
  }
}

/**
 * A focus scroll that keeps the remote's chip inside the sheet's margins and otherwise leaves the
 * row where it is. A television's own pivots every row to put the chip a third of the way along,
 * which slid a row that already showed the chip and cut its first one in half.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberWithinMargins(): BringIntoViewSpec {
  val density = LocalDensity.current
  return remember(density) {
    val lead = with(density) { SHEET_START.toPx() }
    val trail = with(density) { SHEET_END.toPx() }
    object : BringIntoViewSpec {
      override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < lead -> offset - lead
        offset + size > containerSize - trail -> offset + size - (containerSize - trail)
        else -> 0f
      }
    }
  }
}

private fun <T> List<T>.toggle(value: T): List<T> =
  if (value in this) filterNot { it == value } else this + value

@Composable
private fun Panel(
  title: String,
  width: Int,
  onDismiss: () -> Unit,
  content: @Composable () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .width(width.dp)
        .heightIn(max = 420.dp)
        .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
        .background(KeelTvTheme.colors.bgSurface)
        .padding(KeelTvTheme.space.s4)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      Text(
        text = title,
        style = KeelTvTheme.type.heading,
        color = KeelTvTheme.colors.textPrimary,
        modifier = Modifier.padding(start = KeelTvTheme.space.s3),
      )
      content()
    }
  }
}

@Composable
fun LanguagePicker(
  selected: String,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
  title: String = "Language",
  options: List<Pair<String, String>> = LANGUAGE_FILTER_CHOICES,
) {
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { settleFocus(first) }

  Panel(title, 280, onDismiss) {
    LanguageList(selected, onPick, options, first)
  }
}

@Composable
private fun LanguageList(
  selected: String,
  onPick: (String) -> Unit,
  options: List<Pair<String, String>>,
  first: FocusRequester,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  val selectedIndex = remember(selected) { options.indexOfFirst { it.first == selected }.coerceAtLeast(0) }
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (selectedIndex > 4) selectedIndex - 2 else 0)

  LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    items(options.size, key = { options[it].first }) { index ->
      val (value, label) = options[index]
      FocusRow(
        onClick = { onPick(value) },
        selected = value == selected,
        modifier = Modifier
          .fillMaxWidth()
          .height(34.dp)
          .then(if (index == selectedIndex) Modifier.focusRequester(first) else Modifier),
      ) { focused ->
        Box(Modifier.fillMaxWidth().padding(horizontal = KeelTvTheme.space.s3)) {
          Text(
            label,
            style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal),
            color = when {
              focused -> colors.onFocus
              value == selected -> colors.accent
              else -> colors.textSecondary
            },
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart),
          )
        }
      }
    }
  }
}

/**
 * Asks for [requester] until something holding it has been laid out to take it. A request made
 * before then is dropped with a warning rather than failing, so the answer is what says to ask
 * again.
 */
internal suspend fun settleFocus(requester: FocusRequester) {
  repeat(10) {
    if (requester.requestFocus(FocusDirection.Enter)) return
    delay(50)
  }
}
