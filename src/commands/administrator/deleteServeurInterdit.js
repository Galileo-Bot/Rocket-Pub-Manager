const {getGuild} = require('../../utils/Utils.js');
const {Command} = require('advanced-command-handler');

module.exports = new Command({
	aliases:         ['dsi', 'deleteSI', 'delSI'],
	description:     'Retire un serveur aux serveurs interdits.',
	guildOnly:       true,
	name:            'deleteServeurInterdit',
	usage:           'deleteServeurInterdit <ID/Invitation/Nom>',
	userPermissions: ['ADMINISTRATOR']
}, async (handler, message, args) => {
	const guild = await getGuild(handler, args);
	
	if (guild && handler.forbiddenGuilds.has(guild.id ?? guild)) {
		handler.forbiddenGuilds.delete(guild.id ?? guild);
		message.channel.send(`Le serveur '${guild.name ? `${guild.name} (${guild.id})` : guild}' a été retiré à la liste des serveurs interdits !`);
	} else message.channel.send(`Le serveur '${args[0]}' n'a pas été trouvé ou n'est pas valide :/`);
});
