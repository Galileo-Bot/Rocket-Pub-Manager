package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.AuditLogChange
import dev.kord.common.entity.AuditLogEvent
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.getAuditLogEntries
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.BanRemoveEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kord.core.firstOrNull
import kotlinx.datetime.Clock
import storage.Sanction
import storage.SanctionType
import storage.getSanctions
import utils.ROCKET_PUB_GUILD
import utils.SANCTION_LOGGER_CHANNEL
import utils.getLogChannel
import utils.unBanEmbed
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AutoSanctions : Extension() {
	override val name = "AutoSanctions"
	private val scheduler = Scheduler()
	
	@OptIn(ExperimentalTime::class)
	override suspend fun setup() {
		event<BanAddEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				val user = event.user
				if (getSanctions(user.id).none { it.type == SanctionType.BAN && it.isActive }) {
					val ban = event.getBan()
					Sanction(SanctionType.BAN, ban.reason, user.id).apply {
						sendLog(event.kord)
						save()
					}
				}
			}
		}
		
		event<BanRemoveEvent> {
			check { inGuild(ROCKET_PUB_GUILD) }
			
			action {
				val user = event.user
				kord.getGuild(ROCKET_PUB_GUILD)!!.getChannel(SANCTION_LOGGER_CHANNEL).asChannelOf<TextChannel>().createEmbed {
					unBanEmbed(event.kord, user)
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
				}?.let {
					Sanction(SanctionType.KICK, it.reason, event.user.id).apply {
						sendLog(event.kord)
						save()
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
					}.firstOrNull {
						print(it.changes.map(AuditLogChange<*>::key))
						it.id.timeMark.elapsedNow() < 10.seconds && it.changes.any { it.key.name == "communication_disabled_until" }
					} ?: return@let
					
					val duration = new.timeoutUntil!! - Clock.System.now()
					Sanction(SanctionType.MUTE, log.reason, new.id, log.userId, duration.inWholeMilliseconds).apply {
						sendLog(kord)
						save()
					}
					
					scheduler.schedule(duration, name = "Un-mute Scheduler") {
						kord.getLogChannel().createEmbed {
							unMuteEmbed(kord, new, kord.getUser(log.userId))
						}
					}
				}
			}
		}
	}
}
