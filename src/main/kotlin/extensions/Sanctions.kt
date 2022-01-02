package extensions

import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import dev.kord.rest.builder.message.create.embed
import storage.getSanctionCount
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed

enum class ModifySanctionValues(val translation: String) {
	NAME("Nom"),
	TYPE("ID"),
	REASON("Raison"),
	DURATION("Durée"),
	APPLIED_BY("Appliquée par")
}

@OptIn(KordPreview::class)
class Sanctions : Extension() {
	override val name: String = "Sanctions"
	override suspend fun setup() {
		publicSlashCommand {
			name = "sanctions"
			description = "Permet de gérer les sanctions du serveur."
			guild(ROCKET_PUB_GUILD)
			
			publicSubCommand {
				name = "compte"
				description = "Permet d'avoir le nombre de sanctions mises par les modérateurs."
				
				action {
					if (!isStaff(member)) respond {
						content = "Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise."
						return@action
					}
					
					val sanctions = getSanctionCount()
					
					respond {
						embed {
							completeEmbed(
								bot.getKoin().get(),
								"Liste des sanctions appliquées.",
								sanctions.groupBy { it }.map {
									"**${guild!!.getMember(it.key).tag}** : ${it.value.size} sanctions appliquées."
								}.joinToString("\n\n")
							)
						}
					}
				}
			}
			/*
			publicSubCommand {
				name = "modify"
				description = "Permet de modifier une sanction."
				
				action {
				
				}
			}*/
		}
	}
}
