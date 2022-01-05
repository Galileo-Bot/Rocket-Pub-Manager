package extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.enum
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import storage.addBannedGuild
import storage.modifyGuildValue
import storage.removeBannedGuild
import storage.searchBannedGuild
import utils.ROCKET_PUB_GUILD
import utils.bannedGuildEmbed
import utils.modifiedGuildEmbed


fun isValidGuild(string: String) = string.matches(Regex("\\d{17,19}|.{2,100}", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)))

enum class ModifyGuildValues(val translation: String) {
	NAME("Nom"),
	ID("ID"),
	REASON("Raison")
}

@OptIn(KordPreview::class)
class BannedGuilds : Extension() {
	override val name = "bannedGuilds"
	
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
		val value by enum<ModifyGuildValues>("propriété", "La propriété à modifier.", "nom/raison/id")
		val newValue by string("valeur", "La nouvelle valeur à utiliser.")
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "serveurs"
			description = "Permet de gérer les serveurs interdits."
			guild(ROCKET_PUB_GUILD)
			
			publicSubCommand(::AddBannedGuildArguments) {
				name = "add"
				description = "Ajoute un serveur à la liste des serveurs interdits."
				
				action {
					respond {
						content = if (isValidGuild(arguments.guild)) {
							addBannedGuild(arguments.guild, arguments.reason)
							"Serveur `${arguments.guild}` ajouté !"
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
			
			publicSubCommand(::ModifyBannedGuildArguments) {
				name = "modifier"
				description = "Permet de modifier un serveur interdit."
				
				action {
					respond {
						val guild = searchBannedGuild(arguments.guild)
						if (guild != null) {
							modifyGuildValue(arguments.guild, arguments.value, arguments.newValue)
							modifiedGuildEmbed(bot.getKoin().get(), guild, arguments.value, guild[arguments.value], arguments.newValue)
						} else content = "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits."
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
								"Serveur `${arguments.guild}` retiré !"
							} else {
								"Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"
							}
					}
				}
			}
		}
	}
}
