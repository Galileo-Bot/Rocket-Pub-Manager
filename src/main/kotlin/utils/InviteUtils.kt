package utils

import dev.kord.core.Kord

suspend fun getInvite(kord: Kord, text: String) = kord.getInvite(text, true)

fun findInviteLink(text: String) = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.value
fun findInviteCode(text: String) = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.groupValues?.get(1)
