package com.emanueledipietro.remodex.feature.turn

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

@Composable
internal fun RemodexGitBranchGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        val nodeRadius = size.minDimension * 0.16f
        val leftX = size.width * 0.3f
        val topY = size.height * 0.24f
        val bottomY = size.height * 0.78f
        val rightX = size.width * 0.74f
        val rightY = size.height * 0.5f

        drawLine(
            color = color,
            start = Offset(leftX, topY + nodeRadius),
            end = Offset(leftX, bottomY - nodeRadius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(leftX + nodeRadius * 0.9f, topY + nodeRadius * 0.5f),
            end = Offset(rightX - nodeRadius, rightY - nodeRadius * 0.2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = nodeRadius, center = Offset(leftX, topY))
        drawCircle(color = color, radius = nodeRadius, center = Offset(leftX, bottomY))
        drawCircle(color = color, radius = nodeRadius, center = Offset(rightX, rightY))
    }
}

@Composable
internal fun RemodexGitCommitGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        val nodeRadius = size.minDimension * 0.16f
        val center = Offset(size.width / 2f, size.height / 2f)
        val sideInset = size.width * 0.16f

        drawLine(
            color = color,
            start = Offset(sideInset, center.y),
            end = Offset(center.x - nodeRadius * 1.4f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x + nodeRadius * 1.4f, center.y),
            end = Offset(size.width - sideInset, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = nodeRadius, center = center)
    }
}

@Composable
internal fun RemodexGitPushGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.095f
        val radius = size.minDimension * 0.38f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arrowTop = size.height * 0.24f
        val arrowBottom = size.height * 0.62f
        val arrowWing = size.width * 0.145f

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(center.x, arrowBottom),
            end = Offset(center.x, arrowTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, arrowTop),
            end = Offset(center.x - arrowWing, arrowTop + arrowWing),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, arrowTop),
            end = Offset(center.x + arrowWing, arrowTop + arrowWing),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun RemodexGitSyncGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val arcInset = size.minDimension * 0.18f
        val arcRect = Rect(
            left = arcInset,
            top = arcInset,
            right = size.width - arcInset,
            bottom = size.height - arcInset,
        )
        val arrowSize = size.minDimension * 0.105f

        drawArc(
            color = color,
            startAngle = 42f,
            sweepAngle = 212f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = 222f,
            sweepAngle = 212f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        val topRight = Offset(size.width * 0.76f, size.height * 0.28f)
        drawLine(
            color = color,
            start = topRight,
            end = Offset(topRight.x - arrowSize * 1.2f, topRight.y - arrowSize * 0.3f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = topRight,
            end = Offset(topRight.x - arrowSize * 0.2f, topRight.y + arrowSize * 1.1f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        val bottomLeft = Offset(size.width * 0.24f, size.height * 0.72f)
        drawLine(
            color = color,
            start = bottomLeft,
            end = Offset(bottomLeft.x + arrowSize * 1.2f, bottomLeft.y + arrowSize * 0.3f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = bottomLeft,
            end = Offset(bottomLeft.x + arrowSize * 0.2f, bottomLeft.y - arrowSize * 1.1f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun RemodexTrashCircleGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.085f
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.39f
        val bodyTop = size.height * 0.36f
        val bodyBottom = size.height * 0.68f
        val bodyLeft = size.width * 0.35f
        val bodyRight = size.width * 0.65f

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, bodyTop),
            end = Offset(size.width * 0.66f, bodyTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.42f, size.height * 0.28f),
            end = Offset(size.width * 0.58f, size.height * 0.28f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(bodyLeft, bodyTop),
            end = Offset(bodyLeft, bodyBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(bodyRight, bodyTop),
            end = Offset(bodyRight, bodyBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(bodyLeft, bodyBottom),
            end = Offset(bodyRight, bodyBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, bodyTop + strokeWidth),
            end = Offset(size.width * 0.5f, bodyBottom - strokeWidth),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
