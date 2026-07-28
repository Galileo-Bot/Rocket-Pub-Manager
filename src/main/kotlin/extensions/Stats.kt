package extensions

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
import storage.DailyCount
import storage.getAdCountsByDay
import storage.getVerificationCountsByDay
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed
import utils.renderDailyCountChart
import java.time.Instant
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
					respondWithChart(
						data = getVerificationCountsByDay(arguments.period.since()),
						fileName = "verifications.png",
						title = Translations.Embeds.Stats.Verifications.title.translate(),
					)
				}
			}

			publicSubCommand(::PeriodArguments) {
				name = Translations.Commands.Stats.Ads.name
				description = Translations.Commands.Stats.Ads.description

				action {
					respondWithChart(
						data = getAdCountsByDay(arguments.period.since()),
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
	fileName: String,
	title: String,
) {
	respond {
		if (data.isEmpty()) {
			content = Translations.Messages.noDataForPeriod.translate()
			return@respond
		}

		addFile(fileName, ChannelProvider { ByteReadChannel(renderDailyCountChart(title, data)) })
		completeEmbed(interactionResponse.kord, title, "") { image = "attachment://$fileName" }
	}
}
