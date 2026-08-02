package extensions.sanctions

import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.coalescingString
import dev.kordex.core.commands.converters.impl.duration
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.commands.converters.impl.member
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kord.core.behavior.channel.createMessage
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import extensions.isStaff
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import storage.ModifySanctionValues
import storage.SanctionType
import storage.getSanction
import storage.modifySanction
import utils.ROCKET_PUB_GUILD
import utils.displayValue
import utils.getLogSanctionsChannel
import utils.modifiedSanctionEmbed


class ModifySanctions : Extension() {
	override val name = "Modify-Sanctions"

	abstract class ModifySanction : Arguments() {
		val id by int {
			name = Translations.Arguments.Case.name
			description = Translations.Arguments.Case.description
		}
	}

	class ModifyAppliedByArguments : ModifySanction() {
		val appliedBy by member {
			name = Translations.Arguments.Moderator.name
			description = Translations.Arguments.Moderator.description
			requiredGuild = { ROCKET_PUB_GUILD }
			validate {
				if (!value.isStaff()) {
					throw DiscordRelayedException(Translations.Errors.notStaffMember)
				}
			}
		}
	}

	class ModifyDurationArguments : ModifySanction() {
		val duration by duration {
			name = Translations.Arguments.Duration.name
			description = Translations.Arguments.Duration.description
		}
	}

	class ModifyReasonArguments : ModifySanction() {
		val reason by coalescingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
		}
	}

	class ModifySanctionTypeArguments : ModifySanction() {
		val type by enumChoice<SanctionType> {
			name = Translations.Arguments.Type.name
			description = Translations.Arguments.Type.description
			typeName = Translations.Arguments.Type.name
		}
	}

	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.ModifySanctions.name
			description = Translations.Commands.ModifySanctions.description

			publicSubCommand(ModifySanctions::ModifyAppliedByArguments) {
				name = Translations.Commands.ModifySanctions.Moderator.name
				description = Translations.Commands.ModifySanctions.Moderator.description

				action {
					applyModification(arguments.id, ModifySanctionValues.APPLIED_BY, arguments.appliedBy.id.toString())
				}
			}

			publicSubCommand(ModifySanctions::ModifyDurationArguments) {
				name = Translations.Commands.ModifySanctions.Duration.name
				description = Translations.Commands.ModifySanctions.Duration.description

				action {
					// The converter yields a calendar period, so it has to be resolved against a date to
					// give a number of milliseconds.
					val now = Clock.System.now()
					val durationMS = (now.plus(arguments.duration, TimeZone.currentSystemDefault()) - now)

					applyModification(arguments.id, ModifySanctionValues.DURATION, durationMS.inWholeMilliseconds)
				}
			}

			publicSubCommand(ModifySanctions::ModifyReasonArguments) {
				name = Translations.Commands.ModifySanctions.Reason.name
				description = Translations.Commands.ModifySanctions.Reason.description

				action {
					applyModification(arguments.id, ModifySanctionValues.REASON, arguments.reason)
				}
			}

			publicSubCommand(ModifySanctions::ModifySanctionTypeArguments) {
				name = Translations.Commands.ModifySanctions.Type.name
				description = Translations.Commands.ModifySanctions.Type.description

				action {
					// Types are stored lowercase, and `removeSanctions` filters on that form.
					applyModification(arguments.id, ModifySanctionValues.TYPE, arguments.type.name.lowercase())
				}
			}
		}
	}
}

/**
 * Applies the edit and reports it, both to the moderator and to the sanction logs.
 *
 * Unknown case numbers are rejected up-front, since the bare UPDATE silently matches no row.
 */
private suspend fun PublicSlashCommandContext<*, *>.applyModification(
	id: Int,
	column: ModifySanctionValues,
	newValue: Any?,
) {
	val before = getSanction(id) ?: throw DiscordRelayedException(
		Translations.Errors.sanctionNotFound.withNamedPlaceholders("id" to id.toString())
	)

	modifySanction(id, column, newValue)

	val kord = interactionResponse.kord
	val after = getSanction(id) ?: return
	val valueBefore = before.displayValue(column)
	val valueAfter = after.displayValue(column)

	respond {
		modifiedSanctionEmbed(kord, after, column, valueBefore, valueAfter, user.id)
	}

	kord.getLogSanctionsChannel().createMessage {
		modifiedSanctionEmbed(kord, after, column, valueBefore, valueAfter, user.id)
	}
}
