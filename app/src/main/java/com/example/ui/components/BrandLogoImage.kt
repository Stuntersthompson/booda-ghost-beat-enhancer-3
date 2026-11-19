package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioLogoCharcoal

/**
 * Renders the pixel-faithful official BOODA GHOST BEATZ logo at its exact 302:246 aspect ratio
 * using ContentScale.Fit and charcoal padding—never cropping, stretching, or recoloring.
 */
@Composable
fun BrandLogoImage(
  height: Dp,
  contentDescription: String?,
  framed: Boolean = true,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(8.dp)
  val containerModifier = modifier
    .height(height)
    .aspectRatio(302f / 246f)
    .clip(shape)
    .background(StudioLogoCharcoal)
    .let { base ->
      if (framed) base.border(1.dp, StudioBorder, shape) else base
    }
    .semantics(mergeDescendants = true) {}

  Box(
    modifier = containerModifier,
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = painterResource(id = R.drawable.booda_official_logo),
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        .padding(if (framed) 2.dp else 0.dp)
    )
  }
}
