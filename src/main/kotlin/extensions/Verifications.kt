package extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import dev.kord.rest.builder.message.create.embed
import storage.getVerificationCount
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed

@OptIn(KordPreview::class)
class Verifications : Extension() {
	override val name: String = "Verifications"
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "verif"
			description = "Permet de voir les vérifications du staff."
			
			guild(ROCKET_PUB_GUILD)
			
			action {
				if (!isStaff(member)) respond {
					content = "Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise."
					return@action
				}
				
				val verifications = getVerificationCount()
				
				respond {
					embed {
						completeEmbed(
							bot.getKoin().get(),
							"Liste des publicités vérifiées.",
							verifications.groupBy { it }.map {
								"**${guild!!.getMember(it.key).tag}** : ${it.value.size} publicités vérifiées."
							}.joinToString("\n\n")
						)
					}
				}
			}
		}
	}
}
