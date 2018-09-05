package io.sebi

import kotlinx.coroutines.experimental.launch
import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.models.Listing
import net.dean.jraw.models.Submission
import net.dean.jraw.models.SubredditSort
import net.dean.jraw.models.TimePeriod
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import net.dean.jraw.ratelimit.LeakyBucketRateLimiter
import net.dean.jraw.ratelimit.NoopRateLimiter
import java.util.concurrent.TimeUnit

object RedditManager {
    val redditClient: RedditClient
    var cached: Map<GameMode, List<String>> = mapOf()

    init {
        val ua = UserAgent("titular", "io.sebi.titular", "v0.1", "time_trade")
        val creds = Credentials.script(System.getenv("REDDIT_USERNAME"), System.getenv("REDDIT_PASSWORD"), System.getenv("REDDIT_CLIENT_ID"), System.getenv("REDDIT_CLIENT_SECRET"))
        val adapter = OkHttpNetworkAdapter(ua)
        redditClient = OAuthHelper.automatic(adapter, creds)
    }

    fun getImages(g: GameMode): List<String> {
        if(cached.containsKey(g) && (cached[g]?.count() ?: 0) > 10) {
            return cached[g]!!
        }
        val paginator = redditClient.subreddit("disneyvacation").posts().sorting(SubredditSort.TOP).timePeriod(TimePeriod.ALL).limit(500).build()
        val images = mutableListOf<String>()
        val iter = paginator.iterator()
        while(iter.hasNext() && images.count() < g.limit) {
            val thisPage = iter.next()
            thisPage.children.forEach {
                if(it.url.contains("i.redd.it") || it.url.contains("i.imgur.com")) {
                    if(it.url.contains(".jpg") || it.url.contains(".png")) {
                        if(!it.isNsfw) {
                            images.add(it.url)
                        }
                    }
                }
            }
        }
        println("Delivering to game ${images.count()} images!")
        cached += g to images.toList()
        return images.toList()
    }

    fun prefetch(g: GameMode) {
        launch {
            getImages(g)
        }
    }
}