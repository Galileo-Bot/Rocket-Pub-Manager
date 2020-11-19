const {isStaff} = require('../utils/Utils.js');
module.exports = async (handler, member) => {
	if (!isStaff(member)) {
		handler.client.guilds.cache.get(process.env.ROCKET_PUB_ID).channels.cache
		       .filter(c => c.isText() && c.parent?.id === process.env.ADS_CATEGORY_ID)
		       .forEach(async (c) => (await c.messages.fetch())
			       .filter((m) => m.author.id === member.user.id)
			       .forEach(m => m.delete({
				       timestamp: 5000,
				       reason: 'Auto delete.'
			       })));
	}
};
