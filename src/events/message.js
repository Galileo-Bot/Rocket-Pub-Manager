const {
	getThing,
	BetterEmbed
} = require('advanced-command-handler');

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
		
		handler.sanctions.push("sanctions", {
			type,
			reason,
			createdAt: message.createdAt,
			case: handler.sanctions.get(message.author.id, "sanctions").length + 1
		}, message.author.id)
		
		message.client.guilds.cache.get(process.env.ROCKET_PUB_ID).channels.cache.get(process.env.LOGGER_CHANNEL_ID).send(embed.description, {embed: embed.build()});
	}
	
	function isStaff(member) {
		return member.roles.cache.has('494521544618278934');
	}
	
	const rocketPub = handler.client.guilds.cache.get(process.env.ROCKET_PUB_ID);
	const channels = rocketPub.channels.cache.filter(c => c.isText() && c.topic && c.topic.includes(process.env.FILTER_EMOJI));
	const args = message.content.slice(handler.prefixes[0].length).trim().split(/\s+/g);
	/**
	 * @type {Command}
	 */
	const command = await getThing('command', args.shift());
	
	if (command) {
		if (message.guild?.id === rocketPub.id && isStaff(message.member)) {
			command.run(handler, message, args);
		}
	} else if (channels.map(c => c.id).includes(message.channel.id)) {
		if (isStaff(message.member)) return;
		
		let invite;
		await handler.client
		             .fetchInvite(message.content)
		             .then(i => (invite = i))
		             .catch(() => {
		             });
		
		if (message.content.split('\n').length - 1 < 5) {
			createSanction(message, 'warn', 'Publicité trop courte.');
		}
		
		if (!invite) return;
		
		if (handler.forbiddenGuilds.has(invite.guild.id)) {
			createSanction(message, 'warn', 'Serveur interdit.');
		}
		
		if (!/\s/.test(message.content)) {
			createSanction(message, 'warn', 'Publicité sans description.');
		}
	}
};
