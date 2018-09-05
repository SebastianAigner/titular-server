package io.sebi

import io.ktor.application.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ClosedSendChannelException
import java.time.*
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.DevelopmentEngine.main(args)



object HttpClientManager {
    val client = HttpClient(Apache) {
        engine {
            this.customizeClient {
                this.setUserAgent("Titular Game")
            }
        }
    }
}

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    println("Setting up routing...")
    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        webSocket("/myws/echo") {
            val uuid = UUID.randomUUID()
            while (true) {
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    val txt = frame.readText()
                    handleMessage(uuid, txt)
                }
            }
        }
    }
}

val allPlayers = mutableMapOf<UUID, Player>()
val games = mutableMapOf<String, Game>()

suspend fun DefaultWebSocketServerSession.handleMessage(uuid: UUID, message: String) {
    println("[INCOMING] $message")
    val player = allPlayers[uuid]
    val tokenized = message.split(" ")
    if(player == null) {
        if(tokenized[0].toLowerCase() == "name") {
                allPlayers[uuid] = Player(uuid, this, tokenized[1], null, 0)
                sendString("UUID $uuid", uuid)
        }
        return
    }
    else {
        when(tokenized[0].toLowerCase()) {
            "whoami" -> {
                sendString("PLAYER ${player.uuid} ${player.name} ${player.points}", player.uuid)
            }
            "game" -> {
                val game = games.getOrPut(tokenized[1]) {
                    Game(mutableSetOf())
                }
                game.addPlayer(player)
                sendString("Welcome to Game #${tokenized[1]}", player.uuid)
            }
        }
        if(player.game == null) {
            sendString("not in game.", player.uuid)
            return
        }
        when(tokenized[0].toLowerCase()) {
            "lp" -> {
                val players = (player.game?.players) ?: emptyList<Player>()
                players.forEach {
                    sendString("PLAYER ${it.uuid} ${it.name} ${it.points}", player.uuid)
                }
            }
            "gamemode" -> {
                player.game!!.changeGameMode(tokenized[1])
            }
            "guess" -> {
                player.game!!.handleGuess(player, message.drop(6))

            }
            "start" -> {
                player.game?.startRound()
            }
            "chat" -> {
                player.game?.broadcast(message)
            }
            "vote" -> {
                println(tokenized[1])
                player.game?.vote(UUID.fromString(tokenized[1]))
            }
        }
    }
}

suspend fun DefaultWebSocketServerSession.sendString(str: String, uuid: UUID) {
    try {
        send(Frame.Text(str))
    } catch (t: Throwable) {
        if(t is ClosedSendChannelException) {
            allPlayers[uuid]?.game?.removePlayer(uuid)
        }
        println("Socket send failed: " + t.localizedMessage + "$t")
    }
}
