package extensions

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.embed
import dev.kordex.core.utils.deleteIgnoringNotFound
import dev.kordex.core.utils.getJumpUrl
import fr.ayfri.rocketmanager.i18n.Translations
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import storage.Sanction
import storage.SanctionType
import utils.*
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Iterated by the events and the bin buttons concurrently, the writes are rare. */
val sanctionMessages = CopyOnWriteArrayList<SanctionMessage>()

suspend fun TextChannelBehavior.lightSanction(
	member: Member,
	reason: String,
	message: Message? = null,
	appliedBy: Snowflake = kord.selfId,
) {
	createMessage {
		val welcome =
			if (LocalTime.now().hour in 6..18) Translations.Messages.goodMorning.translate() else Translations.Messages.goodEvening.translate()

		val shownReason = message?.let {
			Translations.Messages.lightWarnReasonWithChannel.translateNamed(
				"reason" to reason.dropLast(1),
				"channel" to it.channel.mention
			)
		} ?: reason

		content = Translations.Messages.lightWarnMessage.translateNamed(
			"welcome" to welcome,
			"member" to member.mention,
			"reason" to shownReason
		)

		allowedMentions {
			users += member.id
		}
	}

	Sanction(SanctionType.LIGHT_WARN, reason, member.id, appliedBy = appliedBy).save()

	// Off the event handler, which holds the ads lock: the member gets a few seconds to read the warning first.
	message?.let { kord.launch { delay(5.seconds); it.deleteIgnoringNotFound() } }
}

suspend fun autoSanctionMessage(message: Message, type: SanctionType, reason: String) {
	val sanction = Sanction(type, reason, message.author!!.id)
	val channelToSend = message.kord.getVerifChannel()

	val old = sanctionMessages.find {
		it.sanction.member == sanction.member && it.sanction.reason == sanction.reason && it.sanction.type == sanction.type
	}

	if (old != null) {
		// The embed already lists the previous ads, appending a link costs no fetch.
		val links = (old.sanctionMessage.sanctionedAdLinks() + message.getJumpUrl()).distinct()

		when (links.size) {
			1 -> return

			in 5..9 -> {
				sanction.type = SanctionType.MUTE
				sanction.durationMS = links.size.div(2).days.inWholeMilliseconds
			}

			in 10..Int.MAX_VALUE -> {
				sanction.type = SanctionType.MUTE
				sanction.durationMS = links.size.days.inWholeMilliseconds
				sanction.reason = Translations.Messages.adInAllCategories.translate()
			}
		}

		old.sanctionMessage = old.sanctionMessage.edit {
			embed {
				autoSanctionEmbed(message, sanction, links)
			}
		}
		return
	}

	channelToSend.createMessage {
		embed {
			autoSanctionEmbed(message, sanction)
		}

		with(sanctionMessageButtons()) { applyToMessage() }
	}.also {
		sanctionMessages.add(SanctionMessage(it, sanction))
	}
}
