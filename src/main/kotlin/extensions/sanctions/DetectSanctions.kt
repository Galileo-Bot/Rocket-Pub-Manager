package extensions.sanctions

import dev.kord.common.entity.AuditLogChangeKey
import dev.kord.common.entity.AuditLogEvent
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.getAuditLogEntries
import dev.kord.core.entity.AuditLogEntry
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.BanRemoveEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kordex.core.checks.inGuild
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.scheduling.Scheduler
import dev.kordex.core.utils.timeoutUntil
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock
import storage.Sanction
import storage.SanctionType
import storage.getSanctions
import utils.ROCKET_PUB_GUILD
import utils.getLogSanctionsChannel
import utils.sendLog
import utils.unBanEmbed
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.seconds

/** Audit log entries older than this were not caused by the event being handled. */
private val AUDIT_LOG_WINDOW = 10.seconds

/**
 * The newest audit log entry of [action] that could have caused the event being handled.
 *
 * The audit log is the only place Discord tells who performed a moderation action done outside of the bot.
 */
private suspend fun GuildBehavior.recentAuditLogEntry(
	action: AuditLogEvent,
	targetId: Snowflake? = null,
	matches: (AuditLogEntry) -> Boolean = { true },
) = getAuditLogEntries {
	this.action = action
	targetId?.let { userId = it }
}.firstOrNull { it.id.timeMark.elapsedNow() < AUDIT_LOG_WINDOW && matches(it) }

class DetectSanctions : Extension() {
	override val name = "Detect-Sanctions"
	private val scheduler = Scheduler()

	override suspend fun setup() {
		event<BanAddEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberBanAdd)
				val sanctionedBy = entry?.userId?.let { event.guild.getMemberOrNull(it) }
				val user = event.user

				if (getSanctions(user.id, SanctionType.BAN).any { it.isActive }) return@action

				Sanction(SanctionType.BAN, event.getBan().reason, user.id, sanctionedBy?.id).apply {
					if (getSanctions(user.id).any { it.equalExceptOwner(this) }) return@action

					save()
					sendLog(kord)
				}
			}
		}

		event<BanRemoveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberBanRemove)
				val unBannedBy = entry?.userId?.let { event.guild.getMemberOrNull(it) }

				kord.getLogSanctionsChannel().createEmbed {
					unBanEmbed(event.kord, event.user, unBannedBy, entry?.reason)
				}
			}
		}

		event<MemberLeaveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberKick, event.user.id) ?: return@action

				Sanction(SanctionType.KICK, entry.reason, event.user.id).apply {
					if (getSanctions(event.user.id).any { it.equalExceptOwner(this) }) return@action

					save()
					sendLog(kord)
				}
			}
		}

		event<MemberUpdateEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				event.old ?: return@action
				val member = event.member

				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberUpdate) { candidate ->
					candidate.changes.any { it.key == AuditLogChangeKey.CommunicationDisabledUntil }
				} ?: return@action

				val moderatorId = entry.userId ?: return@action
				val duration = (member.timeoutUntil ?: return@action) - Clock.System.now()

				Sanction(SanctionType.MUTE, entry.reason, member.id, moderatorId, duration.inWholeMilliseconds).apply {
					if (getSanctions(member.id).any { it.equalExceptOwner(this) }) return@action

					save()
					sendLog(kord)
				}

				scheduler.schedule(duration, name = "Un-mute Scheduler") {
					kord.getLogSanctionsChannel().createEmbed {
						unMuteEmbed(kord, member, kord.getUser(moderatorId))
					}
				}
			}
		}
	}
}
