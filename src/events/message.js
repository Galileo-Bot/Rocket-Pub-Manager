const {
	getThing,
	BetterEmbed
} = require('advanced-command-handler');

module.exports = async (handler, message) => {
	if (message.author.bot || message.system) return;
	
	function hasPermission(member, permission) {
		return member.hasPermission(permission, {
			checkAdmin: true,
			checkOwner: true
		});
	}
	
	const rocketPub = handler.client.guilds.cache.get(process.env.ROCKET_PUB_ID);
	const channels = rocketPub.channels.cache.filter(c => c.isText() && c.topic && c.topic.includes(process.env.FILTER_EMOJI));
	
	if (channels.map(c => c.id).includes(message.channel.id)) {
		// if (hasPermission(message.member, 'ADMINISTRATOR')) return;
		
		let invite;
		await handler.client.fetchInvite(message.content).then(i => invite = i).catch(() => {
		});
		
		
		if (!invite) return;
		
		if (handler.forbiddenGuilds.has(invite.guild.id)) {
			createSanction(message, 'warn', 'Serveur interdit.');
		}
		
		if (invite && message.content.match(/https:\/\/discord.gg\/[A-Za-z0-9]{7}/)) {
			createSanction(message, 'warn', 'Publicité sans description.');
		}
	}
	
	const args = message.content.slice(handler.prefixes[0].length).trim().split(/\s+/g);
	/**
	 * @type {Command}
	 */
	const command = await getThing('command', args.shift());
	
	if (command) {
		if (message.guild?.id === rocketPub.id && hasPermission(message.member, 'ADMINISTRATOR')) {
			command.run(handler, message, args);
		}
	}
};

function createSanction(message, type, sanction) {
	const embed = BetterEmbed.fromTemplate('basic', {
		client: message.client
	});
	embed.title = sanction;
	embed.fields = [
		{
			name:  'Utilisateur incriminé :',
			value: `${message.author.tag} (${message.author.id})`
		}, {
			name:  'Salon :',
			value: message.channel
		}
	];
	embed.description = `?${type} ${message.author} ${sanction}`;
	embed.thumbnail = message.author.displayAvatarURL({
		dynamic: true,
		format:  'gif'
	});
	
	message.client.guilds.cache.get(process.env.ROCKET_PUB_ID).channels.cache.get(process.env.LOGGER_CHANNEL_ID).send(embed.description, {embed: embed.build()});
}
