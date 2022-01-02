package utils

import dev.kord.core.Kord
import dev.kord.core.entity.Invite

suspend fun getInvite(kord: Kord, text: String): Invite? = kord.getInvite(text, true)

fun findInviteLink(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.value
fun findInviteCode(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.groupValues?.get(1)
