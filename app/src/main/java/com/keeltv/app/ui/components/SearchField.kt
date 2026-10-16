package com.keeltv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun SearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  anchor: FocusRequester? = null,
) {
  val colors = KeelTvTheme.colors
  val shape = RoundedCornerShape(KeelTvTheme.radii.control)
  val rowFocus = remember { FocusRequester() }
  val fieldFocus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  var editing by remember { mutableStateOf(false) }
  var hasEdited by remember { mutableStateOf(false) }
  // The field reports "not focused" once before it ever gets focus, which must not end the edit.
  var everFocused by remember { mutableStateOf(false) }
  var hint by remember { mutableStateOf<String?>(null) }
  val voice = voiceSearchAvailable()
  val lit = focused || editing

  Row(
    modifier = modifier
      .height(KeelTvTheme.dimens.controlHeight)
      .background(if (lit) colors.focus else colors.bgControl, shape)
      .padding(start = 14.dp, end = KeelTvTheme.space.s3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!voice) {
      Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = null,
        tint = if (lit) colors.onFocusMuted else colors.textTertiary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(KeelTvTheme.space.s2))
    }
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (editing) {
        BackHandler(enabled = true) { editing = false }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fieldFocus)
            .onPreInterceptKeyBeforeSoftKeyboard { event ->
              if (event.key != Key.Back) {
                false
              } else {
                // Both halves are taken, or the IME closes on the down and the edit ends on an up
                // it never sees.
                if (event.type == KeyEventType.KeyUp) editing = false
                true
              }
            }
            .onFocusChanged {
              if (it.isFocused) everFocused = true else if (everFocused) editing = false
            }
            .onPreviewKeyEvent { event ->
              val vertical = event.key == Key.DirectionUp || event.key == Key.DirectionDown
              if (vertical && event.type == KeyEventType.KeyDown) {
                editing = false
                true
              } else {
                false
              }
            },
          singleLine = true,
          textStyle = KeelTvTheme.type.control.copy(color = colors.onFocus, fontWeight = FontWeight.Normal),
          cursorBrush = SolidColor(colors.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { editing = false }),
        )
        LaunchedEffect(Unit) {
          fieldFocus.requestFocus()
          keyboard?.show()
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rowFocus)
            .then(if (anchor != null) Modifier.focusRequester(anchor) else Modifier)
            .clickable(interactionSource = interaction, indication = null) {
              hasEdited = true
              everFocused = false
              editing = true
            },
        ) {
          Text(
            text = value.ifEmpty { hint ?: placeholder },
            style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal),
            color = when {
              lit && value.isNotEmpty() -> colors.onFocus
              lit -> colors.onFocusMuted
              value.isNotEmpty() -> colors.textPrimary
              hint != null -> colors.textSecondary
              else -> colors.textTertiary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        LaunchedEffect(Unit) {
          keyboard?.hide()
          if (hasEdited) rowFocus.requestFocus()
        }
      }
    }
    if (voice) {
      Spacer(Modifier.width(KeelTvTheme.space.s2))
      VoiceButton(onText = onValueChange, onHint = { hint = it }, onLight = lit)
    }
  }
}
