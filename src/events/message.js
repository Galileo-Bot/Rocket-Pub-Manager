const {isStaff} = require('../utils/Utils.js');
const {
	getThing,
	BetterEmbed
} = require('advanced-command-handler');

/**
 * @param {CommandHandlerInstance} handler - Le handler.
 * @param {Message} message - Le message.
 * @returns {Promise<void>} - Rien.
 */
module.exports = async (handler, message) => {
	if (message.author.bot || message.system) return;
	
	function hasPermission(member, ...permission) {
		return member.hasPermission(permission, {
			checkAdmin: true,
			checkOwner: true
		});
	}
	
	function createSanction(message, type, reason) {
		const embed = BetterEmbed.fromTemplate('basic', {
			client: message.client
		});
		
		embed.title = reason;
		embed.fields = [
			{
				name:  'Utilisateur incriminé :',
				value: `${message.author.tag} (${message.author.id})`
			}, {
				name:  'Salon :',
				value: message.channel
			}
		];
		embed.description = `?${type} ${message.author} ${reason}`;
		embed.thumbnail = message.author.displayAvatarURL({
			dynamic: true,
			format:  'gif'
		});
		
		if (!handler.sanctions.has(message.author.id)) {
			handler.sanctions.set(message.author.id, {
				sanctions: []
			});
		}
		
		handler.sanctions.push(message.author.id, {
			type,
			reason,
			createdAt: message.createdAt,
			case:      handler.sanctions.get(message.author.id).sanctions.length + 1
		}, 'sanctions');
		message.client.guilds.cache.get(process.env.ROCKET_PUB_ID).channels.cache.get(process.env.LOGGER_CHANNEL_ID).send(embed.description, {embed: embed.build()});
		
		handler.client.guilds.cache.get(process.env.ROCKET_PUB_ID).channels.cache
		       .filter(c => c.isText() && c.parent?.id === process.env.ADS_CATEGORY_ID)
		       .forEach(async (c) => {
			       (await c.messages.fetch())
				       .filter((m) => m.author.id === message.author.id)
				       .forEach(m => {
					       m.delete({
						       timestamp: 5000,
						       reason:    'Auto delete.'
					       });
				       });
		       });
	}
	
	const rocketPub = handler.client.guilds.cache.get(process.env.ROCKET_PUB_ID);
	const categoryChannels = rocketPub.channels.cache.filter(c => c.isText() && c.topic && c.topic.includes(process.env.FILTER_EMOJI));
	const otherChannels = rocketPub.channels.cache.filter(c => c.isText() && c.topic && c.topic.includes(process.env.FILTER_EMOJI_SHORT));
	const args = message.content.slice(handler.prefixes[0].length).trim().split(/\s+/g);
	/**
	 * @type {Command}
	 */
	const command = await getThing('command', args.shift());
	
	if (command) {
		if (command.category === 'administration') if (!hasPermission(message.member, 'ADMINISTRATOR')) return;
		if (command.category === 'moderation') if (!isStaff(message.member)) return;
		
		if (message.guild?.id === rocketPub.id && isStaff(message.member)) {
			command.run(handler, message, args);
		}
	} else if (!isStaff(message.member) && categoryChannels.concat(otherChannels).map(c => c.id).includes(message.channel.id)) {
		const invite = await handler.client.fetchInvite(message.content).catch(() => {
		});
		const mention = /@(everyone|here)/.exec(message.content);
		
		if (!/\s/.test(message.content)) createSanction(message, 'warn', 'Publicité sans description.');
		
		if (!categoryChannels.map(c => c.id).includes(message.channel.id)) return;
		
		if (message.content.split('\n').length - 1 < 5 && /\s/.test(message.content)) createSanction(message, 'warn', 'Publicité trop courte.');
		if (mention) createSanction(message, 'warn', `Tentative de mention ${mention[2]}.`);
		if (invite && handler.forbiddenGuilds.has(invite.guild.id)) createSanction(message, 'warn', 'Serveur interdit.');
	}
};
