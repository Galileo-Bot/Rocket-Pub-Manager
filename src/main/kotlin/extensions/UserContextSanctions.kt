package extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.modules.unsafe.annotations.UnsafeAPI
import com.kotlindiscord.kord.extensions.modules.unsafe.commands.UnsafeUserCommand
import com.kotlindiscord.kord.extensions.modules.unsafe.extensions.unsafeUserCommand
import com.kotlindiscord.kord.extensions.modules.unsafe.types.InitialUserCommandResponse
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import storage.Sanction
import storage.SanctionType
import utils.getLogSanctionsChannel
import utils.sanctionEmbed
import java.util.*

class UserContextSanctions : Extension() {
	override val name = "UserContextSanctions"
	private val modalsTargets = mutableMapOf<String, Snowflake>()
	
	@OptIn(UnsafeAPI::class)
	override suspend fun setup() {
		event<GuildModalSubmitInteractionCreateEvent> {
			action {
				if (event.interaction.modalId in modalsTargets) {
					val commandName = event.interaction.modalId.substringBefore(" ").uppercase()
					val sanctionType = SanctionType.valueOf(commandName)
					
					val r = event.interaction.deferPublicResponse()
					
					val userId = modalsTargets[event.interaction.modalId] ?: return@action
					val reason = event.interaction.actionRows[0].textInputs["reason"]?.value ?: return@action
					val target = event.interaction.getGuildOrNull()?.getMemberOrNull(userId) ?: return@action
					val author = event.interaction.user
					
					Sanction(sanctionType, reason, target.id, appliedBy = author.id).apply {
						r.respond {
							sanctionEmbed(kord, this@apply)
						}
						
						save()
						sendLog()
						
						when(sanctionType) {
							SanctionType.LIGHT_WARN -> {
								kord.getLogSanctionsChannel().lightSanction(target, reason)
								return@apply
							}
							SanctionType.BAN -> target.ban {
								this.reason = reason
								deleteMessagesDays = 14
							}
							SanctionType.KICK -> target.kick(reason)
							else -> {}
						}
					}
				}
			}
		}
		
		val userCommandsSanctionTypes = listOf("ban", "kick", "light_warn", "warn")
		userCommandsSanctionTypes.forEach {
			unsafeUserCommand {
				createSanctionCommand(it)
			}
		}
	}
	
	@OptIn(UnsafeAPI::class)
	private fun UnsafeUserCommand.createSanctionCommand(type: String) {
		name = type.replace("_", " ")
		initialResponse = InitialUserCommandResponse.None
		
		action {
			val randomId = UUID.randomUUID().toString()
			val customId = "$type $randomId"
			modalsTargets[customId] = event.interaction.targetId
			
			event.interaction.modal("Avertissement de membre", customId) {
				actionRow {
					textInput(TextInputStyle.Paragraph, "reason", "Raison") {
						allowedLength = 3..500
						placeholder = "Insulte le staff..."
						required = true
					}
				}
			}
		}
	}
}
