import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.checks.channelFor
import dev.kordex.core.checks.userFor
import extensions.*
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

val debug get() = System.getenv("AYFRI_ROCKETMANAGER_ENVIRONMENT") == "development"
val adsAutomatic get() = System.getenv("AYFRI_ROCKETMANAGER_AUTOMATIC_SANCTIONS").toBooleanStrict()
val endMessageAutomatic get() = System.getenv("AYFRI_ROCKETMANAGER_AUTOMATIC_END_MESSAGE").toBooleanStrict()

lateinit var bot: ExtensibleBot

val dataSource = MysqlConnectionPoolDataSource().apply {
	serverName = System.getenv("AYFRI_ROCKETMANAGER_DB_IP")
	port = System.getenv("AYFRI_ROCKETMANAGER_DB_PORT").toInt()
	databaseName = System.getenv("AYFRI_ROCKETMANAGER_DB_NAME")
	password = System.getenv("AYFRI_ROCKETMANAGER_DB_MDP")
	allowMultiQueries = true
	user = System.getenv("AYFRI_ROCKETMANAGER_DB_USER")
}.also { logger.debug("Database connection initialized") }

private var oldConnection: Connection? = null

val connection: Connection
	get() {
		if (oldConnection == null) oldConnection = dataSource.connection
		oldConnection?.let {
			try {
				it.createStatement().execute("SELECT 1")
			} catch (e: Exception) {
				logger.debug("Connection is closed, creating a new one")
				oldConnection = dataSource.connection
			}
		}

		return oldConnection!!
	}

val ExtensibleBot.kord get() = getKoin().get<Kord>()

@PrivilegedIntent
suspend fun main() {
	TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

	bot = ExtensibleBot(System.getenv("AYFRI_ROCKETMANAGER_TOKEN")) {
		applicationCommands {
			slashCommandCheck {
				if (debug) logger.info("Got a slash command from ${userFor(event)?.id.enquote} in ${(channelFor(event)?.id?.toString() ?: "dm").enquote}")
				pass()
			}
		}

		chatCommands {
			enabled = true
			defaultPrefix = System.getenv("AYFRI_ROCKETMANAGER_PREFIX")
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

		hooks {
			extensionAdded {
				if (debug) logger.info { "Loaded extension: ${it.name} with ${it.slashCommands.size} slash commands, ${it.chatCommands.size} chat commands and ${it.eventHandlers.size} events" }
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

	if (debug) logger.debug("Debug mode is enabled.")
	bot.start()
}
