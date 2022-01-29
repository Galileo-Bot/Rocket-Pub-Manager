package extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import storage.SanctionType
import storage.getVerificationCount
import utils.completeEmbed
import utils.getReasonForMessage

@OptIn(KordPreview::class)
class Verifications : Extension() {
	override val name: String = "Verifications"
	
	class VerifyMessageArguments : Arguments() {
		val message by message("message", "Le message à vérifier.", requireGuild = true)
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "verif"
			description = "Permet de voir les vérifications du staff."
			
			check { isStaff() }
			
			publicSubCommand {
				name = "list"
				description = "Permet de voir les vérifications du staff."
				
				action {
					val verificationCount = getVerificationCount()
					val verifications = verificationCount.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
					
					respond {
						completeEmbed(
							this@publicSubCommand.kord, "Liste des publicités vérifiées.", verifications.map {
								"**${guild!!.getMember(it.first).tag}** : ${it.second} publicités vérifiées."
							}.joinToString("\n\n")
						)
					}
				}
			}
			
			publicSubCommand(::VerifyMessageArguments) {
				name = "message"
				description = "Permet de vérifier un message."
				
				action {
					val message = arguments.message.fetchMessage()
					
					getReasonForMessage(message)?.let {
						autoSanctionMessage(message, SanctionType.WARN, it)
					} ?: verificationMessage(message)
				}
			}
		}
	}
}
