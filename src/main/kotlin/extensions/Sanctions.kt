package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.duration
import com.kotlindiscord.kord.extensions.commands.converters.impl.enum
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import storage.SanctionType
import storage.getSanctionCount
import storage.modifySanction
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed

enum class ModifySanctionValues(val translation: String) {
	APPLIED_BY("Appliquée par"),
	DURATION("Durée"),
	REASON("Raison"),
	TYPE("ID"),
}

@OptIn(KordPreview::class)
class Sanctions : Extension() {
	override val name: String = "Sanctions"
	
	abstract class ModifySanction : Arguments() {
		val case by int("cas", "Le numéro de la sanction à modifier.")
	}
	
	class ModifySanctionTypeArguments : ModifySanction() {
		val type by enum("type", "Type de la sanction.", "type", { SanctionType.valueOf(it.uppercase()) })
	}
	
	class ModifyReasonArguments : ModifySanction() {
		val reason by coalescedString("raison", "Raison de la sanction.")
	}
	
	class ModifyDurationArguments : ModifySanction() {
		val duration by duration("duree", "Durée de la sanction.")
	}
	
	class ModifyAppliedByArguments : ModifySanction() {
		val appliedBy by member("modérateur", "Par qui la sanction a été appliquée.", { ROCKET_PUB_GUILD }) { _, member ->
			if (!isStaff(member)) {
				throw DiscordRelayedException("La personne n'a pas le rôle staff et n'est donc pas modérateur, impossible de l'utiliser.")
			}
		}
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "sanctions"
			description = "Permet de gérer les sanctions du serveur."
			
			publicSubCommand {
				name = "compte"
				description = "Permet d'avoir le nombre de sanctions mises par les modérateurs."
				
				check { isStaff() }
				
				action {
					val sanctions = getSanctionCount()
					
					respond {
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
		
		publicSlashCommand {
			name = "modifier"
			description = "Permet de modifier une sanction."
			
			publicSubCommand(::ModifyAppliedByArguments) {
				name = "modérateur"
				description = "Permet de modifier le modérateur de la sanction."
				
				action {
					modifySanction(arguments.case, ModifySanctionValues.APPLIED_BY, arguments.appliedBy.id.asString)
				}
			}
			
			publicSubCommand(::ModifyDurationArguments) {
				name = "durée"
				description = "Permet de modifier la durée de la sanction."
				
				action {
					modifySanction(arguments.case, ModifySanctionValues.DURATION, arguments.duration.toString())
				}
			}
			
			publicSubCommand(::ModifyReasonArguments) {
				name = "raison"
				description = "Permet de modifier la raison de la sanction."
				
				action {
					modifySanction(arguments.case, ModifySanctionValues.REASON, arguments.reason)
				}
			}
			
			publicSubCommand(::ModifySanctionTypeArguments) {
				name = "type"
				description = "Permet de modifier le type de la sanction."
				
				action {
					modifySanction(arguments.case, ModifySanctionValues.TYPE, arguments.type.name)
				}
			}
		}
	}
}
