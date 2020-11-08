const {getGuild} = require('../../utils/Utils.js');
const {Command} = require('advanced-command-handler');

module.exports = new Command({
	aliases:         ['asi', 'addSI'],
	description:     'Ajoute un serveur aux serveurs interdits.',
	guildOnly:       true,
	name:            'addServeurInterdit',
	usage:           'addServeurInterdit <ID/Invitation/Nom>',
	userPermissions: ['ADMINISTRATOR']
}, async (handler, message, args) => {
	const guild = await getGuild(handler, args);
	const reason = args.slice(1).length > 1 ? args.slice(1).join(' ') : 'Non définie.';
	
	if (guild) {
		handler.forbiddenGuilds.set(guild.id ?? guild, reason);
		message.channel.send(`Le serveur '${guild.name ? `${guild.name} (${guild.id})` : guild}' a été ajouté à la liste des serveurs interdits, raison :\n> ${reason}`);
	} else message.channel.send(`Le serveur '${args[0]}' n'a pas été trouvé ou n'est pas valide :/`);
});
