package extensions

import com.kotlindiscord.kord.extensions.commands.slash.AutoAckType
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.common.annotation.KordPreview
import storage.getVerificationCount
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed

@OptIn(KordPreview::class)
class Verifications : Extension() {
	override val name: String = "Verifications"
	
	override suspend fun setup() {
		slashCommand {
			name = "verif"
			description = "Permet de voir les vérifications du staff."
			autoAck = AutoAckType.PUBLIC
			guild = ROCKET_PUB_GUILD
			
			action {
				if (!isStaff(this)) publicFollowUp {
					content = "Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise."
					return@action
				}
				
				val verifications = getVerificationCount()
				
				publicFollowUp {
					embed {
						completeEmbed(
							bot.getKoin().get(),
							"Liste des publicités vérifiées.",
							verifications.groupBy { it }.map {
								"**${guild!!.getMember(it.key).tag}** : ${it.value.size} publicités vérifiées."
							}.joinToString("\n\n")
						)()
					}
				}
			}
		}
	}
}
