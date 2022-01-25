package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.duration
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import storage.modifySanction
import utils.ROCKET_PUB_GUILD


enum class ModifySanctionValues(val translation: String) {
	APPLIED_BY("Appliquée par"),
	DURATION("Durée"),
	REASON("Raison"),
	TYPE("Type"),
}

class ModifySanctions : Extension() {
	override val name = "Modify-Sanctions"
	
	abstract class ModifySanction : Arguments() {
		val id by int("cas", "Le numéro de la sanction à modifier.")
	}
	
	class ModifyAppliedByArguments : ModifySanction() {
		val appliedBy by member("modérateur", "Par qui la sanction a été appliquée.", { ROCKET_PUB_GUILD }) { _, member ->
			if (!member.isStaff()) {
				throw DiscordRelayedException("La personne n'a pas le rôle staff et n'est donc pas modérateur, impossible de l'utiliser.")
			}
		}
	}
	
	class ModifyDurationArguments : ModifySanction() {
		val duration by duration("duree", "Durée de la sanction.")
	}
	
	class ModifyReasonArguments : ModifySanction() {
		val reason by coalescedString("raison", "Raison de la sanction.")
	}
	
	class ModifySanctionTypeArguments : ModifySanction() {
		val type by enumChoice<SanctionType>("type", "Type de la sanction.", "type")
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "modifier"
			description = "Permet de modifier une sanction."
			
			publicSubCommand(ModifySanctions::ModifyAppliedByArguments) {
				name = "modérateur"
				description = "Permet de modifier le modérateur de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.APPLIED_BY, arguments.appliedBy.id.toString())
				}
			}
			
			publicSubCommand(ModifySanctions::ModifyDurationArguments) {
				name = "durée"
				description = "Permet de modifier la durée de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.DURATION, arguments.duration.toString())
				}
			}
			
			publicSubCommand(ModifySanctions::ModifyReasonArguments) {
				name = "raison"
				description = "Permet de modifier la raison de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.REASON, arguments.reason)
				}
			}
			
			publicSubCommand(ModifySanctions::ModifySanctionTypeArguments) {
				name = "type"
				description = "Permet de modifier le type de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.TYPE, arguments.type.name)
				}
			}
		}
	}
}
