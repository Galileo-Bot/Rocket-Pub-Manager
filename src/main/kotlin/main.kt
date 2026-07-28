import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.annotations.warnings.ReplacingDefaultErrorResponseBuilder
import dev.kordex.core.checks.channelFor
import dev.kordex.core.checks.userFor
import dev.kordex.core.types.FailureReason
import dev.kordex.core.utils.env
import extensions.*
import fr.ayfri.rocketmanager.i18n.Translations
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.util.*


val logger = KotlinLogging.logger("main")

val debug get() = env("AYFRI_ROCKETMANAGER_ENVIRONMENT") == "development"
val adsAutomatic get() = env("AYFRI_ROCKETMANAGER_AUTOMATIC_SANCTIONS").toBooleanStrict()
val endMessageAutomatic get() = env("AYFRI_ROCKETMANAGER_AUTOMATIC_END_MESSAGE").toBooleanStrict()

lateinit var bot: ExtensibleBot

val dataSource = MysqlConnectionPoolDataSource().apply {
	serverName = env("AYFRI_ROCKETMANAGER_DB_IP")
	port = env("AYFRI_ROCKETMANAGER_DB_PORT").toInt()
	databaseName = env("AYFRI_ROCKETMANAGER_DB_NAME")
	password = env("AYFRI_ROCKETMANAGER_DB_MDP")
	allowMultiQueries = true
	user = env("AYFRI_ROCKETMANAGER_DB_USER")
}.also { logger.debug { "Database connection initialized" } }

private var currentConnection: Connection? = null
private val connectionLock = Any()

/**
 * The shared database connection, re-opened whenever the server dropped it.
 *
 * [Connection.isValid] is used instead of a probe query so no statement is leaked on every access.
 */
val connection: Connection
	get() = synchronized(connectionLock) {
		val existing = currentConnection
		if (existing != null && runCatching { existing.isValid(2) }.getOrDefault(false)) return@synchronized existing

		logger.debug { "Opening a new database connection" }
		dataSource.connection.also { currentConnection = it }
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

		chatCommands {
			enabled = true
			defaultPrefix = env("AYFRI_ROCKETMANAGER_PREFIX")
		}

		extensions {
			sentry { enable = false }

			add(::AutoSanctions)
			add(::BannedGuilds)
			add(::CheckAds)
			add(::EndMessage)
			add(::Errors)
			add(::ModifySanctions)
			add(::RemoveAds)
			add(::Sanctions)
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

		intents { +Intents.ALL }

		presence {
			status = PresenceStatus.Idle
			listening(" les membres.")
		}
	}

	if (debug) logger.debug { "Debug mode is enabled." }
	bot.start()
}
