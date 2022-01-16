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
import dev.kord.common.annotation.KordPreview
import kotlinx.datetime.toKotlinInstant
import storage.addBannedGuild
import storage.getAllBannedGuilds
import storage.modifyGuildValue
import storage.removeBannedGuild
import storage.searchBannedGuild
import utils.bannedGuildEmbed
import utils.completeEmbed
import utils.cutFormatting
import utils.modifiedGuildEmbed


fun isValidGuild(string: String) = string.matches(Regex("\\d{17,19}|.{2,100}", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)))

enum class ModifyGuildValues(val translation: String) : ChoiceEnum {
	NAME("Nom"),
	ID("ID"),
	REASON("Raison");
	
	override val readableName = translation
}

@OptIn(KordPreview::class)
class BannedGuilds : Extension() {
	override val name = "BannedGuilds"
	
	class AddBannedGuildArguments : Arguments() {
		/**
		 * Could be name or Snowflake.
		 */
		val guild by string("serveur", "Le serveur à bannir.")
		val reason by string("raison", "La raison de pourquoi ce serveur est à bannir.")
	}
	
	class GetBannedGuildArguments : Arguments() {
		val guild by string("serveur", "Le serveur à récupérer.")
	}
	
	class RemoveBannedGuildArguments : Arguments() {
		val guild by string("serveur", "Le serveur à dé-bannir.")
	}
	
	class ModifyBannedGuildArguments : Arguments() {
		val guild by string("serveur", "Le serveur à modifier.")
		val value by enumChoice<ModifyGuildValues>("propriété", "La propriété à modifier.", "nom/raison/id")
		val newValue by string("valeur", "La nouvelle valeur à utiliser.")
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
						content = if (isValidGuild(arguments.guild)) {
							addBannedGuild(arguments.guild, arguments.reason)
							"Serveur `${arguments.guild}` ajouté à la liste des serveurs interdits !"
						} else {
							"Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"
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
							bannedGuildEmbed(bot.getKoin().get(), it)
						} ?: "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits.".also {
							this.content = it
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
					} else {
						respondingPaginator {
							bannedGuilds.chunked(20).forEach {
								page {
									val list = it.map {
										val date = it.bannedSince.toInstant().toKotlinInstant().toDiscord(TimestampType.RelativeTime)
										val result = "${it.name ?: it.id}${it.id?.run { "(`${this})`" } ?: ""} $date"
										"$result - ${it.reason.cutFormatting(80 - result.length)}"
									}
									
									completeEmbed(
										client = this@publicSubCommand.kord,
										title = "Liste des serveurs bannis",
										description = list.joinToString("\n") + "\n\nFaites `/${this@publicSlashCommand.name} get <id>` pour avoir plus d'informations sur un serveur."
									)
								}
							}
						}.send()
					}
				}
			}
			
			publicSubCommand(::ModifyBannedGuildArguments) {
				name = "modifier"
				description = "Permet de modifier un serveur interdit."
				
				action {
					respond {
						searchBannedGuild(arguments.guild)?.let {
							modifyGuildValue(arguments.guild, arguments.value, arguments.newValue)
							modifiedGuildEmbed(bot.getKoin().get(), it, arguments.value, it[arguments.value], arguments.newValue)
						} ?: "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits.".also { content = it }
					}
				}
			}
			
			publicSubCommand(::RemoveBannedGuildArguments) {
				name = "remove"
				description = "Retire un serveur de la liste des serveurs interdits."
				
				action {
					respond {
						content =
							if (isValidGuild(arguments.guild)) {
								removeBannedGuild(arguments.guild)
								"Serveur `${arguments.guild}` retiré de la liste des serveurs interdits !"
							} else {
								"Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"
							}
					}
				}
			}
		}
	}
}
