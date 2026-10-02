package org.yb.secondwind.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

/** The device's casing colour (light theme bar, connected dot) and a dimmer amber for dark theme bars. */
val YellowbrickYellow = Color(0xFFFFD500)
val YellowbrickAmber = Color(0xFFE6B800)
val OnYellow = Color(0xFF1C1B1F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ybTopBarColors(): TopAppBarColors {
    val bg = if (isSystemInDarkTheme()) YellowbrickAmber else YellowbrickYellow
    return TopAppBarDefaults.topAppBarColors(
        containerColor = bg, scrolledContainerColor = bg,
        titleContentColor = OnYellow, navigationIconContentColor = OnYellow, actionIconContentColor = OnYellow,
    )
}

/** A Yellowbrick seen face-on — tall rounded body, keypad bar, antenna stub top-left, two uplink arcs. 24 dp grid, 2 px stroke. */
val YbIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Yellowbrick", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        listOf(
            "M10 12 h4 a2.5 2.5 0 0 1 2.5 2.5 v6 a2.5 2.5 0 0 1 -2.5 2.5 h-4 a2.5 2.5 0 0 1 -2.5 -2.5 v-6 a2.5 2.5 0 0 1 2.5 -2.5 z",
            "M10.5 20.5 H13.5",
            "M9.5 12 V8.5",
            "M8.3 4.6 A3.8 3.8 0 0 1 13 5.9",
            "M8 1.9 A6.8 6.8 0 0 1 16 4.2",
        ).forEach { d ->
            addPath(
                pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}
