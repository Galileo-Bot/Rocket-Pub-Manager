package storage

import bot
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.serialization.Serializable

@Serializable
class User(val userID: Snowflake) {
	val sanctions: MutableList<Sanction> = ArrayList()
	
	suspend fun getUser(): User? {
		return bot.kord.getUser(userID, EntitySupplyStrategy.cacheWithRestFallback)
	}
}
/*
object UserSerializer : Serializer<storage.User> {
	override fun write(data: storage.User): Slice<Byte> {
		return Slice.ofBytesJava(1000).addUnsignedLong(data.userID.value, ByteOps.Java()).add()
	}
	
	override fun read(slice: Slice<Byte>): storage.User {
		TODO("Not yet implemented")
	}
}*/
