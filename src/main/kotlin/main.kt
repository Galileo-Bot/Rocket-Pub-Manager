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
import utils.enquote
import java.sql.Connection
import java.util.*


val logger = KotlinLogging.logger("main")
val configuration = dotenv {
	ignoreIfMissing = true
	systemProperties = true
}

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

private var oldConnection: Connection? = null

val connection: Connection
	get() {
		if (oldConnection == null) oldConnection = dataSource.connection
		oldConnection?.let {
			try {
				it.createStatement().execute("SELECT 1")
			} catch (e: Exception) {
				logger.debug { "Connection is closed, creating a new one" }
				oldConnection = dataSource.connection
			}
		}

		return oldConnection!!
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
				logger.debug { "Got a slash command from ${user?.id.enquote} in ${(channel?.id?.toString() ?: "dm").enquote}" }
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
				is FailureReason.RelayedFailure -> {
					if (debug) logger.error { "Relayed failure: ${failureReason.error}" }
					// Handle relayed failures (errors thrown by the command itself)
					failureReason.error.message
				}

				is FailureReason.ProvidedCheckFailure -> {
					if (debug) logger.error { "Check failure: ${failureReason.error}" }
					// Handle check failures (permission checks, etc.)
					Translations.Errors.insufficientPermissions.translate()
				}

				is FailureReason.ArgumentParsingFailure -> {
					if (debug) logger.error { "Argument parsing failure: ${failureReason.error.localizedMessage}" }
					Translations.Errors.argumentParsingError.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")
				}

				is FailureReason.OwnPermissionsCheckFailure -> {
					if (debug) logger.error { "Own permissions check failure: ${failureReason.error.localizedMessage}" }
					Translations.Errors.insufficientPermissions.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")
				}

				is FailureReason.ExecutionError -> {
					if (debug) logger.error { "Execution error: ${failureReason.error.localizedMessage}" }
					Translations.Errors.executionError.translate() + (if (debug) "\n${failureReason.error.localizedMessage}" else "")
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

	logger.debug { "Debug mode is enabled." }
	bot.start()
}
