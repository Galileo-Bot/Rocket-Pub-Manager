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
import storage.liftActiveBans
import utils.ROCKET_PUB_GUILD
import utils.getLogSanctionsChannel
import utils.sendLog
import utils.unBanEmbed
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.seconds

/** Audit log entries older than this were not caused by the event being handled. */
private val AUDIT_LOG_WINDOW = 10.seconds

/** Without a limit the flow pages backwards through the whole audit log whenever no entry matches, a normal leave for instance. */
private const val AUDIT_LOG_LIMIT = 10

/**
 * The newest audit log entry of [action] aimed at [targetId] that could have caused the event being handled.
 *
 * The audit log is the only place Discord tells who performed a moderation action done outside of the bot. Its
 * `user_id` query filter is the actor, not the target, so the target is matched on the entries themselves.
 */
private suspend fun GuildBehavior.recentAuditLogEntry(
	action: AuditLogEvent,
	targetId: Snowflake,
	matches: (AuditLogEntry) -> Boolean = { true },
) = getAuditLogEntries {
	this.action = action
	limit = AUDIT_LOG_LIMIT
}.firstOrNull { it.id.timeMark.elapsedNow() < AUDIT_LOG_WINDOW && it.targetId == targetId && matches(it) }

class DetectSanctions : Extension() {
	override val name = "Detect-Sanctions"
	private val scheduler = Scheduler()

	override suspend fun setup() {
		event<BanAddEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val user = event.user
				val sanctions = getSanctions(user.id)
				if (sanctions.any { it.type == SanctionType.BAN && it.isActive }) return@action

				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberBanAdd, user.id)
				// The bot's own bans are recorded by the command that issued them.
				if (entry?.userId == kord.selfId) return@action

				// The audit entry carries the reason, fetching the ban again would only repeat it.
				Sanction(SanctionType.BAN, entry?.reason, user.id, entry?.userId).apply {
					if (sanctions.any { it.equalExceptOwner(this) }) return@action

					save()
					sendLog(kord)
				}
			}
		}

		event<BanRemoveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberBanRemove, event.user.id)
				val unBannedBy = entry?.userId?.let { event.guild.getMemberOrNull(it) }

				liftActiveBans(event.user.id)

				kord.getLogSanctionsChannel().createEmbed {
					unBanEmbed(event.kord, event.user, unBannedBy, entry?.reason)
				}
			}
		}

		event<MemberLeaveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberKick, event.user.id) ?: return@action

				// The bot's own kicks are recorded by the command that issued them.
				if (entry.userId == kord.selfId) return@action

				Sanction(SanctionType.KICK, entry.reason, event.user.id, entry.userId).apply {
					if (getSanctions(event.user.id).any { it.equalExceptOwner(this) }) return@action

					save()
					sendLog(kord)
				}
			}
		}

		event<MemberUpdateEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }

			action {
				val old = event.old ?: return@action
				val member = event.member

				// Nickname, role and avatar changes fire this event too, only a timeout is worth an audit log request.
				if (member.timeoutUntil == old.timeoutUntil) return@action

				val entry = event.guild.recentAuditLogEntry(AuditLogEvent.MemberUpdate, member.id) { candidate ->
					candidate.changes.any { it.key == AuditLogChangeKey.CommunicationDisabledUntil }
				} ?: return@action

				val moderatorId = entry.userId ?: return@action
				// The bot's own mutes are recorded by the command that issued them.
				if (moderatorId == kord.selfId) return@action
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
