package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.duration
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import dev.kord.common.annotation.KordPreview
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.runBlocking
import storage.Sanction
import storage.SanctionType
import storage.containsSanction
import storage.getSanctionCount
import storage.getSanctions
import storage.modifySanction
import storage.removeSanction
import storage.removeSanctions
import utils.ROCKET_PUB_GUILD
import utils.completeEmbed
import utils.sanctionEmbed

enum class ModifySanctionValues(val translation: String) {
	APPLIED_BY("Appliquée par"),
	DURATION("Durée"),
	REASON("Raison"),
	TYPE("ID"),
}

@OptIn(KordPreview::class)
class Sanctions : Extension() {
	override val name: String = "Sanctions"
	
	class DeleteSanctionArguments : Arguments() {
		val id by int("cas", "Le numéro de la sanction à supprimer.")
	}
	
	class DeleteAllSanctionsArguments : Arguments() {
		val user by user("user", "L'utilisateur à qui supprimer toutes les sanctions.")
		val type by optionalEnumChoice<SanctionType>("type", "Le type de sanctions à supprimer.", "type")
	}
	
	class ListSanctionsArguments : Arguments() {
		val user by user("user", "L'utilisateur à qui afficher les sanctions.")
	}
	
	abstract class ModifySanction : Arguments() {
		val id by int("cas", "Le numéro de la sanction à modifier.")
	}
	
	class ModifyAppliedByArguments : ModifySanction() {
		val appliedBy by member("modérateur", "Par qui la sanction a été appliquée.", { ROCKET_PUB_GUILD }) { _, member ->
			if (!isStaff(member)) {
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
	
	class WarnArguments : Arguments() {
		val member by member("membre", "L'utilisateur à qui avertir.")
		val reason by coalescedString("raison", "Raison de l'avertissement.")
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
			
			publicSubCommand(::ListSanctionsArguments) {
				name = "liste"
				description = "Permet d'avoir la liste des sanctions appliquées à un utilisateur."
				
				action {
					val user = arguments.user
					val sanctions = getSanctions(user.id)
					if (sanctions.isEmpty()) respond("Aucune sanction n'a été appliquée à cet utilisateur.").also { return@action }
					
					respondingPaginator {
						sanctions.chunked(20).forEach {
							page {
								completeEmbed(
									client = bot.getKoin().get(),
									title = "Liste des sanctions appliquées à ${user.tag} (${user.id.asString}).",
									description = it.joinToString("\n\n") {
										val duration = if (it.durationMS > 0) "\n**Duration** : ${it.formattedDuration}" else ""
										val appliedBy = it.appliedBy?.let { appliedById ->
											val getUserTag = runBlocking {
												this@publicSlashCommand.kord.getUser(
													appliedById,
													EntitySupplyStrategy.cacheWithCachingRestFallback
												)?.tag ?: appliedById.asString
											}
											
											"$getUserTag (${appliedById.asString})"
										} ?: "Automatique ou non trouvé"
										
										"""
											> **Cas numéro ${it.id}**
											**Type** : ${it.type.name.lowercase()} $duration
											**Raison** : ${it.reason}
											**Appliquée par** : $appliedBy
										""".trimIndent()
									}
								)
							}
						}
					}.send()
					
				}
			}
			
			publicSubCommand(::DeleteSanctionArguments) {
				name = "supprimer"
				description = "Permet de supprimer une sanction via son numéro de cas."
				
				action {
					respond {
						if (containsSanction(arguments.id)) {
							removeSanction(arguments.id)
							completeEmbed(this@publicSubCommand.kord, "Sanction supprimée.", "La sanction a été supprimée avec succès.")
						} else {
							content = "Sanction non trouvée."
						}
					}
				}
			}
			
			publicSubCommand(::DeleteAllSanctionsArguments) {
				name = "supprimer-toutes"
				description = "Permet de supprimer toutes les sanctions d'un utilisateur."
				
				action {
					respond {
						val sanctions = getSanctions(arguments.user.id)
						
						if (arguments.type != null && sanctions.none { it.type == arguments.type }) {
							content = "Cet utilisateur n'a pas de sanctions de ce type."
						} else if (sanctions.isEmpty()) {
							content = "Cet utilisateur n'a déjà aucune sanction."
						} else {
							removeSanctions(arguments.user.id, arguments.type?.toString())
							embed {
								completeEmbed(
									this@publicSubCommand.kord,
									"${sanctions.size} Sanctions supprimées",
									"Les sanctions de ${arguments.user.mention} ont bien été supprimées avec succès."
								)
								
								field {
									name = "Types"
									value = sanctions.groupBy { it.type }.map { "${it.key.translation + "s"} : **${it.value.size}**" }.joinToString("\n")
								}
							}
						}
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
					modifySanction(arguments.id, ModifySanctionValues.APPLIED_BY, arguments.appliedBy.id.asString)
				}
			}
			
			publicSubCommand(::ModifyDurationArguments) {
				name = "durée"
				description = "Permet de modifier la durée de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.DURATION, arguments.duration.toString())
				}
			}
			
			publicSubCommand(::ModifyReasonArguments) {
				name = "raison"
				description = "Permet de modifier la raison de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.REASON, arguments.reason)
				}
			}
			
			publicSubCommand(::ModifySanctionTypeArguments) {
				name = "type"
				description = "Permet de modifier le type de la sanction."
				
				action {
					modifySanction(arguments.id, ModifySanctionValues.TYPE, arguments.type.name)
				}
			}
		}
		
		publicSlashCommand(::WarnArguments) {
			name = "warn"
			description = "Avertit un membre, enregistre cette sanction."
			
			check { isStaff() }
			
			action {
				Sanction(SanctionType.WARN, arguments.reason, arguments.member.id, appliedBy = user.id).apply {
					save()
					respond {
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
	}
}
