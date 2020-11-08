async function getGuild(handler, args) {
	let guild;
	await handler.client
		.fetchInvite(args[0])
		.then(invite => (guild = invite.guild.vanityURLCode === args[0] ? (args[0].match(/\d{17,19}/g) ? args[0] : null) : invite.guild))
		.catch(() => (guild = args[0].match(/\d{17,19}/g) ? args[0] : null));
	return guild;
}

module.exports = {
	getGuild,
};
