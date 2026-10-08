package extensions

import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.channel.ChannelDeleteEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.rest.builder.component.ComponentContainerBuilder
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.container
import dev.kord.rest.builder.message.messageFlags
import dev.kordex.core.checks.hasPermission
import dev.kordex.core.components.ComponentRegistry
import dev.kordex.core.components.buttons.EphemeralInteractionButton
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.components.menus.OPTIONS_MAX
import dev.kordex.core.components.menus.channel.EphemeralChannelSelectMenu
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.i18n.Key
import fr.ayfri.rocketmanager.i18n.Translations
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import logger
import org.koin.core.component.inject
import storage.Settings
import utils.AD_CHANNEL_EMOTE
import utils.CHART_ACCENT_HEX
import utils.ROCKET_PUB_GUILD
import utils.asMention
import utils.getRocketPubGuild
import kotlin.reflect.KMutableProperty0

private typealias ChannelMenu = EphemeralChannelSelectMenu<ModalForm>
private typealias Button = EphemeralInteractionButton<ModalForm>

/** Every mention takes ~22 of the 4000 characters a message may display, the list stops there. */
private const val MAX_LISTED_AD_CHANNELS = 80

/**
 * The `/config` message: every setting next to the component editing it, redrawn after each change.
 *
 * The components have fixed IDs and are registered once, so a panel opened before a restart keeps working. Their
 * defaults and styles are set right before each render, the panel being only ever used by a handful of admins.
 */
private class ConfigPanel : KordExKoinComponent {
	private val registry: ComponentRegistry by inject()

	private val addAdChannels = adChannelsMenu("config-add-ad-channels", Translations.Config.addAdChannels, Settings::addAdChannels)
	private val removeAdChannels = adChannelsMenu("config-remove-ad-channels", Translations.Config.removeAdChannels, Settings::removeAdChannels)
	private val sanctionLogsChannel = channelMenu("config-sanction-logs-channel", Settings::sanctionLogsChannel)
	private val verifChannel = channelMenu("config-verif-channel", Settings::verifChannel)
	private val verifLogsChannel = channelMenu("config-verif-logs-channel", Settings::verifLogsChannel)
	private val automaticSanctions = toggle("config-automatic-sanctions", Translations.Config.automaticSanctions, Settings::automaticSanctions)
	private val automaticEndMessage =
		toggle("config-automatic-end-message", Translations.Config.automaticEndMessage, Settings::automaticEndMessage)

	suspend fun register() = listOf(
		addAdChannels,
		removeAdChannels,
		sanctionLogsChannel,
		verifChannel,
		verifLogsChannel,
		automaticSanctions,
		automaticEndMessage,
	).forEach {
		it.validate()
		registry.register(it)
	}

	fun MessageBuilder.render(): Unit = container {
		accentColor = Color(CHART_ACCENT_HEX)
		textDisplay(Translations.Config.title.translate())
		separator {}

		val adChannels = Settings.adChannels.map { it.asMention<ChannelBehavior>() }.sorted()
		val shown = adChannels.take(MAX_LISTED_AD_CHANNELS).joinToString(" ").ifEmpty { Translations.Config.noAdChannels.translate() }
		val hidden = adChannels.size - MAX_LISTED_AD_CHANNELS
		textDisplay(
			Translations.Config.adChannels.translateNamed(
				"count" to adChannels.size,
				"channels" to if (hidden > 0) "$shown ${Translations.Config.moreChannels.translateNamed("count" to hidden)}" else shown
			)
		)
		actionRow { addAdChannels.apply(this) }
		actionRow { removeAdChannels.apply(this) }
		separator {}

		textDisplay(Translations.Config.channels.translate())
		channelSetting(Translations.Config.sanctionLogsChannel, sanctionLogsChannel, Settings.sanctionLogsChannel)
		channelSetting(Translations.Config.verifChannel, verifChannel, Settings.verifChannel)
		channelSetting(Translations.Config.verifLogsChannel, verifLogsChannel, Settings.verifLogsChannel)
		separator {}

		textDisplay(Translations.Config.automations.translate())
		automaticSanctions.style = if (Settings.automaticSanctions) ButtonStyle.Success else ButtonStyle.Secondary
		automaticEndMessage.style = if (Settings.automaticEndMessage) ButtonStyle.Success else ButtonStyle.Secondary
		actionRow {
			automaticSanctions.apply(this)
			automaticEndMessage.apply(this)
		}
	}

	private fun ComponentContainerBuilder.channelSetting(label: Key, menu: ChannelMenu, value: Snowflake?) {
		textDisplay("**${label.translate()}**")
		menu.defaultChannels = listOfNotNull(value).toMutableList()
		actionRow { menu.apply(this) }
	}

	/** Ad channels are picked one by one or by category, a category standing for every text channel it holds right now. */
	private fun adChannelsMenu(id: String, placeholder: Key, update: (Collection<Snowflake>) -> List<Snowflake>): ChannelMenu =
		ChannelMenu(null).apply {
			this.id = id
			this.placeholder = placeholder
			maximumChoices = OPTIONS_MAX
			channelType(ChannelType.GuildText, ChannelType.GuildNews, ChannelType.GuildCategory)

			action {
				val picked = selected.mapTo(mutableSetOf()) { it.id }
				val channels = kord.getRocketPubGuild().channels
					.filterIsInstance<CategorizableChannel>()
					.filter { (it is TextChannel || it is NewsChannel) && (it.id in picked || it.categoryId?.let(picked::contains) == true) }
					.map { it.id }
					.toList()

				update(channels)
				edit { render() }
			}
		}

	private fun channelMenu(id: String, setting: KMutableProperty0<Snowflake?>): ChannelMenu = ChannelMenu(null).apply {
		this.id = id
		placeholder = Translations.Config.notSet
		channelType(ChannelType.GuildText)

		action {
			setting.set(selected.single().id)
			edit { render() }
		}
	}

	private fun toggle(id: String, label: Key, setting: KMutableProperty0<Boolean>): Button = Button(null).apply {
		this.id = id
		this.label = label

		action {
			setting.set(!setting.get())
			edit { render() }
		}
	}
}

class Config : Extension() {
	override val name = "Config"

	override suspend fun setup() {
		val panel = ConfigPanel().apply { register() }

		// The ad channels were marked by an emote in their topic before /config stored them, they are imported once.
		event<GuildCreateEvent> {
			check { failIf(event.guild.id != ROCKET_PUB_GUILD || Settings.adChannelsImported) }

			action {
				val marked = event.guild.channels
					.filterIsInstance<TextChannel>()
					.filter { it.topic?.contains(AD_CHANNEL_EMOTE) == true }
					.map { it.id }
					.toList()

				Settings.addAdChannels(marked)
				Settings.adChannelsImported = true
				logger.info { "Imported ${marked.size} ad channels marked in their topic" }
			}
		}

		event<ChannelDeleteEvent> {
			check { failIf(event.channel.id !in Settings.adChannels) }
			action { Settings.removeAdChannels(listOf(event.channel.id)) }
		}

		ephemeralSlashCommand {
			name = Translations.Commands.Config.name
			description = Translations.Commands.Config.description
			scope.limitToGuild(ROCKET_PUB_GUILD)
			requirePermission(Permission.ManageGuild)
			check { hasPermission(Permission.ManageGuild) }

			action {
				respond {
					messageFlags { +MessageFlag.IsComponentsV2 }
					with(panel) { render() }
				}
			}
		}
	}
}
