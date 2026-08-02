
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.cache.lruCache
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.annotations.warnings.ReplacingDefaultErrorResponseBuilder
import dev.kordex.core.checks.channelFor
import dev.kordex.core.checks.userFor
import dev.kordex.core.types.FailureReason
import dev.kordex.core.utils.env
import extensions.*
import extensions.sanctions.*
import fr.ayfri.rocketmanager.i18n.Translations
import io.github.oshai.kotlinlogging.KotlinLogging
import org.sqlite.SQLiteConfig
import storage.applySchema
import java.sql.Connection
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories


val logger = KotlinLogging.logger("main")

val debug get() = env("AYFRI_ROCKETMANAGER_ENVIRONMENT") == "development"
val adsAutomatic get() = env("AYFRI_ROCKETMANAGER_AUTOMATIC_SANCTIONS").toBooleanStrict()
val endMessageAutomatic get() = env("AYFRI_ROCKETMANAGER_AUTOMATIC_END_MESSAGE").toBooleanStrict()

lateinit var bot: ExtensibleBot

/** `date_class = TEXT` stores timestamps as [DATE_FORMAT] instead of epoch millis, which SQLite's `DATE()` needs. */
private val sqliteConfig = SQLiteConfig().apply {
	setDateClass(SQLiteConfig.DateClass.TEXT.value)
	setDateStringFormat(DATE_FORMAT)
	setJournalMode(SQLiteConfig.JournalMode.WAL)
	setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
	setBusyTimeout(5_000)
}

const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

val connection: Connection by lazy {
	val path = Path(env("AYFRI_ROCKETMANAGER_DB_PATH")).absolute()
	logger.info { "Opening the SQLite database at $path" }

	// SQLite creates the file but not the directories leading to it.
	path.parent?.createDirectories()

	sqliteConfig.createConnection("jdbc:sqlite:$path").also { it.applySchema() }
}

val ExtensibleBot.kord get() = getKoin().get<Kord>()

@OptIn(ReplacingDefaultErrorResponseBuilder::class)
@PrivilegedIntent
suspend fun main() {
	TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

	bot = ExtensibleBot(env("AYFRI_ROCKETMANAGER_TOKEN")) {
		applicationCommands {
			slashCommandCheck {
				val user = userFor(event)
				val channel = channelFor(event)
				logger.debug { "Got a slash command from ${user?.id} in ${channel?.id ?: "dm"}" }
				pass()
			}
		}

		// Kord caches every entity type unbounded by default, anything missing here falls back to REST.
		cache {
			cachedMessages = 0

			kord {
				emojis(none())
				members(lruCache(500))
				presences(none())
				users(lruCache(500))
				voiceState(none())
			}
		}

		chatCommands {
			enabled = true
			defaultPrefix = env("AYFRI_ROCKETMANAGER_PREFIX")
		}

		extensions {
			sentry { enable = false }

			add(::BannedGuilds)
			add(::CheckAds)
			add(::DetectSanctions)
			add(::EndMessage)
			add(::Errors)
			add(::ModifySanctions)
			add(::RemoveAds)
			add(::Sanctions)
			add(::Stats)
			add(::UserContextSanctions)
			add(::Verifications)
		}

		errorResponse { message, failureReason ->
			val userMsg = when (failureReason) {
				is FailureReason.ProvidedCheckFailure ->
					// Handle check failures (permission checks, etc.)
					Translations.Errors.insufficientPermissions.translate()

				is FailureReason.ArgumentParsingFailure ->
					Translations.Errors.argumentParsingError.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")

				is FailureReason.OwnPermissionsCheckFailure ->
					Translations.Errors.insufficientPermissions.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")

				is FailureReason.ExecutionError ->
					Translations.Errors.executionError.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")

				else -> message.translate()
			}

			if (failureReason !is FailureReason.RelayedFailure) {
				logger.error {
					"""
					${failureReason.error.stackTraceToString()}
					""".trimIndent()
				}
			}

			this.content = userMsg
		}

		hooks {
			extensionAdded {
				// Remove unnecessary default kordex about extension
				if (it.name == "kordex.about") {
					removeExtension(it.name)
				}

				logger.debug { "Loaded extension: ${it.name} with ${it.slashCommands.size} slash commands, ${it.chatCommands.size} chat commands and ${it.eventHandlers.size} events" }
			}
		}

		i18n {
			defaultLocale = Locale.FRENCH
		}

		// Only what the event handlers need, presences would ship every member of every guild at startup.
		intents(addDefaultIntents = false) {
			+Intent.Guilds
			+Intent.GuildMembers
			+Intent.GuildModeration
			+Intent.GuildMessages
			+Intent.MessageContent
		}

		presence {
			status = PresenceStatus.Idle
			listening(" les membres.")
		}
	}

	if (debug) logger.debug { "Debug mode is enabled." }
	bot.start()
}
