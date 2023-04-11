import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val SIT_DELAY = 200
const val CHECK_DELAY = 450
const val BAD_DELAY = 900
const val PIPE_MEAN_DELAY = 1550
const val TIME_TO_DETERMINE_PIPE_VALUE = 3 * PIPE_MEAN_DELAY
const val TIME_TO_THINK_BETTER_PIPE_EXISTS = 3 * PIPE_MEAN_DELAY

val PATTERN = Regex("""\d+""")

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0,
    var bestPipe: Pipe = Pipe()
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

enum class Modifiers(val cost: Int) {
    reverse(40), double(50), slow(40), shuffle(10), min(10)
}

enum class Method { GET, PUT, POST }

fun main(args: Array<String>) {
    val host = args[0]
    val apiUrl = "http://$host/api"
    val token = args[1]

    val robot = Robot()
    val observedPipes = listOf(Pipe(1), Pipe(2), Pipe(3))

    fun Pipe.sendRequest(
        method: Method,
        url: String,
        token: String,
        type: Modifiers? = null
    ) {
        val t1 = currentTimeMillis()
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method.name
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true

            if (type != null) {
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write("""{"type":"${type.name}"}""") }
            }

            if (method == Method.POST) when (responseCode) {
                200 -> {
                    robot.resources -= type!!.cost
                    println("apply modifier $type to pipe with url: $url")
                }

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

                if (value != 0) {
                    if (newValue - value <= 4)
                        direction = if (newValue > value) Direction.Up else Direction.Down

                    peoplesOnPipe = when (direction) {
                        Direction.Down -> {
                            when (val peoplesOnPipe = value - newValue) {
                                -9 -> 1
                                -8 -> 2
                                -7 -> 3
                                -6 -> 4
                                else -> peoplesOnPipe
                            }
                        }

                        else -> {
                            when (val peoplesOnPipe = newValue - value) {
                                -9 -> 1
                                -8 -> 2
                                -7 -> 3
                                -6 -> 4
                                else -> peoplesOnPipe
                            }
                        }
                    }

                    // todo: most -> all, peoplesOnPipe == 4
                    if (peoplesOnPipe > 2) timeMostPeopleOnPipe = robot.gameTime
                }
                value = newValue

                if (method == Method.PUT) {
                    robot.resources += value

                    delay = (currentTimeMillis() - t1).toInt()
                    robot.gameTime += delay
                    timeOfCollectedValue = robot.gameTime
                }

                println(
                    "Time: ${robot.gameTime}. Pipe: $number, peoples: $peoplesOnPipe. " +
                            "Collected $value in $delay (total: ${robot.resources})"
                )
            }
        }
    }

    fun Pipe.collect() = sendRequest(
        method = Method.PUT,
        url = "$apiUrl/pipe/${this.number}",
        token = token
    )

    fun Pipe.value() = sendRequest(
        method = Method.GET,
        url = "$apiUrl/pipe/${this.number}/value",
        token = token
    )

    fun Pipe.modifier(type: Modifiers) = sendRequest(
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
            delay < SIT_DELAY || (TIME_TO_DETERMINE_PIPE_VALUE / delay) * value > 75 -> collect()
            else -> value()
        }
    }

    fun getObservedPipes(exclude: Pipe) = observedPipes.toMutableList().apply {
        removeAt(exclude.number - 1)
        shuffle()
    }

    fun collectInfoAboutPipes() = observedPipes.shuffled().forEach {
        it.collectAndSkipOrValueOrCollect()
    }

    // todo: not lose points on it, stay tune and just collect and observe by findPipeWithPeoples()
    // todo: deprecate count bad delay, we always on best, if besty is bad, all other also bad
    fun ifAllPipesBadShuffleBaddestPipe() {
        var badDelay = 0
        observedPipes.forEach { if (it.delay >= BAD_DELAY) badDelay++ }
        if (badDelay == 3) with(observedPipes.maxBy { it.delay }) {
            modifier(type = Modifiers.shuffle)
            collectAndSkipOrValueOrCollect()
        }
    }

    // todo: In tests, this function take 1 MS, maybe try to less?
    fun findBestPipe() {
        var bestPipeValue = 0

        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            when (it.direction) {
                Direction.Down -> for (i in 0 until TIME_TO_DETERMINE_PIPE_VALUE step it.delay) {
                    for (j in 0 until it.peoplesOnPipe - 1) {
                        localValue--
                        if (localValue < 1) localValue = PIPE_MAX_VALUE
                    }
                    localPipeValue += localValue
                }

                else -> for (i in 0 until TIME_TO_DETERMINE_PIPE_VALUE step it.delay) {
                    for (j in 0 until it.peoplesOnPipe - 1) {
                        localValue++
                        if (localValue > 10) localValue = PIPE_MIN_VALUE
                    }
                    localPipeValue += localValue
                }
            }
            if (localPipeValue > bestPipeValue) {
                bestPipeValue = localPipeValue
                robot.bestPipe = it
            }
        }
    }

    fun Pipe.pingWithValue(): Int {
        value()
        return value
    }

    fun scanForPipes(exclude: Pipe) = getObservedPipes(exclude = exclude).forEach {
        val firstPingValue = it.pingWithValue()
        for (i in 0 until BAD_DELAY step exclude.delay) exclude.collect()
        val secondPingValue = it.pingWithValue()
        if (firstPingValue != secondPingValue) it.collect()
        if (it.delay < exclude.delay) return
    }

    collectInfoAboutPipes()
    findBestPipe()

    while (true) {
        robot.bestPipe.collect()

        if (robot.gameTime >= 10000 &&
            robot.bestPipe.delay >= SIT_DELAY + (robot.gameTime / PIPE_MEAN_DELAY) && // more stable to end of game
            (robot.gameTime - robot.bestPipe.timeMostPeopleOnPipe) >= TIME_TO_THINK_BETTER_PIPE_EXISTS
        ) scanForPipes(exclude = robot.bestPipe)

        findBestPipe()

        if (robot.bestPipe.delay > BAD_DELAY) ifAllPipesBadShuffleBaddestPipe()
    }
}
