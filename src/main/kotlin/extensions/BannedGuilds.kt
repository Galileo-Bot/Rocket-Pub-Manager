package extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import kotlinx.datetime.toKotlinInstant
import storage.*
import utils.bannedGuildEmbed
import utils.completeEmbed
import utils.cutFormatting
import utils.modifiedGuildEmbed


fun isValidGuildId(value: String) = value.matches(Regex("\\d{17,19}|.{2,100}", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)))
fun isValidInvitation(value: String) =
	value.matches(
		Regex(
			"\\b(?:https?://)?(?:www\\.)?(?:discord\\.(?:gg|io|me|li)|discordapp\\.com/invite)/[a-zA-Z0-9]+(?:\\?[a-zA-Z0-9]+=[a-zA-Z0-9]+(&[a-zA-Z0-9]+=[a-zA-Z0-9]+)*)?\\b",
			setOf(RegexOption.DOT_MATCHES_ALL)
		)
	)

enum class ModifyGuildValues(val translation: String) : ChoiceEnum {
	NAME("Nom"),
	ID("ID"),
	REASON("Raison");

	override val readableName = translation
}

class BannedGuilds : Extension() {
	override val name = "Banned-Guilds"

	class AddBannedGuildArguments : Arguments() {
		/**
		 * Could be name or Snowflake.
		 */
		val guild by string {
			name = "serveur"
			description = "Le serveur à bannir."
		}

		val reason by string {
			name = "raison"
			description = "La raison de pourquoi ce serveur est à bannir."
		}
	}

	class GetBannedGuildArguments : Arguments() {
		val guild by string {
			name = "serveur"
			description = "Le serveur à récupérer."
		}
	}

	class RemoveBannedGuildArguments : Arguments() {
		val guild by string {
			name = "serveur"
			description = "Le serveur à débannir."
		}
	}

	class ModifyBannedGuildArguments : Arguments() {
		val guild by string {
			name = "serveur"
			description = "Le serveur à modifier."
		}

		val value by enumChoice<ModifyGuildValues> {
			name = "propriété"
			description = "La propriété à modifier."
			typeName = "Nom/Id/Raison"
		}

		val newValue by string {
			name = "valeur"
			description = "La nouvelle valeur à utiliser."
		}
	}

	override suspend fun setup() {
		publicSlashCommand {
			name = "serveurs"
			description = "Permet de gérer les serveurs interdits."

			publicSubCommand(::AddBannedGuildArguments) {
				name = "add"
				description = "Ajoute un serveur à la liste des serveurs interdits."

				action {
					respond {
						content = when {
							isValidGuildId(arguments.guild) -> {
								addBannedGuild(arguments.guild, arguments.reason)
								"Serveur `${arguments.guild}` ajouté à la liste des serveurs interdits !"
							}

							isValidInvitation(arguments.guild) -> {
								val invitation = this@publicSubCommand.kord.getInviteOrNull(arguments.guild)

								addBannedGuild(arguments.guild, arguments.reason, invitation?.partialGuild?.id)
								"Serveur `${arguments.guild}` ajouté à la liste des serveurs interdits !"
							}

							else -> "Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"
						}
					}
				}
			}

			publicSubCommand(::GetBannedGuildArguments) {
				name = "get"
				description = "Permet d'avoir des informations sur un serveur interdit."

				action {
					respond {
						searchBannedGuild(arguments.guild)?.let {
							bannedGuildEmbed(this@publicSlashCommand.kord, it)
						} ?: "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits.".also {
							content = it
						}
					}
				}
			}

			publicSubCommand {
				name = "list"
				description = "Permet d'avoir la liste des serveurs interdits."

				action {
					val bannedGuilds = getAllBannedGuilds()

					if (bannedGuilds.isEmpty()) {
						respond("Aucun serveur interdit pour le moment.")
						return@action
					}

					respondingPaginator {
						bannedGuilds.chunked(20).forEach { bannedGuildListChunk ->
							page {
								val list = bannedGuildListChunk.map { (name, id, reason, bannedSince) ->
									val date = bannedSince.toInstant().toKotlinInstant().toDiscord(TimestampType.RelativeTime)
									val result = "${name ?: id} ${id?.run { "`($this)`" } ?: ""} $date"
									"$result - ${reason.cutFormatting(100 - result.length)}"
								}

								completeEmbed(
									client = this@publicSubCommand.kord,
									title = "Liste des serveurs bannis",
									description = "${list.joinToString("\n")}\n\nFaites `/${this@publicSlashCommand.name} get <id ou nom>` pour avoir plus d'informations sur un serveur."
								)
							}
						}
					}.send()
				}
			}

			publicSubCommand(::ModifyBannedGuildArguments) {
				name = "modifier"
				description = "Permet de modifier un serveur interdit."

				action {
					respond {
						val bannedGuildFound = searchBannedGuild(arguments.guild)?.let {
							modifyGuildValue(arguments.guild, arguments.value, arguments.newValue)
							modifiedGuildEmbed(bot.getKoin().get(), it, arguments.value, it[arguments.value], arguments.newValue)
						}

						bannedGuildFound ?: "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits.".also { content = it }
					}
				}
			}

			publicSubCommand(::RemoveBannedGuildArguments) {
				name = "remove"
				description = "Retire un serveur de la liste des serveurs interdits."

				action {
					respond {
						val validGuild = isValidGuildId(arguments.guild)

						content =
							if (validGuild) "Serveur `${arguments.guild}` retiré de la liste des serveurs interdits !"
							else "Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"

						if (validGuild) removeBannedGuild(arguments.guild)
					}
				}
			}
		}
	}
}
