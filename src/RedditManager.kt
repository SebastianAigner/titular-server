package io.sebi

import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.models.SubredditSort
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import net.dean.jraw.ratelimit.LeakyBucketRateLimiter
import java.util.concurrent.TimeUnit

object RedditManager {
    val redditClient: RedditClient
    @Volatile
    var cached: Map<GameMode, List<String>> = mapOf()

    init {
        val ua = UserAgent("titular", "io.sebi.titular", "v0.1", "time_trade")
        val creds = Credentials.script(System.getenv("REDDIT_USERNAME"), System.getenv("REDDIT_PASSWORD"), System.getenv("REDDIT_CLIENT_ID"), System.getenv("REDDIT_CLIENT_SECRET"))
        val adapter = OkHttpNetworkAdapter(ua)
        redditClient = OAuthHelper.automatic(adapter, creds)
        redditClient.rateLimiter = LeakyBucketRateLimiter(60, 1, TimeUnit.SECONDS).apply { refill(60) }
    }

    fun getImages(g: GameMode): List<String> {
        cached[g]?.takeIf { it.count() > 10 }?.let { return it }
        val paginator = redditClient.subreddit(g.subreddit).posts().sorting(SubredditSort.TOP).timePeriod(g.timeLimit).limit(500).build()
        val images = mutableListOf<String>()
        for (thisPage in paginator) { //todo: inspection for paginator.iterator()? It is unnecessary after all
            if (images.count() > g.limit) {
                break
            }
            thisPage.children.forEach {
                val regex = ".*(i.redd.it|i.imgur.com).*(jpg|png)".toRegex()
                if (it.url matches regex && !it.isNsfw) {
                    images.add(it.url)
                }
            }
        }
        println("Delivering to game ${images.count()} images!")
        val listed = images.toList()
        cached += g to listed
        return listed
    }

    fun prefetch(g: GameMode) {
            getImages(g)
    }
}