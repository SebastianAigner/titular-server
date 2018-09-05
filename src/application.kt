package io.sebi

import io.ktor.application.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.experimental.*
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
                val frame = try {
                    incoming.receive()
                }
                catch (e: Exception) {
                    println("Hit an exception on our way! " + e.localizedMessage)
                    allPlayers[uuid]?.let {
                        it.game?.let {
                            it.removePlayer(uuid)
                        }
                    }
                    if(allPlayers.containsKey(uuid)) {
                        allPlayers.remove(uuid)
                    }
                    allPlayers.forEach {
                        it.value.socket.sendString("NOPLAYERS ${allPlayers.count()}")
                    }
                    return@webSocket
                }
                if (frame is Frame.Text) {
                    val txt = frame.readText()
                    handleMessage(uuid, txt)
                }
            }
        }
    }
    PlayerNumberUpdater.run()
}

val allPlayers = mutableMapOf<UUID, Player>()
val games = mutableMapOf<String, Game>()

suspend fun updatePlayerNumbers() {
    allPlayers.forEach {
        it.value.socket.sendString("NOPLAYERS ${allPlayers.count()}")
    }
}

object PlayerNumberUpdater {
    fun run() {
        launch {
            while(true) {
                delay(5000)
                updatePlayerNumbers()
            }
        }
    }
}

suspend fun DefaultWebSocketServerSession.handleMessage(uuid: UUID, message: String) {
    println("[INCOMING] $message")
    updatePlayerNumbers()
    val player = allPlayers[uuid]
    val tokenized = message.split(" ")
    if(player == null) {
        if(tokenized[0].toLowerCase() == "name") {
                allPlayers[uuid] = Player(uuid, this, tokenized[1], null, 0)
                sendString("UUID $uuid")
                updatePlayerNumbers()
                val lobbies = games.filter { it.value.players.count() > 0 }.toList().sortedByDescending { it.second.players.count() }.map { Pair(it.first, it.second.players.count()) }
                lobbies.forEach {
                    sendString("LOBBY ${it.first} ${it.second}")
                }
        }
        return
    }
    else {
        when(tokenized[0].toLowerCase()) {
            "whoami" -> {
                sendString("PLAYER ${player.uuid} ${player.name} ${player.points}")
            }
            "game" -> {
                val game = games.getOrPut(tokenized[1]) {
                    Game(mutableSetOf())
                }
                sendString("Welcome to Game #${tokenized[1]}")
                sendString("JOINED ${tokenized[1]}")
                game.addPlayer(player)
            }
        }
        if(player.game == null) {
            sendString("not in game.")
            return
        }
        when(tokenized[0].toLowerCase()) {
            "lp" -> {
                val players = (player.game?.players) ?: emptyList<Player>()
                players.forEach {
                    sendString("PLAYER ${it.uuid} ${it.name} ${it.points}")
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

suspend fun DefaultWebSocketServerSession.sendString(str: String) {
    println("Sending: $str")
    send(Frame.Text(str))
}
