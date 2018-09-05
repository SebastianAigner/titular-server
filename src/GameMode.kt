package io.sebi

import net.dean.jraw.models.TimePeriod

enum class GameMode(val subreddit: String, var timeLimit: TimePeriod, var limit: Int) {
    TOP_ALL_TIME("disneyVacation", TimePeriod.ALL, 800),
    TOP_THIS_MONTH("disneyVacation", TimePeriod.MONTH, 300),
    TOP_THIS_WEEK("disneyVacation", TimePeriod.WEEK, 100),
    HOT("disneyVacation", TimePeriod.DAY,50),
    HMMM("hmmm", TimePeriod.ALL, 500),
    NOTDISNEY_ALL_TIME("notdisneyvacation", TimePeriod.ALL, 500)
}
