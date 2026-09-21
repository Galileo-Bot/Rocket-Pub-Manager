package extensions

import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.i18n.Key
import dev.kordex.core.time.TimestampType
import dev.kordex.core.time.toDiscord
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Instant
import storage.*
import utils.bannedGuildEmbed
import utils.completeEmbed
import utils.cutFormatting
import utils.modifiedGuildEmbed
import kotlin.time.ExperimentalTime


private val GUILD_ID_OR_NAME_REGEX = Regex("\\d{17,19}|.{2,100}", RegexOption.DOT_MATCHES_ALL)
private val INVITATION_REGEX = Regex(
	"\\b(?:https?://)?(?:www\\.)?(?:discord\\.(?:gg|io|me|li)|discordapp\\.com/invite)/[a-zA-Z0-9]+(?:\\?[a-zA-Z0-9]+=[a-zA-Z0-9]+(&[a-zA-Z0-9]+=[a-zA-Z0-9]+)*)?\\b"
)

fun isValidGuildId(value: String) = value.matches(GUILD_ID_OR_NAME_REGEX)
fun isValidInvitation(value: String) = value.matches(INVITATION_REGEX)

enum class ModifyGuildValues(val translation: Key, val column: String) : ChoiceEnum {
	NAME(Translations.Fields.name, "name"),
	ID(Translations.Fields.id, "id"),
	REASON(Translations.Fields.reason, "reason");

	override val readableName = translation
}

class BannedGuilds : Extension() {
	override val name = "Banned-Guilds"

	class AddBannedGuildArguments : Arguments() {
		/**
		 * Could be name or Snowflake.
		 */
		val guild by string {
			name = Translations.Arguments.Guild.name
			description = Translations.Arguments.Guild.description
		}

		val reason by string {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
		}
	}

	class GetBannedGuildArguments : Arguments() {
		val guild by string {
			name = Translations.Arguments.Guild.name
			description = Translations.Arguments.Guild.description
		}
	}

	class RemoveBannedGuildArguments : Arguments() {
		val guild by string {
			name = Translations.Arguments.Guild.name
			description = Translations.Arguments.Guild.description
		}
	}

	class ModifyBannedGuildArguments : Arguments() {
		val guild by string {
			name = Translations.Arguments.Guild.name
			description = Translations.Arguments.Guild.description
		}

		val value by enumChoice<ModifyGuildValues> {
			name = Translations.Arguments.Property.name
			description = Translations.Arguments.Property.description
			typeName = Translations.Arguments.Property.name
		}

		val newValue by string {
			name = Translations.Arguments.Value.name
			description = Translations.Arguments.Value.description
		}
	}

	@OptIn(AlwaysPublicResponse::class, ExperimentalTime::class)
	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.BannedGuilds.name
			description = Translations.Commands.BannedGuilds.description
			staffOnly()

			publicSubCommand(::AddBannedGuildArguments) {
				name = Translations.Commands.BannedGuilds.Add.name
				description = Translations.Commands.BannedGuilds.Add.description

				action {
					respond {
						// An invite matches the loose name pattern too, so it has to be checked first to keep the guild's ID.
						content = when {
							isValidInvitation(arguments.guild) -> {
								val invitation = this@publicSubCommand.kord.getInviteOrNull(arguments.guild.substringAfterLast('/').substringBefore('?'))

								addBannedGuild(arguments.guild, arguments.reason, invitation?.partialGuild?.id)
								Translations.Messages.guildAdded.translateNamed("guild" to arguments.guild)
							}

							isValidGuildId(arguments.guild) -> {
								addBannedGuild(arguments.guild, arguments.reason)
								Translations.Messages.guildAdded.translateNamed("guild" to arguments.guild)
							}

							else -> Translations.Errors.invalidGuildId.translate()
						}
					}
				}
			}

			publicSubCommand(::GetBannedGuildArguments) {
				name = Translations.Commands.BannedGuilds.Get.name
				description = Translations.Commands.BannedGuilds.Get.description

				action {
					val guild = searchBannedGuild(arguments.guild)

					respond {
						if (guild != null) bannedGuildEmbed(this@publicSlashCommand.kord, guild)
						else content = Translations.Errors.guildNotFound.translate()
					}
				}
			}

			publicSubCommand {
				name = Translations.Commands.BannedGuilds.List.name
				description = Translations.Commands.BannedGuilds.List.description

				action {
					val bannedGuilds = getAllBannedGuilds()

					if (bannedGuilds.isEmpty()) {
						respond(Translations.Messages.noBannedGuilds.translate())
						return@action
					}

					respondingPaginator {
						bannedGuilds.chunked(20).forEach { bannedGuildListChunk ->
							page {
								val list = bannedGuildListChunk.map { (name, id, reason, bannedSince) ->
									val date =
										Instant.fromEpochMilliseconds(bannedSince.time)
											.toDiscord(TimestampType.RelativeTime)
									val result = "${name ?: id} ${id?.run { "`($this)`" } ?: ""} $date"
									"$result - ${reason.cutFormatting(100 - result.length)}"
								}

								completeEmbed(
									client = this@publicSubCommand.kord,
									title = Translations.Embeds.BannedGuilds.List.title.translate(),
									description = Translations.Embeds.BannedGuilds.List.description.translateNamed(
										"list" to list.joinToString("\n"),
										"command" to this@publicSlashCommand.name
									)
								)
							}
						}
					}.send()
				}
			}

			publicSubCommand(::ModifyBannedGuildArguments) {
				name = Translations.Commands.BannedGuilds.Modify.name
				description = Translations.Commands.BannedGuilds.Modify.description

				action {
					val guild = searchBannedGuild(arguments.guild)
					if (guild != null) modifyGuildValue(arguments.guild, arguments.value, arguments.newValue)

					respond {
						if (guild != null) modifiedGuildEmbed(
							this@publicSlashCommand.kord,
							guild,
							arguments.value,
							guild[arguments.value],
							arguments.newValue
						)
						else content = Translations.Errors.guildNotFound.translate()
					}
				}
			}

			publicSubCommand(::RemoveBannedGuildArguments) {
				name = Translations.Commands.BannedGuilds.Remove.name
				description = Translations.Commands.BannedGuilds.Remove.description

				action {
					val validGuild = isValidGuildId(arguments.guild)
					if (validGuild) removeBannedGuild(arguments.guild)

					respond(
						if (validGuild) Translations.Messages.guildRemoved.translateNamed("guild" to arguments.guild)
						else Translations.Errors.invalidGuildId.translate()
					)
				}
			}
		}
	}
}
