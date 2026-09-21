package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.checks.channelFor
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.utils.env
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Duration.Companion.minutes

const val DISCORD_INVITE_LINK_REGEX =
	"(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z\\d-]+)"
const val AD_CATEGORY_CHANNEL_EMOTE = "🔗"
const val AD_CHANNEL_EMOTE = "<:validate:525405975289659402>"

/** Window during which new ad messages from the same author are grouped into the same pending [entities.Verification], even if their content differs across channels. */
val AD_GROUPING_WINDOW = 10.minutes

/** Serveur du bot */
val ROCKET_PUB_GUILD = Snowflake("465918902254436362")

/** Salon des logs pour les sanctions */
val SANCTION_LOGS_CHANNEL = Snowflake(env("AYFRI_ROCKETMANAGER_CHANNEL_SANCTION_ID"))

/** Rôle de staff */
val STAFF_ROLE = Snowflake("494521544618278934")

/** Émoji de validation */
val VALID_EMOJI = Snowflake("525405975289659402")

/** Salon des vérifications */
val VERIF_CHANNEL = Snowflake(env("AYFRI_ROCKETMANAGER_CHANNEL_VERIF_ID"))

/** Salon des logs pour les vérifications */
val VERIF_LOGS_CHANNEL = Snowflake(env("AYFRI_ROCKETMANAGER_CHANNEL_VERIF_LOGS_ID"))

fun messageJumpUrl(channelId: Snowflake, messageId: Snowflake) = "https://discord.com/channels/$ROCKET_PUB_GUILD/$channelId/$messageId"

fun ChannelBehavior.isAdChannel() = this is TextChannel && topic?.contains(AD_CHANNEL_EMOTE) == true
fun ChannelBehavior.isCategoryChannel() = this is TextChannel && topic?.contains(AD_CATEGORY_CHANNEL_EMOTE) == true

/**
 * Guild channels are cached by the `Guilds` intent and kept fresh by `CHANNEL_UPDATE`, so this never has to hit
 * REST: these checks run on every message of the guild.
 */
suspend fun <T : Event> CheckContext<T>.isAdChannel() {
	if (!passed) return
	val channel = channelFor(event)

	if (channel == null) fail(Translations.Errors.channelIsNull)
	failIfNot(Translations.Errors.channelNotAdChannel) { channel!!.asChannelOrNull()?.isAdChannel() == true }
}

suspend fun Kord.getVerifChannel() =
	getChannelOf<TextChannel>(VERIF_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!

suspend fun Kord.getLogSanctionsChannel() =
	getChannelOf<TextChannel>(SANCTION_LOGS_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!

suspend fun Kord.getVerifLogsChannel() =
	getChannelOf<TextChannel>(VERIF_LOGS_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!

suspend fun Kord.getRocketPubGuild() =
	getGuildOrNull(ROCKET_PUB_GUILD, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
