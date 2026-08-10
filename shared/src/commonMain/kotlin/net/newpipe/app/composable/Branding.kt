package net.newpipe.app.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val ONewPipeNavy = Color(0xFF101827)
val ONewPipeMint = Color(0xFF50E3C2)
val ONewPipeViolet = Color(0xFF7C5CFC)

/**
 * ONewPipe's new mark: an open mint orbit around a violet play signal.
 * The same proportions and colors are reproduced by the web and Android launcher assets.
 */
@Composable
fun BrandLogo(modifier: Modifier = Modifier.size(48.dp)) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ONewPipeNavy)
    ) {
        val side = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = ONewPipeMint,
            radius = side * 0.31f,
            center = center,
            style = Stroke(width = side * 0.105f, cap = StrokeCap.Round)
        )

        val triangle = Path().apply {
            moveTo(center.x - side * 0.08f, center.y - side * 0.14f)
            lineTo(center.x + side * 0.18f, center.y)
            lineTo(center.x - side * 0.08f, center.y + side * 0.14f)
            close()
        }
        drawPath(triangle, color = ONewPipeViolet)
    }
}
