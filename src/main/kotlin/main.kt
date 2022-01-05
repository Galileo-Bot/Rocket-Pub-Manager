
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.channelFor
import com.kotlindiscord.kord.extensions.checks.userFor
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import extensions.BannedGuilds
import extensions.CheckAds
import extensions.RemoveAds
import extensions.Sanctions
import extensions.Verifications
import io.github.cdimascio.dotenv.dotenv
import mu.KotlinLogging
import utils.ROCKET_PUB_GUILD_STAFF
import java.sql.Connection
import java.util.*


val logger = KotlinLogging.logger("main")
val configuration = dotenv()
val debug get() = configuration["AYFRI_ROCKETMANAGER_ENVIRONMENT"] == "development"
lateinit var bot: ExtensibleBot
lateinit var connection: Connection

@PrivilegedIntent
suspend fun main() {
	bot = ExtensibleBot(configuration["AYFRI_ROCKETMANAGER_TOKEN"]) {
		applicationCommands {
			defaultGuild = ROCKET_PUB_GUILD_STAFF
			
			slashCommandCheck {
				if (debug) println("Got a message from ${userFor(event)?.id} in ${channelFor(event)?.id ?: "dm"}")
				pass()
			}
		}
		
		cache {
			cachedMessages = 1000
		}
		
		chatCommands {
			enabled = true
			defaultPrefix = configuration["AYFRI_ROCKETMANAGER_PREFIX"]
		}
		
		extensions {
			sentry { enable = false }
			
			add(::BannedGuilds)
			add(::CheckAds)
			add(::RemoveAds)
			add(::Sanctions)
			add(::Verifications)
		}
		
		hooks {
			extensionAdded {
				if (debug) logger.info("Loaded extension: ${it.name} with ${it.slashCommands.size} slash commands, ${it.chatCommands.size} chat commands and ${it.eventHandlers.size} events")
			}
		}
		
		i18n { defaultLocale = Locale.FRENCH }
		
		intents { +Intents.all }
		
		presence {
			status = PresenceStatus.Idle
			listening(" les membres.")
		}
	}
	
	val dataSource = MysqlConnectionPoolDataSource()
	dataSource.apply {
		serverName = configuration["AYFRI_ROCKETMANAGER_DB_IP"]
		port = configuration["AYFRI_ROCKETMANAGER_DB_PORT"].toInt()
		databaseName = configuration["AYFRI_ROCKETMANAGER_DB_NAME"]
		password = configuration["AYFRI_ROCKETMANAGER_DB_MDP"]
		allowMultiQueries = true
		user = configuration["AYFRI_ROCKETMANAGER_DB_USER"]
	}
	connection = dataSource.connection
	logger.info("Connection to the Database established.")
	if (debug) logger.debug("Debug mode is enabled.")
	
	bot.start()
}

