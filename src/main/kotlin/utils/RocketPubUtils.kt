package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.checks.channelFor
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.i18n.Key
import fr.ayfri.rocketmanager.i18n.Translations
import storage.Settings
import kotlin.time.Duration.Companion.minutes

const val DISCORD_INVITE_LINK_REGEX =
	"(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z\\d-]+)"

/** Marked the ad channels in their topic before `/config` stored them, only read to import them once. */
const val AD_CHANNEL_EMOTE = "<:validate:525405975289659402>"

/** Window during which new ad messages from the same author are grouped into the same pending [entities.Verification], even if their content differs across channels. */
val AD_GROUPING_WINDOW = 10.minutes

/** Serveur du bot */
val ROCKET_PUB_GUILD = Snowflake("465918902254436362")

/** Rôle de staff */
val STAFF_ROLE = Snowflake("494521544618278934")

/** Émoji de validation */
val VALID_EMOJI = Snowflake("525405975289659402")

fun messageJumpUrl(channelId: Snowflake, messageId: Snowflake) = "https://discord.com/channels/$ROCKET_PUB_GUILD/$channelId/$messageId"

/** An in-memory set lookup, this check runs on every message of the guild. */
suspend fun <T : Event> CheckContext<T>.isAdChannel() {
	if (!passed) return
	failIfNot(Translations.Errors.channelNotAdChannel) { channelFor(event)?.id in Settings.adChannels }
}

/** Fails with a message pointing to `/config` while the channel [name] is not configured. */
private suspend fun Kord.getConfiguredChannel(id: Snowflake?, name: Key) = id
	?.let { getChannelOf<TextChannel>(it, EntitySupplyStrategy.cacheWithCachingRestFallback) }
	?: throw DiscordRelayedException(Translations.Errors.channelNotConfigured.withNamedPlaceholders("channel" to name.translate()))

suspend fun Kord.getVerifChannel() = getConfiguredChannel(Settings.verifChannel, Translations.Config.verifChannel)

suspend fun Kord.getLogSanctionsChannel() = getConfiguredChannel(Settings.sanctionLogsChannel, Translations.Config.sanctionLogsChannel)

suspend fun Kord.getVerifLogsChannel() = getConfiguredChannel(Settings.verifLogsChannel, Translations.Config.verifLogsChannel)

suspend fun Kord.getRocketPubGuild() =
	getGuildOrNull(ROCKET_PUB_GUILD, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
