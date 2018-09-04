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
import java.time.*
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.DevelopmentEngine.main(args)

object UrlManager{
    var urls: List<String> = emptyList()
}

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val client = HttpClient(Apache) {
        engine {
            this.customizeClient {
                this.setUserAgent("Titular Game")
            }
        }
    }
    UrlManager.urls = runBlocking { withContext(DefaultDispatcher) {
        println("Getting Images...")
            val jsonContent = client.get<String>("https://www.reddit.com/r/disneyvacation/top/.json?sort=top&t=all&limit=500")
        println("Got $jsonContent")
            val redditRegex = Regex("(https:\\/\\/i.\\w{4,6}.\\w{2,3}\\/\\w+.jpg)")
            val urls = redditRegex.findAll(jsonContent).map { it.groupValues.first() }
            urls
    } }.toList()


    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        webSocket("/myws/echo") {
            val uuid = UUID.randomUUID()
            send(Frame.Text("UUID $uuid"))
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

data class Player(val uuid: UUID, val socket: DefaultWebSocketServerSession, val name: String, var game: Game?, var points: Int)
class Game(val players: MutableSet<Player>) {
    suspend fun broadcast(str: String) {
        players.forEach {
            it.socket.sendString(str)
        }
    }



    suspend fun addPlayer(p: Player) {
        players.add(p)
        p.game = this
        broadcast("PLAYER ${p.uuid} ${p.name} ${p.points}")
        broadcast("IMAGE $image")
        players.forEach {
            p.socket.sendString("PLAYER ${it.uuid} ${it.name} ${it.points}")
        }
    }

    var image: String = "https://via.placeholder.com/600x400"
    val imagesPlayedAlready = mutableListOf<String>()

    val guessTime = System.getenv("GUESS_TIME")?.toInt() ?: 5
    val voteTime = System.getenv("VOTE_TIME")?.toInt() ?: 5
    suspend fun startRound() {
        launch {
            do {
                image = UrlManager.urls.shuffled().first()
            } while(imagesPlayedAlready.contains(image))
            //todo: abort condition if all images have been played through
            imagesPlayedAlready.add(image)
            broadcast("STARTROUND")
            broadcast("IMAGE $image")
            guessAllowed = true
            broadcast("TIME $guessTime")
            var timeRemaining = guessTime
            while(timeRemaining > 0 && players.count() != guesses.count()) {
                println("players: ${players.count()}, guesses: ${guesses.count()}")
                timeRemaining--
                delay(1000)
            }
            guessAllowed = false
            broadcast("VOTENOW")
            broadcast("TIME $voteTime")
            voteAllowed = true
            timeRemaining = voteTime
            while(timeRemaining > 0 && players.count() != votes.count()) {
                timeRemaining--
                delay(1000)
            }
            voteAllowed = false
            broadcast("VOTEEND")
            //evaluate votes
            votes.forEach {
                broadcast("POINT ${it.key.uuid} ${it.value}")
                it.key.points += it.value
            }
            guesses = mutableMapOf()
            votes = mutableMapOf()
        }
    }

    var guesses = mutableMapOf<Player, String>()
    var votes = mutableMapOf<Player, Int>()
    var guessAllowed = false
    var voteAllowed = false

    suspend fun handleGuess(p: Player, s: String) {
        if(!guessAllowed) return
        guesses[p] = s
        broadcast("GUESS ${p.uuid} $s")
    }

    fun getPlayerFromUUID(uuid: UUID) = players.first { it.uuid.equals(uuid) }

    fun vote(uuid: UUID) {
        if(!voteAllowed) return
        val pl = getPlayerFromUUID(uuid)
        val oldVal = votes.getOrDefault(pl, 0)
        votes[pl] = oldVal + 1
        println("Voting for player $uuid, now has ${oldVal+1} points")
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
                sendString("Hello, ${tokenized[1]}.")
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
                game.addPlayer(player)
                sendString("Welcome to Game #${tokenized[1]}")
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
    try {
        send(Frame.Text(str))
    } catch (t: Throwable) {
        println(t.localizedMessage)
    }

}