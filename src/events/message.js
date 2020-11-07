const {getThing} = require('advanced-command-handler');
module.exports = async (handler, message) => {
	if (message.author.bot || message.system) return;
	
	const args = message.content.slice(handler.prefixes[0].length).trim().split(/\s+/g);
	/**
	 * @type {Command}
	 */
	const command = await getThing('command', args.shift());
	
	if (command) {
		if (message.guild?.id === '465918902254436362' && handler.client.hasPermission(message, 'ADMINISTRATOR')) {
			command.run(handler, message, args);
		}
	}
};
