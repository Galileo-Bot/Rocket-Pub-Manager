const {
	CommandHandler,
	Logger
} = require('advanced-command-handler');
const Enmap = require('enmap');
require('dotenv').config();

const forbiddenGuilds = new Enmap({
	autoFetch: true,
	fetchAll:  true,
	dataDir:   'assets/db',
	name:      'forbiddenGuilds'
});
const sanctions = new Enmap({
	autoFetch: true,
	fetchAll:  true,
	dataDir:   'assets/db',
	name:      'sanctions'
});

forbiddenGuilds.defer
               .then(() => {
	               sanctions.defer.then(() => {
		               CommandHandler.forbiddenGuilds = forbiddenGuilds;
		               Logger.info('DB prepared.', 'DBManager');
		
		               CommandHandler.create({
			               prefixes:    ['!'],
			               owners:      ['386893236498857985', '216214448203890688'],
			               commandsDir: 'src/commands',
			               eventsDir:   'src/events'
		               });
		
		               CommandHandler.launch({
			               token: process.env.TOKEN
		               });
	               }).catch(err => Logger.error(err.stack));
               })
               .catch(err => Logger.error(err.stack));
