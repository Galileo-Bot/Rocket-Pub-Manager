package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.AuditLogChangeKey
import dev.kord.common.entity.AuditLogEvent
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.getAuditLogEntries
import dev.kord.core.entity.User
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.BanRemoveEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import storage.Sanction
import storage.SanctionType
import storage.getSanctions
import utils.ROCKET_PUB_GUILD
import utils.getLogSanctionsChannel
import utils.unBanEmbed
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AutoSanctions : Extension() {
	override val name = "Detect-Sanctions"
	private val scheduler = Scheduler()
	
	@OptIn(ExperimentalTime::class)
	override suspend fun setup() {
		event<BanAddEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				var sanctionedBy: User? = null
				
				event.guild.getAuditLogEntries {
					action = AuditLogEvent.MemberBanAdd
				}.firstOrNull {
					it.id.timeMark.elapsedNow() < 10.seconds
				}?.let { entry ->
					sanctionedBy = event.guild.getMemberOrNull(entry.userId)
				}
				
				val user = event.user
				
				if (getSanctions(user.id).none { it.type == SanctionType.BAN && it.isActive }) {
					val ban = event.getBan()
					Sanction(SanctionType.BAN, ban.reason, user.id, sanctionedBy?.id).apply {
						if (getSanctions(user.id).any { it.equalExceptOwner(this) }) return@action
						
						save()
						sendLog()
					}
				}
			}
		}
		
		event<BanRemoveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				var sanctionedBy: User? = null
				var reason: String? = null
				
				event.guild.getAuditLogEntries {
					action = AuditLogEvent.MemberBanRemove
				}.firstOrNull {
					it.id.timeMark.elapsedNow() < 10.seconds
				}?.let { entry ->
					sanctionedBy = event.guild.getMemberOrNull(entry.userId)
					reason = entry.reason
				}
				
				val user = event.user
				kord.getLogSanctionsChannel().createEmbed {
					unBanEmbed(event.kord, user, sanctionedBy, reason)
				}
			}
		}
		
		event<MemberLeaveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				event.guild.getAuditLogEntries {
					action = AuditLogEvent.MemberKick
					userId = event.user.id
				}.firstOrNull {
					it.id.timeMark.elapsedNow() < 10.seconds
				}?.let { entry ->
					Sanction(SanctionType.KICK, entry.reason, event.user.id).apply {
						if (getSanctions(event.user.id).any { it.equalExceptOwner(this) }) return@action
						
						save()
						sendLog()
					}
				}
			}
		}
		
		event<MemberUpdateEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				val before = event.old
				val new = event.member
				
				before?.let {
					val log = event.guild.getAuditLogEntries {
						action = AuditLogEvent.MemberUpdate
					}.firstOrNull { entry ->
						entry.id.timeMark.elapsedNow() < 10.seconds && entry.changes.any { it.key == AuditLogChangeKey.CommunicationDisabledUntil }
					} ?: return@let
					
					val duration = (new.timeoutUntil ?: return@action) - Clock.System.now()
					Sanction(SanctionType.MUTE, log.reason, new.id, log.userId, duration.inWholeMilliseconds).apply {
						if (getSanctions(new.id).any { it.equalExceptOwner(this) }) return@action
						
						save()
						sendLog()
					}
					
					scheduler.schedule(duration, name = "Un-mute Scheduler") {
						kord.getLogSanctionsChannel().createEmbed {
							unMuteEmbed(kord, new, kord.getUser(log.userId))
						}
					}
				}
			}
		}
	}
}

fun UserBehavior.getNextMuteDuration() = when (getSanctions(id).size) {
	0 -> 0.seconds
	in 1..2 -> 6.hours
	in 3..4 -> 1.days
	in 5..6 -> 5.days
	in 7..8 -> 14.days
	else -> 27.days
}.inWholeMilliseconds

fun UserBehavior.getNextSanctionType() = when (getSanctions(id).size) {
	0 -> SanctionType.LIGHT_WARN
	
	in 1..4 -> when {
		getSanctions(id).any { it.type == SanctionType.MUTE } -> SanctionType.MUTE
		getSanctions(id).any { it.type == SanctionType.KICK } -> SanctionType.KICK
		getSanctions(id).any { it.type == SanctionType.BAN } -> SanctionType.BAN
		else -> SanctionType.WARN
	}
	
	in 5..10 -> when {
		getSanctions(id).any { it.type == SanctionType.MUTE } -> SanctionType.KICK
		getSanctions(id).any { it.type == SanctionType.KICK } -> SanctionType.BAN
		else -> SanctionType.WARN
	}
	
	else -> SanctionType.BAN
}
