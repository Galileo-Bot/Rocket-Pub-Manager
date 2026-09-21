package utils

private val INVITE_LINK_REGEX = Regex(DISCORD_INVITE_LINK_REGEX)

fun findInviteLink(text: String) = INVITE_LINK_REGEX.find(text)?.value
fun findInviteCode(text: String) = INVITE_LINK_REGEX.find(text)?.groupValues?.get(1)
