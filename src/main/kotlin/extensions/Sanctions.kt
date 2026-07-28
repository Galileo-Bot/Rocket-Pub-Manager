package extensions

import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.builders.ConverterBuilder
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.withContext
import dev.kordex.core.time.TimestampType
import dev.kordex.core.utils.*
import fr.ayfri.rocketmanager.i18n.Translations
import storage.*
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
		"fake pub" to Translations.Sanctions.fakePub,
		"invite reward" to Translations.Sanctions.inviteReward,
		"mauvaise catégorie" to Translations.Sanctions.wrongCategory,
		"mention everyone/here" to Translations.Sanctions.mentionEveryone,
		"pub interdite" to Translations.Sanctions.forbiddenAd,
		"pub mp" to Translations.Sanctions.dmAd,
		"pubs à la suite" to Translations.Sanctions.consecutiveAds,
		"sans description" to Translations.Sanctions.noDescription,
		"spam après warn" to Translations.Sanctions.spamAfterWarn,
		"spam" to Translations.Sanctions.spam,
		//		"lien" to "Lien de publicité interdit dans ${channel.mention}.",
	).mapValues { (_, value) -> value.translate() }

fun ConverterBuilder<String>.autoCompleteReason() {
	autoComplete {
		suggestString {
			sanctions.forEach { (key, value) -> choice(key, value) }
		}
	}
}

enum class DurationUnits(val durationUnit: DurationUnit) : ChoiceEnum {
	DAYS(DurationUnit.DAYS),
	HOURS(DurationUnit.HOURS),
	MINUTES(DurationUnit.MINUTES);

	override val readableName
		get() = when (this) {
			DAYS -> Translations.Units.days
			HOURS -> Translations.Units.hours
			MINUTES -> Translations.Units.minutes
		}
}

class Sanctions : Extension() {
	override val name = "Sanctions"

	class BanArguments : Arguments() {
		val member by member {
			name = Translations.Arguments.Member.name
			description = Translations.Arguments.Member.description
		}

		val reason by coalescingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
			autoComplete {
				suggestStringMap(sanctions, FilterStrategy.Contains)
			}
		}

		val duration by optionalInt {
			name = Translations.Arguments.Duration.name
			description = Translations.Arguments.Duration.description
		}

		val deleteDays by optionalInt {
			name = Translations.Arguments.DeleteDays.name
			description = Translations.Arguments.DeleteDays.description

			maxValue = 7
			minValue = 1
		}

		val unit by optionalEnumChoice<DurationUnits> {
			name = Translations.Arguments.Unit.name
			description = Translations.Arguments.Unit.description
			typeName = Translations.Arguments.Unit.name
		}
	}

	class DeleteSanctionArguments : Arguments() {
		val id by int {
			name = Translations.Arguments.Id.name
			description = Translations.Arguments.Id.description
		}
	}

	class DeleteAllSanctionsArguments : Arguments() {
		val user by user {
			name = Translations.Arguments.User.name
			description = Translations.Arguments.User.description
		}

		val type by optionalEnumChoice<SanctionType> {
			name = Translations.Arguments.Type.name
			description = Translations.Arguments.Type.description
			typeName = Translations.Arguments.Type.name
		}
	}

	class ListSanctionsArguments : Arguments() {
		val user by user {
			name = Translations.Arguments.User.name
			description = Translations.Arguments.User.description
		}

		val type by optionalEnumChoice<SanctionType> {
			name = Translations.Arguments.Type.name
			description = Translations.Arguments.Type.description
			typeName = Translations.Arguments.Type.name
		}
	}

	class KickArguments : Arguments() {
		val member by member {
			name = Translations.Arguments.Member.name
			description = Translations.Arguments.Member.description
		}

		val reason by coalescingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
			autoComplete {
				suggestStringMap(
					sanctions, FilterStrategy.Contains
				)
			}
		}
	}

	class MuteArguments : Arguments() {
		val member by member {
			name = Translations.Arguments.Member.name
			description = Translations.Arguments.Member.description
		}

		val duration by int {
			name = Translations.Arguments.Duration.name
			description = Translations.Arguments.Duration.description
		}

		val unit by enumChoice<DurationUnits> {
			name = Translations.Arguments.Unit.name
			description = Translations.Arguments.Unit.description
			typeName = Translations.Arguments.Unit.name
		}

		val reason by coalescingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
			autoComplete {
				suggestStringMap(sanctions, FilterStrategy.Contains)
			}
		}
	}

	class UnBanArguments : Arguments() {
		val user by user {
			name = Translations.Arguments.User.name
			description = Translations.Arguments.User.description
		}

		val reason by coalescingDefaultingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
			defaultValue = Translations.Messages.noReason.translate()
		}
	}

	class UnMuteArguments : Arguments() {
		val member by member {
			name = Translations.Arguments.Member.name
			description = Translations.Arguments.Member.description
		}
	}

	class WarnArguments : Arguments() {
		val member by member {
			name = Translations.Arguments.Member.name
			description = Translations.Arguments.Member.description
		}

		val reason by coalescingString {
			name = Translations.Arguments.Reason.name
			description = Translations.Arguments.Reason.description
			autoCompleteReason()
		}
	}

	@OptIn(AlwaysPublicResponse::class)
	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.Sanctions.name
			description = Translations.Commands.Sanctions.description

			publicSubCommand {
				name = Translations.Commands.Sanctions.Count.name
				description = Translations.Commands.Sanctions.Count.description

				action {
					val sanctions = getSanctionCount()

					respond {
						completeEmbed(
							client = bot.getKoin().get(),
							title = Translations.Embeds.Sanctions.Count.title.translate(),
							description = sanctions.groupBy { it }.map {
								Translations.Embeds.Sanctions.Count.description.translateNamed(
									"username" to guild!!.getMember(it.key).username,
									"count" to it.value.size.toString()
								)
							}.joinToString("\n\n")
						)
					}
				}
			}

			publicSubCommand(::ListSanctionsArguments) {
				name = Translations.Commands.Sanctions.List.name
				description = Translations.Commands.Sanctions.List.description

				action {
					val user = arguments.user
					val sanctions = getSanctions(user.id).let { sanctions ->
						arguments.type?.let { sanctions.filter { it.type == arguments.type } } ?: sanctions
					}
					if (sanctions.isEmpty()) throw DiscordRelayedException(Translations.Embeds.Sanctions.List.noSanctions)

					// Resolved up-front so the page bodies below stay non-suspending.
					val moderatorNames = sanctions.mapNotNull { it.appliedBy }.distinct().associateWith { appliedById ->
						this@publicSlashCommand.kord.getUser(
							appliedById,
							EntitySupplyStrategy.cacheWithCachingRestFallback
						)?.username
					}

					respondingPaginator {
						sanctions.chunked(10).forEach {
							page {
								completeEmbed(
									client = bot.getKoin().get(),
									title = Translations.Embeds.Sanctions.List.title
										.withContext(this@action)
										.translateNamed(
											"type" to (arguments.type?.let { "du type **${it.translation.translate()}** " } ?: ""),
											"user" to user.username,
											"userId" to user.id.toString()
										),
									description = it.joinToString("\n\n") {
										val appliedBy = it.appliedBy?.let { appliedById ->
											"${moderatorNames[appliedById] ?: "`$appliedById`"} (`$appliedById`)"
										} ?: Translations.Messages.automaticOrNotFound.translate()

										val duration =
											if (it.durationMS > 0) "**${Translations.Fields.duration.translate()}** : ${it.formattedDuration}" else ""

										"""
											> **${Translations.Fields.caseNumber.translate()} ${it.id}** ${it.type.emote}
											**${Translations.Fields.appliedBy.translate()}** : $appliedBy
											**${Translations.Fields.date.translate()}** : ${
											it.sanctionedAt.toMessageFormat(
												DiscordTimestampStyle.LongDateTime
											)
										}
											$duration
											**${Translations.Fields.reason.translate()}** : ${it.reason}
											**${Translations.Fields.type.translate()}** : ${it.type.translation.translate()}
										""".trimIndent().replace("\n\n", "\n")
									}
								)
							}
						}
					}.send()
				}
			}

			publicSubCommand(::DeleteSanctionArguments) {
				name = Translations.Commands.Sanctions.Delete.name
				description = Translations.Commands.Sanctions.Delete.description

				action {
					val sanctionId = arguments.id
					val sanction =
						getSanction(sanctionId)
							?: throw DiscordRelayedException(
								Translations.Embeds.Sanctions.Delete.notFound.withNamedPlaceholders(
									"id" to sanctionId.toString()
								)
							)

					val appliedBy = sanction.appliedBy?.let {
						val user = this@publicSubCommand.kord.getUser(it) ?: return@let null
						"${user.username} (`${user.id}`)"
					}

					respond {
						completeEmbed(
							this@publicSubCommand.kord,
							Translations.Embeds.Sanctions.Delete.title.translate(),
							Translations.Embeds.Sanctions.Delete.success.translateNamed(
								"id" to sanctionId.toString(),
								"user" to user.mention
							)
						) {
							field {
								name = Translations.Embeds.Sanctions.Delete.fieldName.translateNamed(
									"id" to sanctionId.toString(),
									"emote" to sanction.type.emote
								)
								value =
									"""
									> **${Translations.Fields.caseNumber.translate()} ${sanction.id}** ${sanction.type.emote}
									**${Translations.Fields.appliedBy.translate()}** : $appliedBy
									**${Translations.Fields.date.translate()}** : ${
										sanction.sanctionedAt.toMessageFormat(
											DiscordTimestampStyle.LongDateTime
										)
									}
									""".trimIndent()
							}
						}
					}
				}
			}

			publicSubCommand(::DeleteAllSanctionsArguments) {
				name = Translations.Commands.Sanctions.DeleteAll.name
				description = Translations.Commands.Sanctions.DeleteAll.description

				action {
					val sanctions = getSanctions(arguments.user.id).let { all ->
						arguments.type?.let { type -> all.filter { it.type == type } } ?: all
					}

					respond {
						if (arguments.type != null && sanctions.none { it.type == arguments.type }) {
							content = Translations.Embeds.Sanctions.DeleteAll.noSanctionsType.translate()
						} else if (sanctions.isEmpty()) {
							content = Translations.Embeds.Sanctions.DeleteAll.noSanctions.translate()
						} else {
							removeSanctions(arguments.user.id, arguments.type?.toString())
							completeEmbed(
								this@publicSubCommand.kord,
								Translations.Embeds.Sanctions.DeleteAll.title.translateNamed("count" to sanctions.size.toString()),
								Translations.Embeds.Sanctions.DeleteAll.success.translateNamed("user" to arguments.user.mention)
							) {
								field {
									name = Translations.Fields.types.translate()
									value = sanctions.groupBy { it.type }
										.map { "${it.key.translation.translate() + "s"} : **${it.value.size}**" }
										.joinToString("\n")
								}
							}
						}
					}
				}
			}
		}

		publicSlashCommand(::BanArguments) {
			name = Translations.Commands.Sanctions.Ban.name
			description = Translations.Commands.Sanctions.Ban.description

			action {
				val duration = arguments.unit?.durationUnit?.let { arguments.duration?.toDuration(it) }

				guild?.getBanOrNull(arguments.member.id)?.let {
					val sanctions = getSanctions(arguments.member.id)
					sanctions.find { it.type == SanctionType.BAN && it.isActive }?.let {
						throw DiscordRelayedException(
							Translations.Errors.alreadyBannedUntil.withNamedPlaceholders(
								"until" to it.toDiscordTimestamp(TimestampType.RelativeTime)
							)
						)
					}

					throw DiscordRelayedException(Translations.Errors.alreadyBanned)
				}

				if (guild?.fetchGuildOrNull()?.selfMember()?.fetchMemberOrNull()
						?.canInteract(arguments.member) != true
				) {
					throw DiscordRelayedException(Translations.Errors.cannotBanMember)
				}

				Sanction(
					SanctionType.BAN,
					arguments.reason,
					arguments.member.id,
					durationMS = duration?.inWholeMilliseconds ?: 0,
					appliedBy = user.id
				).apply {
					respond {
						applyToMember(arguments.member, arguments.deleteDays)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}

					save()
					sendLog()
				}
			}
		}

		publicSlashCommand(::KickArguments) {
			name = Translations.Commands.Sanctions.Kick.name
			description = Translations.Commands.Sanctions.Kick.description

			action {
				if (guild?.fetchGuildOrNull()?.selfMember()?.fetchMemberOrNull()
						?.canInteract(arguments.member) != true
				) {
					throw DiscordRelayedException(Translations.Errors.cannotKickMember)
				}

				Sanction(SanctionType.KICK, arguments.reason, arguments.member.id, appliedBy = user.id).apply {
					respond {
						applyToMember(arguments.member)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}

					save()
					sendLog()
				}
			}
		}

		publicSlashCommand(::MuteArguments) {
			name = Translations.Commands.Sanctions.Mute.name
			description = Translations.Commands.Sanctions.Mute.description

			action {
				val duration = arguments.duration.toDuration(arguments.unit.durationUnit)
				if (arguments.member.timeoutUntil != null) throw DiscordRelayedException(
					Translations.Errors.alreadyMuted.withNamedPlaceholders(
						"until" to arguments.member.timeoutUntil!!.toMessageFormat(DiscordTimestampStyle.RelativeTime)
					)
				)
				if (duration < 2.minutes) throw DiscordRelayedException(Translations.Errors.muteDurationTooShort)
				if (duration > 28.days) throw DiscordRelayedException(Translations.Errors.muteDurationTooLong)

				if (!guild!!.selfMember().canInteract(arguments.member)) {
					throw DiscordRelayedException(Translations.Errors.cannotMuteMember)
				}

				Sanction(
					SanctionType.MUTE,
					arguments.reason,
					arguments.member.id,
					durationMS = duration.inWholeMilliseconds,
					appliedBy = user.id
				).apply {
					respond {
						applyToMember(arguments.member)
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}

					save()
					sendLog()
				}
			}
		}

		publicSlashCommand(::UnBanArguments) {
			name = Translations.Commands.Sanctions.Unban.name
			description = Translations.Commands.Sanctions.Unban.description

			action {
				guild?.getBanOrNull(arguments.user.id)
					?: throw DiscordRelayedException(
						Translations.Errors.userNotFound.withNamedPlaceholders("user" to arguments.user.username)
					)

				guild?.unban(arguments.user.id, arguments.reason)

				respond {
					embed {
						unBanEmbed(this@publicSlashCommand.kord, arguments.user, user)
					}
				}
			}
		}

		publicSlashCommand(::UnMuteArguments) {
			name = Translations.Commands.Sanctions.Unmute.name
			description = Translations.Commands.Sanctions.Unmute.description

			action {
				if (!guild!!.selfMember().canInteract(arguments.member)) {
					throw DiscordRelayedException(Translations.Errors.cannotUnmuteMember)
				}

				arguments.member.timeoutUntil ?: throw DiscordRelayedException(Translations.Errors.userNotMuted)

				arguments.member.edit {
					timeoutUntil = null
				}

				respond {
					embed {
						unMuteEmbed(this@publicSlashCommand.kord, arguments.member, user)
					}
				}
			}
		}

		publicSlashCommand(::WarnArguments) {
			name = Translations.Commands.Sanctions.Warn.name
			description = Translations.Commands.Sanctions.Warn.description

			action {
				Sanction(SanctionType.WARN, arguments.reason, arguments.member.id, appliedBy = user.id).apply {
					save()
					sendLog()

					respond {
						sanctionEmbed(this@publicSlashCommand.kord, this@apply)
					}
				}
			}
		}
	}
}
