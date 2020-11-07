const {CommandHandler} = require('advanced-command-handler');
require('dotenv').config();

CommandHandler.create({
	prefixes:    ['!'],
	owners:      ['386893236498857985', '216214448203890688'],
	commandsDir: 'src/commands',
	eventsDir:   'src/events',
});

CommandHandler.launch({
	token: process.env.TOKEN
});
