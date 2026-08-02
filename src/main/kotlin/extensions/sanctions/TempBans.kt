package extensions.sanctions

import dev.kordex.core.extensions.Extension
import dev.kordex.core.utils.scheduling.Scheduler
import fr.ayfri.rocketmanager.i18n.Translations
import logger
import storage.getPendingTemporaryBans
import storage.markSanctionLifted
import utils.getRocketPubGuild
import kotlin.time.Duration.Companion.minutes

/** Bans are counted in hours at least, checking every minute is precise enough and costs one query. */
private val SWEEP_INTERVAL = 1.minutes

/**
 * Lifts the bans whose duration has run out.
 *
 * The expiry is not scheduled ban per ban: the sweep reads the pending bans back from the database every time,
 * so the ones that expired while the bot was down are lifted on the first pass after a restart.
 */
class TempBans : Extension() {
	override val name = "Temp-Bans"
	private val scheduler = Scheduler()

	override suspend fun setup() {
		scheduler.schedule(SWEEP_INTERVAL, name = "Temp-ban sweep", pollingSeconds = 10, repeat = true) {
			runCatching { liftExpiredBans() }
				.onFailure { logger.error(it) { "Failed to lift the expired temporary bans." } }
		}
	}

	private suspend fun liftExpiredBans() {
		val expired = getPendingTemporaryBans().filterNot { it.isActive }
		if (expired.isEmpty()) return

		val guild = kord.getRocketPubGuild()
		val reason = Translations.Messages.temporaryBanExpired.translate()

		expired.forEach { sanction ->
			markSanctionLifted(sanction.id)

			// The ban may already be gone, lifted by hand while the bot was down.
			guild.getBanOrNull(sanction.member) ?: return@forEach
			guild.unban(sanction.member, reason)

			logger.info { "Lifted the expired ban ${sanction.id} of ${sanction.member}" }
		}
	}
}
