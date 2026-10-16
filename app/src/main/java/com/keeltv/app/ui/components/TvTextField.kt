package com.keeltv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun TvTextField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  masked: Boolean = false,
  keyboardType: KeyboardType = KeyboardType.Text,
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

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(label, style = KeelTvTheme.type.small, color = colors.textTertiary)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(if (focused || editing) colors.focus else colors.bgRaised, shape)
        .defaultMinSize(minHeight = KeelTvTheme.dimens.fieldHeight)
        .padding(horizontal = KeelTvTheme.space.s3, vertical = KeelTvTheme.space.s2),
      contentAlignment = Alignment.CenterStart,
    ) {
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
          textStyle = KeelTvTheme.type.body.copy(color = colors.onFocus),
          cursorBrush = SolidColor(colors.accent),
          visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
          keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { editing = false }),
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
            .clickable(interactionSource = interaction, indication = null) {
              hasEdited = true
              everFocused = false
              editing = true
            },
        ) {
          val shown = when {
            value.isEmpty() -> placeholder
            masked -> "•".repeat(value.length.coerceAtMost(24))
            else -> value
          }
          Text(
            text = shown,
            style = KeelTvTheme.type.body,
            color = when {
              focused && value.isEmpty() -> colors.onFocusMuted
              focused -> colors.onFocus
              value.isEmpty() -> colors.textTertiary
              else -> colors.textPrimary
            },
            maxLines = 1,
          )
        }
        LaunchedEffect(Unit) {
          keyboard?.hide()
          if (hasEdited) rowFocus.requestFocus()
        }
      }
    }
  }
}
