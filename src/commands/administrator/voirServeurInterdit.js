const {getGuild} = require('../../utils/Utils.js');
const {
	Command,
	BetterEmbed
} = require('advanced-command-handler');

module.exports = new Command({
	name:            'voirServeurInterdit',
	aliases:         ['vsi', 'voirSI'],
	description:     'Permet de savoir si un serveur est interdit ou d\'avoir la liste complète des serveurs interdits.',
	usage:           'voirServeurInterdit <ID/Invitation/Nom>\nvoirServeurInterdit liste/list/ls',
	userPermissions: ['ADMINISTRATOR']
}, async (handler, message, args) => {
	const embed = BetterEmbed.fromTemplate('basic', {
		client: handler.client
	});
	
	if (['liste', 'list', 'ls'].includes(args[0])) {
		embed.title = 'Liste des serveurs interdits.';
		
	} else {
		const guild = await getGuild(handler, args);
		
		if (guild && handler.forbiddenGuilds.has(guild.id ?? guild)) {
			embed.title = 'Serveur interdit !';
			embed.description = 'Ce serveur est bien interdit.';
			embed.fields = [
				{
					name:  'Raison',
					value: handler.forbiddenGuilds.get(guild.id ?? guild)
				}
			];
			embed.color = '#cf8080';
			
		} else {
			embed.title = 'Serveur autorisé.';
			embed.description = 'Ce serveur n\'est pas interdit (ou n\'existe pas).';
			embed.color = '#6b6b6b';
		}
	}
	
	await message.channel.send({embed: embed.build()});
});
