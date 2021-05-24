import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import extensions.BannedGuilds
import extensions.CheckAds
import extensions.Sanctions
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
			add(::CheckAds)
			add(::BannedGuilds)
			add(::Sanctions)
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
		serverName = configuration["IP"]
		port = configuration["PORT"].toInt()
		databaseName = "Galileo"
		password = configuration["MDP"]
		allowMultiQueries = true
		user = configuration["USER"]
	}
	connection = dataSource.connection
	logger.info("Connection to DataBase established !")
	
	bot.start()
}

