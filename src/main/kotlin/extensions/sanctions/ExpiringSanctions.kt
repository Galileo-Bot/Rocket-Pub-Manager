package extensions.sanctions

import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.extensions.Extension
import dev.kordex.core.utils.scheduling.Scheduler
import fr.ayfri.rocketmanager.i18n.Translations
import io.ktor.http.HttpStatusCode
import logger
import storage.SanctionType
import storage.getPendingTemporarySanctions
import storage.markSanctionLifted
import utils.getLogSanctionsChannel
import utils.getRocketPubGuild
import utils.unMuteEmbed
import kotlin.time.Duration.Companion.minutes

/** Sanctions are counted in minutes at least, checking every minute is precise enough and costs one query. */
private val SWEEP_INTERVAL = 1.minutes

/**
 * Ends the temporary bans and mutes whose duration has run out: the bans are lifted, the mutes, which Discord lifts on
 * its own, only get their end logged.
 *
 * Nothing is scheduled sanction per sanction: the sweep reads the pending ones back from the database every time, so
 * the ones that expired while the bot was down are handled on the first pass after a restart.
 */
class ExpiringSanctions : Extension() {
	override val name = "Expiring-Sanctions"
	private val scheduler = Scheduler()

	override suspend fun setup() {
		scheduler.schedule(SWEEP_INTERVAL, name = "Expired sanctions sweep", pollingSeconds = 10, repeat = true) {
			runCatching { liftExpiredSanctions() }
				.onFailure { logger.error(it) { "Failed to lift the expired temporary sanctions." } }
		}
	}

	private suspend fun liftExpiredSanctions() {
		val expired = getPendingTemporarySanctions().filterNot { it.isActive }
		if (expired.isEmpty()) return

		val guild = kord.getRocketPubGuild()
		val reason = Translations.Messages.temporaryBanExpired.translate()

		expired.forEach { sanction ->
			when (sanction.type) {
				// The ban may already be gone, lifted by hand while the bot was down: an unban then answers 404.
				// A failed unban is not marked lifted so the next sweep retries it.
				SanctionType.BAN -> runCatching { guild.unban(sanction.member, reason) }
					.onFailure { if ((it as? RestRequestException)?.status?.code != HttpStatusCode.NotFound.value) throw it }
					.onSuccess { logger.info { "Lifted the expired ban ${sanction.id} of ${sanction.member}" } }

				else -> kord.getLogSanctionsChannel().createEmbed { unMuteEmbed(kord, UserBehavior(sanction.member, kord)) }
			}

			markSanctionLifted(sanction.id)
		}
	}
}
