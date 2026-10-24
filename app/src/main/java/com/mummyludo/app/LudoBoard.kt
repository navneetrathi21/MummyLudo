package com.mummyludo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*

@Composable
internal fun LudoBoard(
    state: GameState,
    visualProgress: Map<String, Int>,
    legalIds: Set<String>,
    onToken: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White).testTag("ludo_board")) {
        val cellDp = maxWidth / 15
        Canvas(Modifier.fillMaxSize()) { drawBoard() }
        Canvas(Modifier.fillMaxSize()) { drawTokens(state, visualProgress, legalIds) }
        state.tokens.filter { it.id in legalIds }.forEach { token ->
            val (cx, cy) = if (token.progress == YARD) yardTokenLocation(token) else tokenLocation(token)
            Box(
                Modifier
                    .offset(x = cellDp * cx, y = cellDp * cy)
                    .size(cellDp)
                    .clickable { onToken(token.id) }
                    .semantics { contentDescription = "${token.color.label} token ${token.index + 1}" }
                    .testTag("token_${token.color.name.lowercase()}_${token.index}"),
            )
        }
    }
}

internal fun DrawScope.drawTokens(
    state: GameState,
    visualProgress: Map<String, Int>,
    legalIds: Set<String>,
) {
    val cell = size.width / 15f
    val shownTokens = state.tokens.filter { it.progress != FINISHED }.map { token ->
        token.copy(progress = visualProgress[token.id] ?: token.progress)
    }
    shownTokens.forEach { shown ->
        val (cx, cy) = if (shown.progress == YARD) yardTokenLocation(shown) else tokenLocation(shown)
        val siblings = if (shown.progress == YARD) listOf(shown) else shownTokens.filter {
            it.color == shown.color && it.progress == shown.progress
        }
        val slot = siblings.sortedBy { it.index }.indexOfFirst { it.id == shown.id }
        val spread = when (siblings.size) {
            1 -> Offset.Zero
            2 -> listOf(Offset(-.17f, 0f), Offset(.17f, 0f))[slot]
            3 -> listOf(Offset(-.17f, .13f), Offset(.17f, .13f), Offset(0f, -.16f))[slot]
            else -> listOf(Offset(-.16f, -.14f), Offset(.16f, -.14f), Offset(-.16f, .16f), Offset(.16f, .16f))[slot.coerceAtMost(3)]
        }
        val center = Offset((cx + .5f + spread.x) * cell, (cy + .5f + spread.y) * cell)
        if (shown.id in legalIds) drawCircle(Sage.copy(alpha = .25f), cell * .45f, Offset((cx + .5f) * cell, (cy + .5f) * cell))
        drawPawn(center, cell, colorOf(shown.color), if (siblings.size == 1) 1f else .72f)
    }
}

internal fun DrawScope.drawPawn(center: Offset, cell: Float, color: Color, scale: Float) {
    drawOval(
        color = Ink.copy(alpha = .16f),
        topLeft = center + Offset(-cell * .24f * scale, cell * .06f * scale),
        size = androidx.compose.ui.geometry.Size(cell * .48f * scale, cell * .30f * scale),
    )
    drawOval(
        color = color,
        topLeft = center + Offset(-cell * .22f * scale, cell * .01f * scale),
        size = androidx.compose.ui.geometry.Size(cell * .44f * scale, cell * .29f * scale),
    )
    drawCircle(Color.White, cell * .23f * scale, center - Offset(0f, cell * .13f * scale))
    drawCircle(color, cell * .19f * scale, center - Offset(0f, cell * .13f * scale))
    drawCircle(Color.White.copy(alpha = .75f), cell * .055f * scale, center - Offset(cell * .065f * scale, cell * .19f * scale))
}

private val track = listOf(
    1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6, 6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
    7 to 0, 8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 9 to 6, 10 to 6, 11 to 6, 12 to 6,
    13 to 6, 14 to 6, 14 to 7, 14 to 8, 13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8, 8 to 9, 8 to 10,
    8 to 11, 8 to 12, 8 to 13, 8 to 14, 7 to 14, 6 to 14, 6 to 13, 6 to 12, 6 to 11, 6 to 10,
    6 to 9, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8, 0 to 7, 0 to 6,
)

internal fun tokenLocation(token: Token): Pair<Float, Float> {
    if (token.progress in 0..51) {
        val (x, y) = track[globalTrack(token.color, token.progress)]
        return x.toFloat() to y.toFloat()
    }
    val lane = (token.progress - 51).coerceIn(1, 5)
    return when (token.color) {
        LudoColor.RED -> lane.toFloat() to 7f
        LudoColor.GREEN -> 7f to lane.toFloat()
        LudoColor.YELLOW -> (14 - lane).toFloat() to 7f
        LudoColor.BLUE -> 7f to (14 - lane).toFloat()
    }
}

internal fun yardTokenLocation(token: Token): Pair<Float, Float> {
    // The approved inset home occupies 1.1..4.9 cells. These values place the
    // pawn centers at 2.2/3.8 after LudoBoard adds the standard half-cell offset.
    val spots = listOf(1.7f to 1.7f, 3.3f to 1.7f, 1.7f to 3.3f, 3.3f to 3.3f)
    val (x, y) = spots[token.index]
    return when (token.color) {
        LudoColor.RED -> x to y
        LudoColor.GREEN -> (14 - x) to y
        LudoColor.YELLOW -> (14 - x) to (14 - y)
        LudoColor.BLUE -> x to (14 - y)
    }
}

internal fun DrawScope.drawBoard() {
    val c = size.width / 15f
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) = drawRect(color, Offset(x * c, y * c), androidx.compose.ui.geometry.Size(w * c, h * c))
    val yardColors = listOf(
        Color(0xFFF1727C), Color(0xFF63C086), Color(0xFFF7D04A), Color(0xFF5D9CE0),
    )
    rect(0, 0, 6, 6, yardColors[0]); rect(9, 0, 6, 6, yardColors[1])
    rect(9, 9, 6, 6, yardColors[2]); rect(0, 9, 6, 6, yardColors[3])
    listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9).forEachIndexed { index, (x, y) ->
        drawRoundRect(
            Color.White,
            Offset((x + 1.1f) * c, (y + 1.1f) * c),
            androidx.compose.ui.geometry.Size(3.8f * c, 3.8f * c),
            CornerRadius(c * .62f),
        )
        drawRoundRect(
            yardColors[index].copy(alpha = .24f),
            Offset((x + 1.1f) * c, (y + 1.1f) * c),
            androidx.compose.ui.geometry.Size(3.8f * c, 3.8f * c),
            CornerRadius(c * .62f),
            style = Stroke(c * .035f),
        )
        val localCenters = listOf(2.2f to 2.2f, 3.8f to 2.2f, 2.2f to 3.8f, 3.8f to 3.8f)
        localCenters.forEach { (localX, localY) ->
            drawCircle(
                yardColors[index].copy(alpha = .13f),
                c * .48f,
                Offset((x + localX) * c, (y + localY) * c),
            )
            drawCircle(
                yardColors[index].copy(alpha = .32f),
                c * .48f,
                Offset((x + localX) * c, (y + localY) * c),
                style = Stroke(c * .035f),
            )
        }
    }
    track.forEach { (x, y) ->
        drawRect(Color.White, Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c))
        drawRect(Ink.copy(.13f), Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c), style = Stroke(1f))
    }
    for (i in 1..5) {
        rect(i, 7, 1, 1, Color(0xFFF7A3AA)); rect(7, i, 1, 1, Color(0xFF9AD9B4))
        rect(14 - i, 7, 1, 1, Color(0xFFF9DF86)); rect(7, 14 - i, 1, 1, Color(0xFF9BC7F1))
    }
    listOf(
        Triple(track[0], Color(0xFFF36E78), Red),
        Triple(track[13], Color(0xFF79CA9A), Green),
        Triple(track[26], Color(0xFFF9D966), Yellow),
        Triple(track[39], Color(0xFF72A8EC), Blue),
    ).forEach { (cell, fill, _) -> rect(cell.first, cell.second, 1, 1, fill) }
    val center = Offset(7.5f * c, 7.5f * c)
    fun triangle(a: Offset, b: Offset, color: Color) {
        drawPath(Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, color)
    }
    triangle(Offset(6*c,6*c), Offset(6*c,9*c), Red)
    triangle(Offset(6*c,6*c), Offset(9*c,6*c), Green)
    triangle(Offset(9*c,6*c), Offset(9*c,9*c), Yellow)
    triangle(Offset(6*c,9*c), Offset(9*c,9*c), Blue)
    listOf(8, 21, 34, 47).forEach { idx ->
        val (x,y) = track[idx]
        val centerPoint = Offset((x + .5f) * c, (y + .5f) * c)
        drawCircle(Ink.copy(.08f), c * .34f, centerPoint)
        val star = Path()
        repeat(10) { point ->
            val angle = -Math.PI / 2 + point * Math.PI / 5
            val radius = if (point % 2 == 0) c * .25f else c * .11f
            val px = centerPoint.x + kotlin.math.cos(angle).toFloat() * radius
            val py = centerPoint.y + kotlin.math.sin(angle).toFloat() * radius
            if (point == 0) star.moveTo(px, py) else star.lineTo(px, py)
        }
        star.close()
        drawPath(star, Ink.copy(alpha = .48f))
    }
    // Thin, restrained direction arrows on the short post-start hops.
    listOf(1 to 2, 4 to 5).forEach { (from, to) ->
        LudoColor.entries.forEach { color ->
            val a = track[(from + listOf(0,13,26,39)[color.ordinal]) % 52]
            val b = track[(to + listOf(0,13,26,39)[color.ordinal]) % 52]
            val p1 = Offset((a.first+.5f)*c,(a.second+.5f)*c)
            val p2 = Offset((b.first+.5f)*c,(b.second+.5f)*c)
            drawLine(Ink.copy(.46f), p1, p2, c*.052f)
            val d = (p2-p1); val n = Offset(-d.y,d.x)
            drawLine(Ink.copy(.46f), p2, p2-d*.23f+n*.14f, c*.052f)
            drawLine(Ink.copy(.46f), p2, p2-d*.23f-n*.14f, c*.052f)
        }
    }
    // Exact four-block paths from the approved reference (tail and head included).
    val homeArrowCells = listOf(
        listOf(1 to 8, 0 to 8, 0 to 7, 1 to 7),
        listOf(6 to 1, 6 to 0, 7 to 0, 7 to 1),
        listOf(13 to 6, 14 to 6, 14 to 7, 13 to 7),
        listOf(8 to 13, 8 to 14, 7 to 14, 7 to 13),
    )
    homeArrowCells.forEach { cells ->
        val points = cells.map { (x, y) -> Offset((x + .5f) * c, (y + .5f) * c) }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(Ink.copy(.30f), start, end, c * .032f)
        }
        val end = points.last()
        val previous = points[points.lastIndex - 1]
        val d = end - previous
        val n = Offset(-d.y, d.x)
        drawLine(Ink.copy(.34f), end, end - d * .16f + n * .09f, c * .032f)
        drawLine(Ink.copy(.34f), end, end - d * .16f - n * .09f, c * .032f)
    }
}

internal fun colorOf(color: LudoColor): Color = when (color) {
    LudoColor.RED -> Red; LudoColor.GREEN -> Green; LudoColor.YELLOW -> Yellow; LudoColor.BLUE -> Blue
}
