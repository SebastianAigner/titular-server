package io.sebi

import io.ktor.websocket.DefaultWebSocketServerSession
import java.util.*

class Player(val uuid: UUID, val socket: DefaultWebSocketServerSession, val name: String, var game: Game?, var points: Int) {
    override fun hashCode() = uuid.hashCode()
    override fun equals(other: Any?) = uuid == other
}