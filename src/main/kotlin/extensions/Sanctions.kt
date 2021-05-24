package extensions

import com.kotlindiscord.kord.extensions.commands.slash.AutoAckType
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.common.annotation.KordPreview
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
		slashCommand {
			name = "sanctions"
			description = "Permet de gérer les sanctions du serveur."
			guild = ROCKET_PUB_GUILD
			
			subCommand {
				name = "compte"
				description = "Permet d'avoir le nombre de sanctions mises par les modérateurs."
				autoAck = AutoAckType.PUBLIC
				
				action {
					if (!isStaff(this)) publicFollowUp {
						content = "Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise."
						return@action
					}
					
					val sanctions = getSanctionCount()
					
					publicFollowUp {
						embed {
							completeEmbed(
								bot.getKoin().get(),
								"Liste des sanctions appliquées.",
								sanctions.groupBy { it }.map {
									"**${guild!!.getMember(it.key).tag}** : ${it.value.size} sanctions appliquées."
								}.joinToString("\n\n")
							)()
						}
					}
				}
			}
			/*
			subCommand {
				name = "modify"
				description = "Permet de modifier une sanction."
				autoAck = AutoAckType.PUBLIC
				action {
				
				}
			}*/
		}
	}
}
