package extensions.sanctions

import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.edit
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.withContext
import dev.kordex.core.time.TimestampType
import dev.kordex.core.utils.*
import fr.ayfri.rocketmanager.i18n.Translations
import storage.*
import utils.applySanction
import utils.completeEmbed
import utils.ensureCanInteract
import utils.getNextMuteDuration
import utils.getNextSanctionType
import utils.toDetailedString
import utils.unBanEmbed
import utils.unMuteEmbed
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toDuration

/** Sanctions are listed ten per page, the embed description cannot hold much more. */
private const val SANCTIONS_PER_PAGE = 10

/** The paginated listing shared by the listing and search subcommands. */
private suspend fun PublicSlashCommandContext<*, *>.respondWithSanctions(title: String, sanctions: List<Sanction>) {
	val kord = interactionResponse.kord

	// Resolved up-front so the page bodies below stay non-suspending.
	val moderatorNames = sanctions.mapNotNull { it.appliedBy }.distinct().associateWith { appliedById ->
		kord.getUser(appliedById, EntitySupplyStrategy.cacheWithCachingRestFallback)?.username
	}

	respondingPaginator {
		sanctions.chunked(SANCTIONS_PER_PAGE).forEach { chunk ->
			page {
				completeEmbed(
					client = kord,
					title = title,
					description = chunk.joinToString("\n\n") { it.toDetailedString(moderatorNames[it.appliedBy]) }
				)
			}
		}
	}.send()
}

class Sanctions : Extension() {
	override val name = "Sanctions"

	@OptIn(AlwaysPublicResponse::class)
	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.Sanctions.name
			description = Translations.Commands.Sanctions.description

			publicSubCommand {
				name = Translations.Commands.Sanctions.Count.name
				description = Translations.Commands.Sanctions.Count.description

				action {
					respond {
						completeEmbed(
							client = this@publicSlashCommand.kord,
							title = Translations.Embeds.Sanctions.Count.title.translate(),
							description = getSanctionCounts().map { (moderator, count) ->
								Translations.Embeds.Sanctions.Count.description.translateNamed(
									"username" to (guild?.getMemberOrNull(moderator)?.username ?: moderator.toString()),
									"count" to count.toString()
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
					val sanctions = getSanctions(user.id, arguments.type)
					if (sanctions.isEmpty()) throw DiscordRelayedException(Translations.Embeds.Sanctions.List.noSanctions)

					respondWithSanctions(
						Translations.Embeds.Sanctions.List.title
							.withContext(this@action)
							.translateNamed(
								"type" to (arguments.type?.let { "du type **${it.translation.translate()}** " } ?: ""),
								"user" to user.username,
								"userId" to user.id.toString()
							),
						sanctions
					)
				}
			}

			publicSubCommand(::SanctionInfoArguments) {
				name = Translations.Commands.Sanctions.Info.name
				description = Translations.Commands.Sanctions.Info.description

				action {
					val target = arguments.user
					val sanctions = getSanctions(target.id)
					val nextType = target.getNextSanctionType()
					val nextDuration = if (nextType == SanctionType.MUTE) target.getNextMuteDuration() else 0

					respond {
						completeEmbed(
							client = this@publicSubCommand.kord,
							title = Translations.Embeds.Sanctions.Info.title.translateNamed("username" to target.username),
							description = Translations.Embeds.Sanctions.Info.description.translateNamed(
								"user" to target.mention,
								"id" to target.id.toString(),
								"count" to sanctions.size.toString(),
								"ads" to getAdEventCount(target.id).toString()
							)
						) {
							field {
								name = Translations.Fields.types.translate()
								value = sanctions.groupingBy { it.type }.eachCount()
									.map { (type, count) -> "${type.emote} ${type.translation.translate()} : **$count**" }
									.joinToString("\n")
									.ifEmpty { Translations.Embeds.Sanctions.Info.noSanctions.translate() }
							}

							sanctions.find { it.isActive }?.let { active ->
								field {
									name = Translations.Fields.activeSanction.translate()
									value = Translations.Embeds.Sanctions.Info.activeUntil.translateNamed(
										"emote" to active.type.emote,
										"type" to active.type.translation.translate(),
										"until" to active.activeUntil.toMessageFormat(DiscordTimestampStyle.RelativeTime)
									)
								}
							}

							sanctions.lastOrNull()?.let { last ->
								field {
									name = Translations.Fields.lastSanction.translate()
									value = last.toDetailedString()
								}
							}

							field {
								name = Translations.Fields.nextSanction.translate()
								value = Translations.Embeds.Sanctions.Info.nextSanction.translateNamed(
									"emote" to nextType.emote,
									"type" to nextType.translation.translate(),
									"duration" to formatDurationMS(nextDuration)
								)
							}
						}
					}
				}
			}

			publicSubCommand(::SearchSanctionsArguments) {
				name = Translations.Commands.Sanctions.Search.name
				description = Translations.Commands.Sanctions.Search.description

				action {
					val since = arguments.period?.days?.let { Instant.now().minus(it, ChronoUnit.DAYS) }
					val sanctions = searchSanctions(
						member = arguments.user?.id,
						appliedBy = arguments.moderator?.id,
						type = arguments.type,
						since = since,
						reason = arguments.reason
					)

					if (sanctions.isEmpty()) throw DiscordRelayedException(Translations.Embeds.Sanctions.Search.noResults)

					respondWithSanctions(
						Translations.Embeds.Sanctions.Search.title.translateNamed("count" to sanctions.size.toString()),
						sanctions
					)
				}
			}

			publicSubCommand(::DeleteSanctionArguments) {
				name = Translations.Commands.Sanctions.Delete.name
				description = Translations.Commands.Sanctions.Delete.description

				action {
					val sanctionId = arguments.id
					val sanction = getSanction(sanctionId) ?: throw DiscordRelayedException(
						Translations.Embeds.Sanctions.Delete.notFound.withNamedPlaceholders("id" to sanctionId.toString())
					)

					val moderatorName = sanction.appliedBy?.let { this@publicSubCommand.kord.getUser(it)?.username }

					removeSanction(sanctionId)

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
								value = sanction.toDetailedString(moderatorName)
							}
						}
					}
				}
			}

			publicSubCommand(::DeleteAllSanctionsArguments) {
				name = Translations.Commands.Sanctions.DeleteAll.name
				description = Translations.Commands.Sanctions.DeleteAll.description

				action {
					val sanctions = getSanctions(arguments.user.id, arguments.type)

					respond {
						if (sanctions.isEmpty()) {
							content = when (arguments.type) {
								null -> Translations.Embeds.Sanctions.DeleteAll.noSanctions.translate()
								else -> Translations.Embeds.Sanctions.DeleteAll.noSanctionsType.translate()
							}
							return@respond
						}

						removeSanctions(arguments.user.id, arguments.type)
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

		publicSlashCommand(::BanArguments) {
			name = Translations.Commands.Sanctions.Ban.name
			description = Translations.Commands.Sanctions.Ban.description

			action {
				val duration = arguments.unit?.durationUnit?.let { arguments.duration?.toDuration(it) }

				guild?.getBanOrNull(arguments.member.id)?.let {
					getSanctions(arguments.member.id, SanctionType.BAN).find { it.isActive }?.let {
						throw DiscordRelayedException(
							Translations.Errors.alreadyBannedUntil.withNamedPlaceholders(
								"until" to it.toDiscordTimestamp(TimestampType.RelativeTime)
							)
						)
					}

					throw DiscordRelayedException(Translations.Errors.alreadyBanned)
				}

				guild.ensureCanInteract(arguments.member, Translations.Errors.cannotBanMember)

				applySanction(
					Sanction(
						SanctionType.BAN,
						arguments.reason,
						arguments.member.id,
						durationMS = duration?.inWholeMilliseconds ?: 0,
						appliedBy = user.id
					),
					arguments.member,
					arguments.deleteDays
				)
			}
		}

		publicSlashCommand(::KickArguments) {
			name = Translations.Commands.Sanctions.Kick.name
			description = Translations.Commands.Sanctions.Kick.description

			action {
				guild.ensureCanInteract(arguments.member, Translations.Errors.cannotKickMember)

				applySanction(
					Sanction(SanctionType.KICK, arguments.reason, arguments.member.id, appliedBy = user.id),
					arguments.member
				)
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

				guild.ensureCanInteract(arguments.member, Translations.Errors.cannotMuteMember)

				applySanction(
					Sanction(
						SanctionType.MUTE,
						arguments.reason,
						arguments.member.id,
						durationMS = duration.inWholeMilliseconds,
						appliedBy = user.id
					),
					arguments.member
				)
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
				guild.ensureCanInteract(arguments.member, Translations.Errors.cannotUnmuteMember)

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
				applySanction(Sanction(SanctionType.WARN, arguments.reason, arguments.member.id, appliedBy = user.id))
			}
		}
	}
}
