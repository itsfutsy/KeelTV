package com.keeltv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.keeltv.app.R

@Composable
fun KeelLogo(modifier: Modifier = Modifier, markOnly: Boolean = false) {
  Image(
    painter = painterResource(if (markOnly) R.drawable.keel_mark else R.drawable.keel_logo),
    contentDescription = "Keel TV",
    modifier = modifier,
  )
}
