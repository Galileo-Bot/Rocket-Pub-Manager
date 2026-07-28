package utils

import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import storage.DailyCount
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun renderDailyCountChart(title: String, data: List<DailyCount>): ByteArray {
	val days = data.map { it.day.toString() }
	val counts = data.map { it.count }

	val plot = plot {
		line {
			x(days)
			y(counts)
		}

		layout.title = title
	}

	return ByteArrayOutputStream().use { output ->
		ImageIO.write(plot.toBufferedImage(), "png", output)
		output.toByteArray()
	}
}
