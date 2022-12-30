package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralUserCommand
import dev.kord.core.behavior.ban
import storage.Sanction
import storage.SanctionType
import utils.ROCKET_PUB_GUILD
import utils.getLogSanctionsChannel
import kotlin.time.Duration.Companion.days

class UserContextSanctions : Extension() {
	override val name = "UserContextSanctions"
	
	inner class ModalArguments : ModalForm() {
		override var title = "Sanctionner un membre"
		
		val reason = paragraphText {
			label = "Raison"
			maxLength = 500
			minLength = 3
			placeholder = "Insulte le staff..."
			required = true
		}
	}
	
	override suspend fun setup() {
		val userCommandsSanctionTypes = listOf("ban", "kick", "light_warn", "warn")
		userCommandsSanctionTypes.forEach { commandName ->
			ephemeralUserCommand(::ModalArguments) {
				name = commandName
				guildId = ROCKET_PUB_GUILD
				
				check {
					inGuild(ROCKET_PUB_GUILD)
				}
				
				action { modal ->
					if (modal == null || modal.reason.value.isNullOrBlank()) {
						respond("Veuillez remplir le formulaire.")
						return@action
					}
					
					val sanctionType = SanctionType.valueOf(commandName.uppercase())
					val reason = modal.reason.value!!
					val target = event.interaction.target.asMember(guild!!.id)
					val author = event.interaction.user
					
					Sanction(sanctionType, reason, target.id, appliedBy = author.id).apply {
						val kord = this@ephemeralUserCommand.kord
						
						replyWithSanctionEmbed()
						save()
						sendLog(kord)
						
						when (sanctionType) {
							SanctionType.LIGHT_WARN -> {
								kord.getLogSanctionsChannel().lightSanction(target, reason)
								return@apply
							}
							
							SanctionType.BAN -> target.ban {
								this.reason = reason
								deleteMessageDuration = 7.days
							}
							
							SanctionType.KICK -> target.kick(reason)
							else -> {}
						}
					}
				}
			}
		}
	}
}
