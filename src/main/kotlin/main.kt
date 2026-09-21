
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.cache.lruCache
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.ratelimit.ParallelRequestRateLimiter
import dev.kord.rest.request.KtorRequestHandler
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

// Read once: these are checked on every message, and the environment doesn't change while the bot runs.
val debug = env("AYFRI_ROCKETMANAGER_ENVIRONMENT") == "development"
val adsAutomatic = env("AYFRI_ROCKETMANAGER_AUTOMATIC_SANCTIONS").toBooleanStrict()
val endMessageAutomatic = env("AYFRI_ROCKETMANAGER_AUTOMATIC_END_MESSAGE").toBooleanStrict()

/** Prefix of the chat commands the staff answers the auto-sanction embeds with, another bot handles them. */
val chatPrefix = env("AYFRI_ROCKETMANAGER_PREFIX")

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

@OptIn(ReplacingDefaultErrorResponseBuilder::class, KordUnsafe::class)
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

		// Kord's default limiter runs every REST request through one global mutex, so a single rate-limited edit
		// stalls the button acks past Discord's 3s window ("Unknown interaction"). Only requests sharing a bucket wait.
		kord {
			requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = it.token) }
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
			add(::TempBans)
			add(::UserContextSanctions)
			add(::Verifications)
		}

		errorResponse { message, failureReason ->
			val details = if (debug) "\n${failureReason.error.localizedMessage}" else ""

			content = when (failureReason) {
				is FailureReason.ProvidedCheckFailure -> Translations.Errors.insufficientPermissions.translate()
				is FailureReason.ArgumentParsingFailure -> Translations.Errors.argumentParsingError.translate() + details
				is FailureReason.OwnPermissionsCheckFailure -> Translations.Errors.insufficientPermissions.translate() + details
				is FailureReason.ExecutionError -> Translations.Errors.executionError.translate() + details
				else -> message.translate()
			}

			if (failureReason !is FailureReason.RelayedFailure) logger.error(failureReason.error) { "Command failed" }
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
