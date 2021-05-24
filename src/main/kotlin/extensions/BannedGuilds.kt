package extensions

import com.kotlindiscord.kord.extensions.commands.converters.enum
import com.kotlindiscord.kord.extensions.commands.converters.string
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.commands.slash.AutoAckType
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.common.annotation.KordPreview
import storage.addBannedGuild
import storage.modifyGuildValue
import storage.removeBannedGuild
import storage.searchBannedGuild
import utils.ROCKET_PUB_GUILD
import utils.bannedGuildEmbed
import utils.modifiedGuildEmbed


fun isValidGuild(guild: String): Boolean {
	return guild.matches(Regex("\\d{17,19}|.{2,100}", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)))
}

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
		slashCommand {
			name = "serveurs"
			description = "Permet de gérer les serveurs interdits."
			guild = ROCKET_PUB_GUILD
			
			subCommand(::AddBannedGuildArguments) {
				name = "add"
				description = "Ajoute un serveur à la liste des serveurs interdits."
				autoAck = AutoAckType.PUBLIC
				
				action {
					publicFollowUp {
						content = if (isValidGuild(arguments.guild)) {
							addBannedGuild(arguments.guild, arguments.reason)
							"Serveur `${arguments.guild}` ajouté !"
						} else {
							"Cela ne semble ni être un ID de guild, ni un nom de guild :eyes:"
						}
					}
				}
			}
			
			
			subCommand(::GetBannedGuildArguments) {
				name = "get"
				description = "Permet d'avoir des informations sur un serveur interdit."
				autoAck = AutoAckType.PUBLIC
				
				action {
					publicFollowUp {
						val guild = searchBannedGuild(arguments.guild)
						if (guild != null) embed {
							bannedGuildEmbed(bot.getKoin().get(), guild)()
						}
						else content = "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits."
					}
				}
			}
			
			subCommand(::ModifyBannedGuildArguments) {
				name = "modifier"
				description = "Permet de modifier un serveur interdit."
				autoAck = AutoAckType.PUBLIC
				
				action {
					publicFollowUp {
						val guild = searchBannedGuild(arguments.guild)
						if (guild != null) {
							modifyGuildValue(arguments.guild, arguments.value, arguments.newValue)
							embed {
								modifiedGuildEmbed(bot.getKoin().get(), guild, arguments.value, guild[arguments.value], arguments.newValue)()
							}
						} else content = "Ce serveur n'a pas été trouvé dans la liste des serveurs interdits."
					}
				}
			}
			
			subCommand(::RemoveBannedGuildArguments) {
				name = "remove"
				description = "Retire un serveur de la liste des serveurs interdits."
				autoAck = AutoAckType.PUBLIC
				
				action {
					publicFollowUp {
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
