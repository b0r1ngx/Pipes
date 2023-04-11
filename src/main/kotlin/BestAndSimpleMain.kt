import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val SIT_DELAY = 230
const val CHECK_DELAY = 450
const val BAD_DELAY = 900
const val FORMULA_TIME = 5000
const val TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE = 4000

val PATTERN = Regex("""\d+""")

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0
)

enum class Direction { Up, Down, Unknown }

data class Pipe(
    val number: Int = 0,

    var value: Int = 0,
    var delay: Int = 3000,
    var direction: Direction = Direction.Unknown,

    var peoplesOnPipe: Int = 0,
    var timeMostPeopleOnPipe: Int = 10000,
    var timeOfCollectedValue: Int = 0
)

enum class Modifiers(cost: Int) {
    reverse(40), double(50), slow(40), shuffle(10), min(10)
}

enum class Method { GET, PUT, POST }

fun main(args: Array<String>) {
    val host = args[0]
    val apiUrl = "http://$host/api"
    val token = args[1]

    val robot = Robot()
    val observedPipes = listOf(
        Pipe(1), Pipe(2), Pipe(3)
    )

    fun sendRequest(
        pipe: Pipe,
        method: Method,
        url: String,
        token: String,
        type: String? = null
    ) {
        val t1 = currentTimeMillis()
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method.name
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true

            if (type != null) {
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write("""{"type":"$type"}""") }
            }

            if (method == Method.POST)
                when (responseCode) {
                    200 -> println("apply modifier $type to pipe with url: $url")
                    422 -> println("failed to apply modifier")
                    else -> println("$requestMethod: unexpected status code $responseCode")
                }
            else {
                val newValue: Int
                when (responseCode) {
                    200 -> inputStream.bufferedReader().use {
                        newValue = PATTERN.find(it.readText())?.value?.toInt() ?: 0
                    }

                    else -> {
                        println("$requestMethod: unexpected status code $responseCode")
                        return
                    }
                }

                if (pipe.value != 0) {
                    if (newValue - pipe.value <= 4)
                        pipe.direction = if (newValue > pipe.value) Direction.Up else Direction.Down

                    pipe.peoplesOnPipe = when (pipe.direction) {
                        Direction.Down -> {
                            when (val peoplesOnPipe = pipe.value - newValue) {
                                -9 -> 1
                                -8 -> 2
                                -7 -> 3
                                -6 -> 4
                                else -> peoplesOnPipe
                            }
                        }

                        else -> {
                            when (val peoplesOnPipe = newValue - pipe.value) {
                                -9 -> 1
                                -8 -> 2
                                -7 -> 3
                                -6 -> 4
                                else -> peoplesOnPipe
                            }
                        }
                    }
                    if (pipe.peoplesOnPipe > 2) pipe.timeMostPeopleOnPipe = robot.gameTime
                }
                pipe.value = newValue

                if (method == Method.PUT) {
                    robot.resources += pipe.value

                    pipe.delay = (currentTimeMillis() - t1).toInt()
                    robot.gameTime += pipe.delay
                    pipe.timeOfCollectedValue = robot.gameTime
                }

                println(
                    "Time: ${robot.gameTime}. Pipe: ${pipe.number}, peoples: ${pipe.peoplesOnPipe}. " +
                            "Collected ${pipe.value} in ${pipe.delay} (total: ${robot.resources})"
                )
            }
        }
    }

    fun Pipe.collect() = sendRequest(
        pipe = this,
        method = Method.PUT,
        url = "$apiUrl/pipe/${this.number}",
        token = token
    )

    fun Pipe.value() = sendRequest(
        pipe = this,
        method = Method.GET,
        url = "$apiUrl/pipe/${this.number}/value",
        token = token
    )

    fun Pipe.modifier(type: String) = sendRequest(
        pipe = this,
        method = Method.POST,
        url = "$apiUrl/pipe/${this.number}/modifier",
        token = token,
        type = type
    )

    fun Pipe.recalculateOutputValue() {
        val valueTickTimes = (robot.gameTime - timeOfCollectedValue) / delay
        if (valueTickTimes == 0) return

        timeOfCollectedValue += delay * valueTickTimes
        var nextValue = value

        when (direction) {
            Direction.Down -> for (tick in 0 until valueTickTimes) {
                for (j in 0 until peoplesOnPipe - 1) {
                    nextValue--
                    if (nextValue < 1) nextValue = PIPE_MAX_VALUE
                }
            }

            else -> for (tick in 0 until valueTickTimes) {
                for (j in 0 until peoplesOnPipe - 1) {
                    nextValue++
                    if (nextValue > 10) nextValue = PIPE_MIN_VALUE
                }
            }
        }
        value = nextValue
    }

    fun Pipe.collectAndSkipOrValueOrCollect() {
        collect()
        if (delay > CHECK_DELAY) return

        recalculateOutputValue()
        when {
            delay < SIT_DELAY || (FORMULA_TIME / delay) * value > 75 -> collect()
            else -> value()
        }
    }

    fun getShuffledObservedPipes(exclude: Pipe) =
        observedPipes.toMutableList().apply {
            removeAt(exclude.number - 1)
            shuffle()
        }

    fun collectInfoAboutPipes(exclude: Pipe? = null) = if (exclude != null) {
        var badDelay = 0
        observedPipes.forEach { if (it.delay >= BAD_DELAY) badDelay++ }
        if (badDelay == 3) with(observedPipes.maxBy { it.delay }) {
            modifier(type = Modifiers.shuffle.name)
            collectAndSkipOrValueOrCollect()
            return
        }

        getShuffledObservedPipes(exclude).forEach {
            it.collectAndSkipOrValueOrCollect()
        }
    } else observedPipes.shuffled().forEach {
        it.collectAndSkipOrValueOrCollect()
    }


    // TODO: In tests, this function take 1 MS, maybe try to less?
    fun findBestPipe(): Pipe {
        var bestPipe = Pipe()
        var bestPipeValue = 0

        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            when (it.direction) {
                Direction.Down -> for (i in 0 until FORMULA_TIME step it.delay) {
                    for (j in 0 until it.peoplesOnPipe - 1) {
                        localValue--
                        if (localValue < 1) localValue = PIPE_MAX_VALUE
                    }
                    localPipeValue += localValue
                }

                else -> for (i in 0 until FORMULA_TIME step it.delay) {
                    for (j in 0 until it.peoplesOnPipe - 1) {
                        localValue++
                        if (localValue > 10) localValue = PIPE_MIN_VALUE
                    }
                    localPipeValue += localValue
                }
            }
            if (localPipeValue > bestPipeValue) {
                bestPipeValue = localPipeValue
                bestPipe = it
            }
        }
        return bestPipe
    }

    fun checkPeopleOnOtherPipes(exclude: Pipe) =
        getShuffledObservedPipes(exclude = exclude).forEach {
            it.value()
            val firstPingValue = it.value

            for (i in 0 until BAD_DELAY step exclude.delay) // + 1 ; also maybe use findBestPipeLocally() between collect()
                exclude.collect()

            it.value()
            val secondPingValue = it.value

            if (firstPingValue != secondPingValue)
                it.collect()

            if (it.delay < exclude.delay) return
        }

    collectInfoAboutPipes()
    var bestPipe = findBestPipe()

    while (true) {
        with(bestPipe) {
            collect()

            if (delay > BAD_DELAY) collectInfoAboutPipes(exclude = this)
            else if ((robot.gameTime >= 10000 && delay > SIT_DELAY && (robot.gameTime - timeMostPeopleOnPipe) >= TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE)
                || (robot.gameTime >= 30000 && delay > SIT_DELAY - 30 && (robot.gameTime - timeMostPeopleOnPipe) >= TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE * 1.5)
            ) checkPeopleOnOtherPipes(exclude = this)

            bestPipe = findBestPipe()
        }
    }
}
