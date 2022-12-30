package utils

import com.kotlindiscord.kord.extensions.checks.channelFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import configuration
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.supplier.EntitySupplyStrategy

const val DISCORD_INVITE_LINK_REGEX = "(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z\\d-]+)"
const val AD_CATEGORY_CHANNEL_EMOTE = "🔗"
const val AD_CHANNEL_EMOTE = "<:validate:525405975289659402>"

/** Salon où les erreurs du bot sont envoyées */
val ERROR_CHANNEL = Snowflake("864756196539105290")

/** Serveur du bot */
val ROCKET_PUB_GUILD = Snowflake("465918902254436362")

/** Serveur staff */
val ROCKET_PUB_GUILD_STAFF = Snowflake("770763755265064980")

/** Salon des logs pour les sanctions */
val SANCTION_LOGS_CHANNEL = Snowflake(configuration["AYFRI_ROCKETMANAGER_CHANNEL_SANCTION_ID"])

/** Rôle de staff */
val STAFF_ROLE = Snowflake("494521544618278934")

/** Émoji de validation */
val VALID_EMOJI = Snowflake("525405975289659402")

/** Salon des vérifications */
val VERIF_CHANNEL = Snowflake(configuration["AYFRI_ROCKETMANAGER_CHANNEL_VERIF_ID"])

/** Salon des logs pour les vérifications */
val VERIF_LOGS_CHANNEL = Snowflake(configuration["AYFRI_ROCKETMANAGER_CHANNEL_VERIF_LOGS_ID"])

fun ChannelBehavior.isAdChannel() = this is TextChannel && topic?.contains(AD_CHANNEL_EMOTE) == true
fun ChannelBehavior.isCategoryChannel() = this is TextChannel && topic?.contains(AD_CATEGORY_CHANNEL_EMOTE) == true

suspend fun <T : Event> CheckContext<T>.isAdChannel() {
	if (!passed) return
	val channel = channelFor(event)
	
	if (channel == null) fail("Channel is null")
	failIfNot("Channel isn't an ad channel.") { channel!!.fetchChannel().isAdChannel() }
}

suspend fun <T : Event> CheckContext<T>.isInAdCategoryChannel() {
	if (!passed) return
	val channel = channelFor(event) ?: return
	failIfNot("Channel isn't an ad category channel.") { channel.fetchChannel().isCategoryChannel() }
}

suspend fun Kord.getVerifChannel() = getChannelOf<TextChannel>(VERIF_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
suspend fun Kord.getLogSanctionsChannel() = getChannelOf<TextChannel>(SANCTION_LOGS_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
suspend fun Kord.getVerifLogsChannel() = getChannelOf<TextChannel>(VERIF_LOGS_CHANNEL, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
suspend fun Kord.getRocketPubGuild() = getGuildOrNull(ROCKET_PUB_GUILD, EntitySupplyStrategy.cacheWithCachingRestFallback)!!
