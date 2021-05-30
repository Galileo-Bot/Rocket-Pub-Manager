import com.kotlindiscord.kord.extensions.ExtensibleBot
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
import java.sql.Connection
import java.util.*


val logger = KotlinLogging.logger("main")
val configuration = dotenv()
lateinit var bot: ExtensibleBot
lateinit var connection: Connection

@PrivilegedIntent
suspend fun main() {
	bot = ExtensibleBot(configuration["TOKEN"]) {
		cache {
			cachedMessages = 1000
		}
		
		extensions {
			sentry = false
			add(::BannedGuilds)
			add(::CheckAds)
			add(::RemoveAds)
			add(::Sanctions)
			add(::Verifications)
		}
		
		i18n {
			defaultLocale = Locale.FRENCH
		}
		
		intents {
			+Intents.all
		}
		
		presence {
			status = PresenceStatus.Idle
			listening(" les membres.")
		}
		
		slashCommands {
			enabled = true
		}
	}
	
	val dataSource = MysqlConnectionPoolDataSource()
	dataSource.apply {
		serverName = configuration["DB_IP"]
		port = configuration["DB_PORT"].toInt()
		databaseName = configuration["DB_NAME"]
		password = configuration["DB_MDP"]
		allowMultiQueries = true
		user = configuration["DB_USER"]
	}
	connection = dataSource.connection
	logger.info("Connection to DataBase established !")
	
	bot.start()
}

