package io.sebi

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import java.util.*

enum class GameMode(val url: String) {
    TOP_ALL_TIME("https://www.reddit.com/r/disneyvacation/top/.json?sort=top&t=all&limit=500"),
    TOP_THIS_MONTH("https://www.reddit.com/r/disneyvacation/top/.json?sort=top&t=month&limit=500"),
    TOP_THIS_WEEK("https://www.reddit.com/r/disneyvacation/top/.json?sort=top&t=week&limit=500"),
    HOT("https://www.reddit.com/r/disneyvacation/.json?limit=500"),
    HMMM("https://www.reddit.com/r/hmmm/top/.json?sort=top&t=all&limit=500"),
    NOTDISNEY_ALL_TIME("https://www.reddit.com/r/notdisneyvacation/top/.json?sort=top&t=all&limit=500")
}

class Game(var players: Set<Player>, var gamemode: GameMode = GameMode.TOP_ALL_TIME) {
    suspend fun broadcast(str: String) {
        players.forEach {
            it.socket.sendString(str)
        }
    }

    suspend fun addPlayer(p: Player) {
        players += p
        p.game = this

            broadcast("PLAYER ${p.uuid} ${p.name} ${p.points}")

        println("Adding Player, inround:$inRound")
        if(inRound) {
            println("### MESSAGING ${p.name}!")

                p.socket.sendString("STARTROUND")
                p.socket.sendString("IMAGE $image")
                p.socket.sendString("TIME $timeRemaining")
                if(voteAllowed) {
                    p.socket.sendString("VOTENOW") //todo: provide function like p.sendViaSocket() to get rid of all the p.uuid stuff
                }

        }


            players.forEach {
                p.socket.sendString("PLAYER ${it.uuid} ${it.name} ${it.points}")
            }


    }

    var image: String = "https://via.placeholder.com/600x400"
    val imagesPlayedAlready = mutableListOf<String>()

    val guessTime = System.getenv("GUESS_TIME")?.toInt() ?: 5
    val voteTime = System.getenv("VOTE_TIME")?.toInt() ?: 5
    var timeRemaining = 0
    var inRound = false

    suspend fun startRound() {
        if(inRound) return
        launch {
            inRound = true
            broadcast("NOINTERACT")
            println("Launching round with corpus of ${UrlManager.getUrls(gamemode).size} images")
            do {
                image = UrlManager.getUrls(gamemode).shuffled().first()
            } while(imagesPlayedAlready.contains(image))
            //todo: abort condition if all images have been played through
            imagesPlayedAlready.add(image)
            broadcast("INTERACT")
            broadcast("STARTROUND")
            broadcast("IMAGE $image")
            guessAllowed = true
            broadcast("TIME $guessTime")
            /*var*/ timeRemaining = guessTime
            while(timeRemaining > 0 && players.count() != guesses.count()) {
                println("players: ${players.count()}, guesses: ${guesses.count()}")
                timeRemaining--
                delay(1000)
            }
            delay(300) // so that the automated system can still put in a suggestion
            guessAllowed = false
            broadcast("VOTENOW")
            broadcast("TIME $voteTime")
            voteAllowed = true
            timeRemaining = voteTime
            while(timeRemaining > 0 && players.count() != votes.values.sum()) {
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
            inRound = false
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

    suspend fun vote(uuid: UUID) {
        if(!voteAllowed) return
        val pl = getPlayerFromUUID(uuid)
        val oldVal = votes.getOrDefault(pl, 0)
        votes[pl] = oldVal + 1
        println("Voting for player $uuid, now has ${oldVal+1} points")
        broadcast("VOTE_INDICATOR")
    }

    suspend fun removePlayer(uuid: UUID) {
        println("Attention! Removing player $uuid")
        println("previous players ${players.size}:")
        players.forEach {
            println(it)
        }
        players = players.filterNot { it.uuid == uuid }.toSet()
        println("now players ${players.size}")
        broadcast("PLAYER_LEAVE $uuid")
        checkIfEmpty()
    }

    private fun checkIfEmpty() {
        if(players.isEmpty()) {
            println("Removing game from rotation. Previously: ${games.count()} games.")
            games.remove(games.filter { it.value == this }.map { it.key }.first())
            println("Now: ${games.count()} games.")
        }
    }

    suspend fun changeGameMode(newGameMode: String) {
        if(inRound) return
        gamemode = try {
            GameMode.valueOf(newGameMode)
        } catch(i: Throwable) {
            GameMode.TOP_ALL_TIME
        }
        UrlManager.prefetch(gamemode)
        broadcast("GAMEMODE $gamemode")
    }
}