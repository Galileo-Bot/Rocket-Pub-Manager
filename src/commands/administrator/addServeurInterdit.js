const {Command} = require('advanced-command-handler');
module.exports = new Command({
	name: 'addServeurInterdit',
	aliases: ['asi', 'addSI'],
	usage: 'addServeurInterdit <id>',
	userPermissions: ['ADMINISTRATOR'],
	guildOnly: true,
	description: 'Ajoute un serveur aux serveurs interdits.'
}, async(handler, message, args) => {

})
