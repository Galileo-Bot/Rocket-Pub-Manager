package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingCoalescingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import com.kotlindiscord.kord.extensions.utils.canInteract
import com.kotlindiscord.kord.extensions.utils.selfMember
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.annotation.KordPreview
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.edit
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import storage.Sanction
import storage.containsSanction
import storage.getSanctionCount
import storage.getSanctions
import storage.removeSanction
import storage.removeSanctions
import utils.completeEmbed
import utils.sanctionEmbed
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration


enum class SanctionType(val translation: String, val emote: String) : ChoiceEnum {
	BAN("Bannissement", "<:ban:498482002601705482>"),
	KICK("Expulsion", "<:kick:933505066273501184>\n"),
	MUTE("Exclusion (mute)", "<:mute:933505777354834021>\n"),
	WARN("Avertissement", "⚠️");
	
	override val readableName = translation
}

enum class DurationUnits(translation: String, val durationUnit: DurationUnit) : ChoiceEnum {
	DAYS("jours", DurationUnit.DAYS),
	HOURS("heures", DurationUnit.HOURS),
	MINUTES("minutes", DurationUnit.MINUTES);
	
	override val readableName = translation
}

@OptIn(KordPreview::class)
class Sanctions : Extension() {
	override val name = "Sanctions"
	
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
	
	class MuteArguments : Arguments() {
		val member by member("membre", "Le membre à mute.")
		val duration by int("durée", "La durée du mute.")
		val unit by enumChoice<DurationUnits>("unité", "L'unité de la durée du mute.", "unité")
		val reason by defaultingCoalescingString("raison", "La raison du mute.", "Aucune raison donnée.")
	}
	
	class UnMuteArguments : Arguments() {
		val member by member("membre", "Le membre à unmute.")
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
					if (sanctions.isEmpty()) throw DiscordRelayedException("Aucune sanction n'a été appliquée à cet utilisateur.")
					
					respondingPaginator {
						sanctions.chunked(20).forEach {
							page {
								completeEmbed(
									client = bot.getKoin().get(),
									title = "Liste des sanctions appliquées à ${user.tag} (${user.id}).",
									description = it.joinToString("\n\n") {
										val duration = if (it.durationMS > 0) "\n**Duration** : ${it.formattedDuration}" else ""
										val appliedBy = it.appliedBy?.let { appliedById ->
											val getUserTag = runBlocking {
												this@publicSlashCommand.kord.getUser(
													appliedById,
													EntitySupplyStrategy.cacheWithCachingRestFallback
												)?.tag ?: "`$appliedById`"
											}
											
											"$getUserTag (`$appliedById`)"
										} ?: "Automatique ou non trouvé"
										
										"""
											> **Cas numéro ${it.id}** ${it.type.emote}
											**Type** : ${it.type.translation} $duration
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
							completeEmbed(
								this@publicSubCommand.kord,
								"${sanctions.size} Sanctions supprimées",
								"Les sanctions de ${arguments.user.mention} ont bien été supprimées avec succès."
							) {
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
		
		publicSlashCommand(::MuteArguments) {
			name = "mute"
			description = "Mute une personne en utilisant les timeout (éjections) discord."
			
			check { isStaff() }
			
			action {
				val duration = arguments.duration.toDuration(arguments.unit.durationUnit)
				if (arguments.member.timeoutUntil != null) throw DiscordRelayedException(
					"La personne est déjà mute et sera unmute ${
						arguments.member.timeoutUntil!!.toMessageFormat(
							DiscordTimestampStyle.RelativeTime
						)
					}."
				)
				if (duration < 2.minutes) throw DiscordRelayedException("La durée doit être d'au moins 2 minutes.")
				if (duration > 28.days) throw DiscordRelayedException("La durée doit être de moins de 28 jours.")
				
				Sanction(SanctionType.MUTE, arguments.reason, arguments.member.id, durationMS = duration.inWholeMilliseconds, appliedBy = user.id).apply {
					save()
					if (!guild!!.selfMember().canInteract(arguments.member)) {
						throw DiscordRelayedException("Je ne peux pas mute cet utilisateur, il doit avoir un rôle inférieur au mien.")
					}
					
					arguments.member.edit { timeoutUntil = Clock.System.now() + duration }
					respond {
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
		
		publicSlashCommand(::UnMuteArguments) {
			name = "unmute"
			description = "Permet de retirer le mute d'une personne (garde quand même la sanction)."
			
			check { isStaff() }
			
			action {
				if (!guild!!.selfMember().canInteract(arguments.member)) {
					throw DiscordRelayedException("Je ne peux pas retirer le mute cet utilisateur, il doit avoir un rôle inférieur au mien.")
				}
				arguments.member.timeoutUntil?.let {
					arguments.member.edit {
						timeoutUntil = null
					}.also {
						respond("Le membre a bien été un-mute.")
					}
				} ?: throw DiscordRelayedException("Cette personne n'est pas mute, je ne peux pas l'un-mute voyons...")
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
