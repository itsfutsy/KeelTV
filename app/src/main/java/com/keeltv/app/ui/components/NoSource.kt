package com.keeltv.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun NoSource(title: String, note: String) {
  Column(modifier = Modifier.fillMaxSize().padding(KeelTvTheme.dimens.screenPadding)) {
    ScreenHeader(title, note)
  }
}
