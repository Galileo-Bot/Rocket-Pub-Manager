package extensions

import dev.kord.common.Color
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.types.PublicInteractionContext
import dev.kordex.i18n.Key
import fr.ayfri.rocketmanager.i18n.Translations
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlin.time.toKotlinInstant
import storage.DailyCount
import storage.getAdCountsByDay
import storage.getVerificationCountsByDay
import utils.CHART_ACCENT_HEX
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed
import utils.fillMissingDays
import utils.renderDailyCountChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class StatsPeriod(val days: Long?, private val translation: Key) : ChoiceEnum {
	SEVEN_DAYS(7, Translations.Periods.sevenDays),
	THIRTY_DAYS(30, Translations.Periods.thirtyDays),
	NINETY_DAYS(90, Translations.Periods.ninetyDays),
	ALL(null, Translations.Periods.all);

	override val readableName get() = translation
}

private fun StatsPeriod?.since(): Instant {
	val days = (this ?: StatsPeriod.THIRTY_DAYS).days ?: return Instant.EPOCH
	return Instant.now().minus(days, ChronoUnit.DAYS)
}

class Stats : Extension() {
	override val name = "Stats"

	class PeriodArguments : Arguments() {
		val period by optionalEnumChoice<StatsPeriod> {
			name = Translations.Arguments.Period.name
			description = Translations.Arguments.Period.description
			typeName = Translations.Arguments.Period.name
		}
	}

	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.Stats.name
			description = Translations.Commands.Stats.description

			guild(ROCKET_PUB_GUILD)

			publicSubCommand(::PeriodArguments) {
				name = Translations.Commands.Stats.Verifications.name
				description = Translations.Commands.Stats.Verifications.description

				action {
					val since = arguments.period.since()
					respondWithChart(
						data = getVerificationCountsByDay(since),
						since = since,
						fileName = "verifications.png",
						title = Translations.Embeds.Stats.Verifications.title.translate(),
					)
				}
			}

			publicSubCommand(::PeriodArguments) {
				name = Translations.Commands.Stats.Ads.name
				description = Translations.Commands.Stats.Ads.description

				action {
					val since = arguments.period.since()
					respondWithChart(
						data = getAdCountsByDay(since),
						since = since,
						fileName = "ads.png",
						title = Translations.Embeds.Stats.Ads.title.translate(),
					)
				}
			}
		}
	}
}

private suspend fun PublicInteractionContext.respondWithChart(
	data: List<DailyCount>,
	since: Instant,
	fileName: String,
	title: String,
) {
	respond {
		if (data.isEmpty()) {
			content = Translations.Messages.noDataForPeriod.translate()
			return@respond
		}

		// The query only returns days having at least one row, the gaps must be drawn as zeroes.
		val from = maxOf(since.atZone(ZoneId.systemDefault()).toLocalDate(), data.first().day)
		val to = maxOf(LocalDate.now(), data.last().day)
		val series = data.fillMissingDays(from, to)

		val total = series.sumOf { it.count }
		val peak = series.maxBy { it.count }
		val average = total.toDouble() / series.size

		addFile(fileName, ChannelProvider { ByteReadChannel(renderDailyCountChart(title, series)) })

		completeEmbed(interactionResponse.kord, title, "") {
			color = Color(CHART_ACCENT_HEX)
			image = "attachment://$fileName"

			description = Translations.Embeds.Stats.description.translateNamed(
				"from" to from.atStartOfDay(ZoneId.systemDefault()).toInstant().toKotlinInstant()
					.toMessageFormat(DiscordTimestampStyle.LongDate),
				"to" to to.atStartOfDay(ZoneId.systemDefault()).toInstant().toKotlinInstant()
					.toMessageFormat(DiscordTimestampStyle.LongDate),
				"days" to series.size,
			)

			field {
				name = Translations.Fields.total.translate()
				value = "**$total**"
				inline = true
			}

			field {
				name = Translations.Fields.averagePerDay.translate()
				value = "**${"%.1f".format(average)}**"
				inline = true
			}

			field {
				name = Translations.Fields.peakDay.translate()
				value = Translations.Embeds.Stats.peakValue.translateNamed(
					"count" to peak.count,
					"date" to peak.day.atStartOfDay(ZoneId.systemDefault()).toInstant().toKotlinInstant()
						.toMessageFormat(DiscordTimestampStyle.ShortDate),
				)
				inline = true
			}
		}
	}
}
