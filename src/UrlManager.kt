package io.sebi

import io.ktor.client.request.get
import kotlinx.coroutines.experimental.*

object UrlManager{
    var knownUrls: Map<GameMode, List<String>> = mapOf()
    fun prefetch(g: GameMode) {
        println("prefetching $g")
        launch {
            getUrls(g)
        }
    }
    fun getUrls(g: GameMode): List<String> {
        val knownUrl = knownUrls[g]
        if(knownUrl != null) {
            return knownUrl
        }
        else {
            val urls = runBlocking { withContext(DefaultDispatcher) {
                println("Getting Images...")
                val jsonContent = HttpClientManager.client.get<String>(g.url)
                println("Got $jsonContent")
                val redditRegex = Regex("(https:\\/\\/i.\\w{4,6}.\\w{2,3}\\/\\w+.jpg)")
                val urls = redditRegex.findAll(jsonContent).map { it.groupValues.first() }
                urls
            } }.toList()
            if(!urls.isEmpty()) {
                knownUrls += g to urls
            }
            else {
                return listOf(fallback)
            }
            return urls
        }
    }

    val allUrls: Map<GameMode, List<String>> by lazy { //todo: this isn't lazy enough for my taste yet, since it downloads everything in one bulk. even lazier: only load when a certain gamemode is being played.
        GameMode.values().map { gameMode ->
            gameMode to runBlocking { withContext(DefaultDispatcher) {
                println("Getting Images...")
                val jsonContent = HttpClientManager.client.get<String>(gameMode.url)
                println("Got $jsonContent")
                val redditRegex = Regex("(https:\\/\\/i.\\w{4,6}.\\w{2,3}\\/\\w+.jpg)")
                val urls = redditRegex.findAll(jsonContent).map { it.groupValues.first() }
                urls
            } }.toList()
        }.toMap()
    }
    val fallback = "https://via.placeholder.com/600x400"
}