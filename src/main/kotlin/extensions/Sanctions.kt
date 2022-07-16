package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.builders.ConverterBuilder
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescingDefaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import com.kotlindiscord.kord.extensions.utils.FilterStrategy
import com.kotlindiscord.kord.extensions.utils.canInteract
import com.kotlindiscord.kord.extensions.utils.selfMember
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.runBlocking
import storage.Sanction
import storage.SanctionType
import storage.containsSanction
import storage.getSanctionCount
import storage.getSanctions
import storage.removeSanction
import storage.removeSanctions
import utils.completeEmbed
import utils.sanctionEmbed
import utils.unBanEmbed
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration


val sanctions
	get() = mapOf(
		"spam" to "Spam de publicités.",
		"spam2" to "Spam de publicités après avertissements.",
		"interdit" to "Publicité interdite.",
		"description" to "Publicité sans description.",
		"suite" to "Publicités similaires à la chaîne.",
//		"lien" to "Lien de publicité interdit dans ${channel.mention}.",
		"categorie" to "Publicité dans la mauvaise catégorie.",
		"mp" to "Publicité par messages privés.",
		"fake" to "Spam de faux liens.",
		"mention" to "Tentative de mention interdite.",
		"invite reward" to "Publicité pour un serveur invite reward (interdit)."
	)

fun ConverterBuilder<String>.autoCompleteReason() {
	autoComplete {
		suggestString {
			sanctions.forEach { (key, value) -> choice(key, value) }
		}
	}
}

enum class DurationUnits(translation: String, val durationUnit: DurationUnit) : ChoiceEnum {
	DAYS("jours", DurationUnit.DAYS),
	HOURS("heures", DurationUnit.HOURS),
	MINUTES("minutes", DurationUnit.MINUTES);
	
	override val readableName = translation
}

class Sanctions : Extension() {
	override val name = "Sanctions"
	
	class BanArguments : Arguments() {
		val member by member {
			name = "membre"
			description = "Le membre à expulser."
		}
		
		val reason by coalescingString {
			name = "raison"
			description = "La raison de du ban."
			autoComplete {
				suggestStringMap(sanctions, FilterStrategy.Contains)
			}
		}
		
		val duration by optionalInt {
			name = "durée"
			description = "La durée du ban."
		}
		
		val deleteDays by optionalInt {
			name = "suppression"
			description = "Le nombre de jours auquel supprimer les messages."
			
			validate {
				if (value != null) {
					if (value!! < 0) throw DiscordRelayedException("La durée de suppression doit être supérieure à 0.")
					else if (value!! > 7) throw DiscordRelayedException("La durée de suppression ne peut pas être supérieure à 7 jours.")
				}
			}
		}
		
		val unit by optionalEnumChoice<DurationUnits> {
			name = "unité"
			description = "L'unité de la durée du ban."
			typeName = "unité"
		}
	}
	
	class DeleteSanctionArguments : Arguments() {
		val id by int {
			name = "cas"
			description = "Le numéro de la sanction à supprimer."
		}
	}
	
	class DeleteAllSanctionsArguments : Arguments() {
		val user by user {
			name = "utilisateur"
			description = "L'utilisateur à qui supprimer toutes les sanctions."
		}
		
		val type by optionalEnumChoice<SanctionType> {
			name = "type"
			description = "Le type de sanctions à supprimer."
			typeName = "type"
		}
	}
	
	class ListSanctionsArguments : Arguments() {
		val user by user {
			name = "utilisateur"
			description = "L'utilisateur à qui afficher les sanctions."
		}
		
		val type by optionalEnumChoice<SanctionType> {
			name = "type"
			description = "Le type de sanctions à afficher."
			typeName = "TEST"
		}
	}
	
	class KickArguments : Arguments() {
		val member by member {
			name = "membre"
			description = "Le membre à expulser."
		}
		
		val reason by coalescingString {
			name = "raison"
			description = "La raison de l'expulsion."
			autoComplete {
				suggestStringMap(
					sanctions, FilterStrategy.Contains
				)
			}
		}
	}
	
	class MuteArguments : Arguments() {
		val member by member {
			name = "membre"
			description = "Le membre à mute."
		}
		
		val duration by int {
			name = "durée"
			description = "La durée du mute."
		}
		
		val unit by enumChoice<DurationUnits> {
			name = "unité"
			description = "L'unité de la durée du mute."
			typeName = "unité"
		}
		
		val reason by coalescingString {
			name = "raison"
			description = "La raison du mute."
			autoComplete {
				suggestStringMap(
					sanctions, FilterStrategy.Contains
				)
			}
		}
	}
	
	class UnBanArguments : Arguments() {
		val user by user {
			name = "utilisateur"
			description = "L'utilisateur à dé-bannir."
		}
		
		val reason by coalescingDefaultingString {
			name = "raison"
			description = "La raison de la dé-bannissement."
			defaultValue = "Pas de raison définie."
		}
	}
	
	class UnMuteArguments : Arguments() {
		val member by member {
			name = "membre"
			description = "Le membre à unmute."
		}
	}
	
	class WarnArguments : Arguments() {
		val member by member {
			name = "membre"
			description = "L'utilisateur à avertir."
		}
		
		val reason by coalescingString {
			name = "raison"
			description = "Raison de l'avertissement."
			autoCompleteReason()
		}
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "sanctions"
			description = "Permet de gérer les sanctions du serveur."
			
			publicSubCommand {
				name = "compte"
				description = "Permet d'avoir le nombre de sanctions mises par les modérateurs."
				
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
					val sanctions = getSanctions(user.id).let {
						if (arguments.type != null) it.filter { it.type == arguments.type }
						else it
					}
					if (sanctions.isEmpty()) throw DiscordRelayedException("Aucune sanction n'a été appliquée à cet utilisateur.")
					
					respondingPaginator {
						sanctions.chunked(10).forEach {
							page {
								completeEmbed(
									client = bot.getKoin().get(),
									title = "Liste des sanctions ${arguments.type?.let { "du type **${it.translation}**" } ?: ""} appliquées à ${user.tag} (${user.id}).",
									description = it.joinToString("\n\n") {
										val appliedBy = it.appliedBy?.let { appliedById ->
											val getUserTag = runBlocking {
												this@publicSlashCommand.kord.getUser(
													appliedById,
													EntitySupplyStrategy.cacheWithCachingRestFallback
												)?.tag ?: "`$appliedById`"
											}
											
											"$getUserTag (`$appliedById`)"
										} ?: "Automatique ou non trouvé"
										
										val duration = if (it.durationMS > 0) "**Durée** : ${it.formattedDuration}" else ""
										
										"""
											> **Cas numéro ${it.id}** ${it.type.emote}
											**Appliquée par** : $appliedBy
											**Date** : ${it.sanctionedAt.toMessageFormat(DiscordTimestampStyle.LongDateTime)}
											$duration
											**Raison** : ${it.reason}
											**Type** : ${it.type.translation}
										""".trimIndent().replace("\n\n", "\n")
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
						var sanctions = getSanctions(arguments.user.id)
						if (arguments.type != null) sanctions = sanctions.filter { it.type == arguments.type }
						
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
		
		publicSlashCommand(::BanArguments) {
			name = "ban"
			description = "Permet de bannir un membre, temporairement ou définitivement."
			
			action {
				val duration = arguments.unit?.durationUnit?.let { arguments.duration?.toDuration(it) }
				guild!!.getBanOrNull(arguments.member.id)?.let {
					val sanctions = getSanctions(arguments.member.id)
					sanctions.find { it.type == SanctionType.BAN && it.isActive }?.let {
						throw DiscordRelayedException("La personne est déjà bannie jusqu'à ${it.toDiscordTimestamp(TimestampType.RelativeTime)}")
					}
					throw DiscordRelayedException("La personne est déjà bannie.")
				}
				
				if (guild?.fetchGuildOrNull()?.selfMember()?.fetchMemberOrNull()?.canInteract(arguments.member) != true) {
					throw DiscordRelayedException("Je ne peux pas bannir avec ce membre, il doit avoir un rôle inférieur au mien.")
				}
				
				Sanction(SanctionType.BAN, arguments.reason, arguments.member.id, durationMS = duration?.inWholeMilliseconds ?: 0, appliedBy = user.id).apply {
					sendLog()
					save()
					
					respond {
						applyToMember(arguments.member, arguments.deleteDays)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
		
		publicSlashCommand(::KickArguments) {
			name = "kick"
			description = "Éjecte un utilisateur du serveur."
			
			action {
				if (guild?.fetchGuildOrNull()?.selfMember()?.fetchMemberOrNull()?.canInteract(arguments.member) != true) {
					throw DiscordRelayedException("Je ne peux pas éjecter avec ce membre, il doit avoir un rôle inférieur au mien.")
				}
				
				Sanction(SanctionType.KICK, arguments.reason, arguments.member.id, appliedBy = user.id).apply {
					sendLog()
					save()
					
					respond {
						applyToMember(arguments.member)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
		
		publicSlashCommand(::MuteArguments) {
			name = "mute"
			description = "Mute une personne en utilisant les timeout (éjections) discord."
			
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
				
				if (!guild!!.selfMember().canInteract(arguments.member)) {
					throw DiscordRelayedException("Je ne peux pas mute cet utilisateur, il doit avoir un rôle inférieur au mien.")
				}
				
				Sanction(SanctionType.MUTE, arguments.reason, arguments.member.id, durationMS = duration.inWholeMilliseconds, appliedBy = user.id).apply {
					sendLog()
					save()
					
					respond {
						applyToMember(arguments.member)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
		
		publicSlashCommand(::UnBanArguments) {
			name = "unban"
			description = "Permet de dé-bannir quelqu'un."
			
			action {
				guild!!.getBanOrNull(arguments.user.id)?.let {
					guild!!.unban(arguments.user.id, arguments.reason)
					respond {
						embed {
							unBanEmbed(this@publicSlashCommand.kord, arguments.user, user)
						}
					}
				} ?: throw DiscordRelayedException("Le membre **${arguments.user.tag}** n'a pas été trouvé dans la liste des bans.")
			}
		}
		
		publicSlashCommand(::UnMuteArguments) {
			name = "unmute"
			description = "Permet de retirer le mute d'une personne (garde quand même la sanction)."
			
			action {
				if (!guild!!.selfMember().canInteract(arguments.member)) {
					throw DiscordRelayedException("Je ne peux pas retirer le mute cet utilisateur, il doit avoir un rôle inférieur au mien.")
				}
				arguments.member.timeoutUntil?.let {
					arguments.member.edit {
						timeoutUntil = null
					}.also {
						respond {
							embed {
								unMuteEmbed(this@publicSlashCommand.kord, arguments.member, user)
							}
						}
					}
				} ?: throw DiscordRelayedException("Cette personne n'est pas mute, je ne peux pas l'un-mute voyons...")
			}
		}
		
		publicSlashCommand(::WarnArguments) {
			name = "warn"
			description = "Avertit un membre, enregistre cette sanction."
			
			action {
				Sanction(SanctionType.WARN, arguments.reason, arguments.member.id, appliedBy = user.id).apply {
					sendLog()
					save()
					respond {
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
	}
}
