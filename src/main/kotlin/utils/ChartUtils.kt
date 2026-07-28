package utils

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.letsplot.settings.font.FontFace
import org.jetbrains.kotlinx.kandy.letsplot.settings.font.FontFamily
import org.jetbrains.kotlinx.kandy.letsplot.style.LegendPosition
import org.jetbrains.kotlinx.kandy.letsplot.style.Style
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.context.invoke
import storage.DailyCount
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import java.awt.Color as AwtColor

/** `Color.hex(Int)` drops leading zeroes, which breaks the very dark colors used here. */
private fun hexColor(value: Int) = Color.hex(String.format("#%06X", value))

private const val SPACE_DEEP = 0x03050F
private const val SPACE_HORIZON = 0x0A1030
private const val NEBULA_VIOLET = 0x3B2E8F
private const val NEBULA_CYAN = 0x0E4C6B
private const val GRID_HEX = 0x1E2A4A
private const val ACCENT_BRIGHT_HEX = 0xA5F3FC
private const val TEXT_PRIMARY_HEX = 0xE0F2FE
private const val TEXT_SECONDARY_HEX = 0x8CA0C6

/** Accent color of the charts, reused by the embeds so both match. */
const val CHART_ACCENT_HEX = 0x38BDF8

private val ACCENT = hexColor(CHART_ACCENT_HEX)
private val ACCENT_BRIGHT = hexColor(ACCENT_BRIGHT_HEX)
private val GRID = hexColor(GRID_HEX)
private val TEXT_PRIMARY = hexColor(TEXT_PRIMARY_HEX)
private val TEXT_SECONDARY = hexColor(TEXT_SECONDARY_HEX)
private val DEEP_SPACE = hexColor(SPACE_DEEP)

/** Beyond that many days, individual point markers turn the line into noise. */
private const val MAX_DAYS_WITH_POINTS = 45

/** Beyond that many days the daily values are already close enough, smoothing them is wasted work. */
private const val MAX_DAYS_WITH_SMOOTHING = 120

/** Interpolated samples drawn between two consecutive days. */
private const val SAMPLES_PER_DAY = 12

private const val CHART_WIDTH = 1000
private const val CHART_HEIGHT = 500
private const val CHART_SCALE = 2

/** Days without any row are missing from the SQL result, they must be drawn as zeroes. */
fun List<DailyCount>.fillMissingDays(from: LocalDate, to: LocalDate): List<DailyCount> {
	val counts = associate { it.day to it.count }
	val days = ChronoUnit.DAYS.between(from, to)
	return (0..days).map { offset ->
		val day = from.plusDays(offset)
		DailyCount(day, counts[day] ?: 0)
	}
}

/** Rounds the Y axis step up to a 1/2/5 x 10^n value so the labels stay whole numbers. */
private fun niceStep(max: Int, targetTicks: Int = 5): Int {
	if (max <= targetTicks) return 1
	val rough = max.toDouble() / targetTicks
	val magnitude = 10.0.pow(floor(log10(rough)))
	val normalized = rough / magnitude
	val factor = when {
		normalized <= 1 -> 1
		normalized <= 2 -> 2
		normalized <= 5 -> 5
		else -> 10
	}
	return (factor * magnitude).toInt().coerceAtLeast(1)
}

/**
 * Monotone cubic interpolation (Fritsch-Carlson), which rounds the corners of the curve without
 * the overshoots a plain cubic spline would produce, so the line never dips below zero.
 *
 * @return the interpolated values, [SAMPLES_PER_DAY] of them per interval plus the final point.
 */
private fun smooth(values: List<Int>): List<Double> {
	val n = values.size
	val y = values.map { it.toDouble() }
	val slopes = (0..<n - 1).map { y[it + 1] - y[it] }

	val tangents = DoubleArray(n) { index ->
		when (index) {
			0 -> slopes.first()
			n - 1 -> slopes.last()
			else -> (slopes[index - 1] + slopes[index]) / 2
		}
	}

	slopes.forEachIndexed { index, slope ->
		if (slope == 0.0) {
			tangents[index] = 0.0
			tangents[index + 1] = 0.0
			return@forEachIndexed
		}

		val alpha = tangents[index] / slope
		val beta = tangents[index + 1] / slope
		val norm = alpha * alpha + beta * beta
		if (norm > 9) {
			val scale = 3.0 / sqrt(norm)
			tangents[index] = scale * alpha * slope
			tangents[index + 1] = scale * beta * slope
		}
	}

	return buildList {
		for (index in 0..<n - 1) {
			for (sample in 0..<SAMPLES_PER_DAY) {
				val t = sample.toDouble() / SAMPLES_PER_DAY
				val t2 = t * t
				val t3 = t2 * t
				add(
					(2 * t3 - 3 * t2 + 1) * y[index] +
						(t3 - 2 * t2 + t) * tangents[index] +
						(-2 * t3 + 3 * t2) * y[index + 1] +
						(t3 - t2) * tangents[index + 1]
				)
			}
		}
		add(y.last())
	}
}

private val spaceStyle = Style.createCustom {
	global {
		text {
			color = TEXT_SECONDARY
			fontFamily = FontFamily.SANS
			fontSize = 13.0
		}
		title {
			color = TEXT_PRIMARY
			fontFamily = FontFamily.SANS
			fontFace = FontFace.BOLD
			fontSize = 20.0
		}
	}
	plotCanvas {
		background {
			blank = true
		}
		title {
			color = TEXT_PRIMARY
			fontFace = FontFace.BOLD
			fontSize = 22.0
			margin(0.0, 0.0, 16.0, 0.0)
		}
		inset(24.0)
	}
	panel {
		background {
			blank = true
		}
		grid {
			majorLine {
				color = GRID
				width = 0.6
			}
			minorLine {
				blank = true
			}
		}
	}
	axis {
		line {
			blank = true
		}
		ticks {
			blank = true
		}
		text {
			color = TEXT_SECONDARY
			fontSize = 12.0
		}
		title {
			blank = true
		}
	}
	legend {
		position = LegendPosition.None
	}
}

fun renderDailyCountChart(title: String, data: List<DailyCount>): ByteArray {
	val counts = data.map { it.count }
	val max = counts.max()
	val step = niceStep(max)
	val yBreaks = (0..(max + step) step step).map { it.toDouble() }
	// Long ranges get one label per month, otherwise the dates overlap.
	val dateFormat = if (data.size > MAX_DAYS_WITH_SMOOTHING) "%b %Y" else "%d %b"
	val smoothed = data.size in 3..MAX_DAYS_WITH_SMOOTHING

	val days = data.map { it.day.atStartOfDay().toKotlinLocalDateTime() }
	val curve = if (smoothed) smooth(counts) else counts.map { it.toDouble() }
	val curveDays = if (smoothed) {
		val start = data.first().day.atStartOfDay()
		val minutesPerSample = 24L * 60 / SAMPLES_PER_DAY
		curve.indices.map { start.plusMinutes(it * minutesPerSample).toKotlinLocalDateTime() }
	} else {
		days
	}

	val chart = plot {
		area {
			x(curveDays) {
				axis.breaks(format = dateFormat)
			}
			y(curve) {
				axis.breaks(yBreaks)
			}
			fillColor = ACCENT
			alpha = 0.22
		}

		line {
			x(curveDays)
			y(curve)
			color = ACCENT
			// A thick stroke turns into a solid block once the points get close to each other.
			width = if (data.size > MAX_DAYS_WITH_SMOOTHING) 1.0 else 2.5
		}

		if (data.size <= MAX_DAYS_WITH_POINTS) {
			points {
				x(days)
				y(counts.map { it.toDouble() })
				color = ACCENT_BRIGHT
				fillColor = DEEP_SPACE
				symbol = Symbol.CIRCLE_FILLED
				size = 2.6
				stroke = 1.3
			}
		}

		layout {
			this.title = title
			size = CHART_WIDTH to CHART_HEIGHT
			style(spaceStyle)
		}
	}

	return ByteArrayOutputStream().use { output ->
		ImageIO.write(chart.toBufferedImage(scale = CHART_SCALE).overSpaceBackground(), "png", output)
		output.toByteArray()
	}
}

/** Paints a deep space gradient, two nebula glows and a starfield, then lays the plot over it. */
private fun BufferedImage.overSpaceBackground(): BufferedImage {
	val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
	val graphics = canvas.createGraphics()

	graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
	graphics.paint = GradientPaint(
		0f, 0f, AwtColor(SPACE_DEEP),
		width.toFloat(), height.toFloat(), AwtColor(SPACE_HORIZON)
	)
	graphics.fillRect(0, 0, width, height)

	graphics.nebula(width * 0.18f, height * 0.85f, min(width, height) * 0.75f, NEBULA_VIOLET, width, height)
	graphics.nebula(width * 0.88f, height * 0.15f, min(width, height) * 0.6f, NEBULA_CYAN, width, height)

	// A fixed seed keeps the same starfield across renders, so all the charts look like one set.
	val random = Random(seed = 1969)
	repeat(width * height / 2600) {
		val diameter = if (random.nextInt(18) == 0) 4 else 2
		graphics.color = AwtColor(1f, 1f, 1f, 0.15f + random.nextFloat() * 0.7f)
		graphics.fillOval(random.nextInt(width), random.nextInt(height), diameter, diameter)
	}

	graphics.drawImage(this, 0, 0, null)
	graphics.dispose()

	return canvas
}

private fun Graphics2D.nebula(x: Float, y: Float, radius: Float, colorHex: Int, width: Int, height: Int) {
	if (radius < 1f) return

	val color = AwtColor(colorHex)
	paint = RadialGradientPaint(
		Point2D.Float(x, y),
		radius,
		floatArrayOf(0f, 1f),
		arrayOf(AwtColor(color.red, color.green, color.blue, 130), AwtColor(color.red, color.green, color.blue, 0))
	)
	fillRect(0, 0, width, height)
}
