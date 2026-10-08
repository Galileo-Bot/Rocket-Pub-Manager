package storage

import dev.kord.common.entity.Snowflake
import dev.kordex.core.utils.envOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * The settings edited from `/config`, held in memory since the ad channels are checked on every guild message.
 *
 * A setting never saved falls back to its `AYFRI_ROCKETMANAGER_<NAME>` environment variable, so an existing `.env` keeps working.
 */
object Settings {
	private val values = ConcurrentHashMap(
		sqlQuery("SELECT name, value FROM settings") { result -> result.mapRows { it.getString("name") to it.getString("value") }.toMap() }
	)

	private val adChannelIds: MutableSet<Snowflake> = ConcurrentHashMap.newKeySet<Snowflake>().apply {
		addAll(sqlQuery("SELECT channelID FROM ad_channels") { result -> result.mapRows { Snowflake(it.getString("channelID")) } })
	}

	val adChannels: Set<Snowflake> get() = adChannelIds

	/** On until turned off from `/config`, its environment variable was never honoured so the ads have always been checked. */
	var automaticSanctions: Boolean
		get() = values["automatic_sanctions"]?.toBooleanStrict() ?: true
		set(value) = write("automatic_sanctions", value.toString())

	var automaticEndMessage: Boolean
		get() = read("automatic_end_message")?.toBooleanStrict() == true
		set(value) = write("automatic_end_message", value.toString())

	/** The channels can't be unset, `/config` only ever replaces them. */
	var sanctionLogsChannel: Snowflake?
		get() = read("channel_sanction_id")?.let(::Snowflake)
		set(value) = write("channel_sanction_id", requireNotNull(value).toString())

	var verifChannel: Snowflake?
		get() = read("channel_verif_id")?.let(::Snowflake)
		set(value) = write("channel_verif_id", requireNotNull(value).toString())

	var verifLogsChannel: Snowflake?
		get() = read("channel_verif_logs_id")?.let(::Snowflake)
		set(value) = write("channel_verif_logs_id", requireNotNull(value).toString())

	/** Set once the channels marked by the emote in their topic were imported, an emptied list must stay empty. */
	var adChannelsImported: Boolean
		get() = values["ad_channels_imported"] == "true"
		set(value) = write("ad_channels_imported", value.toString())

	/** @return the channels that were not in the list yet. */
	fun addAdChannels(channels: Collection<Snowflake>) = channels.filter(adChannelIds::add).onEach {
		sqlUpdate("INSERT OR IGNORE INTO ad_channels (channelID) VALUES (?)", it.toString())
	}

	/** @return the channels that were in the list. */
	fun removeAdChannels(channels: Collection<Snowflake>) = channels.filter(adChannelIds::remove).onEach {
		sqlUpdate("DELETE FROM ad_channels WHERE channelID = ?", it.toString())
	}

	private fun read(name: String) = values[name] ?: envOrNull("AYFRI_ROCKETMANAGER_${name.uppercase()}")?.ifBlank { null }

	private fun write(name: String, value: String) {
		sqlUpdate("INSERT INTO settings (name, value) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET value = excluded.value", name, value)
		values[name] = value
	}
}
