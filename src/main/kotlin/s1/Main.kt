package s1

import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

val PATTERN = Regex("""\d+""")

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val SIT_DELAY = 245
const val SATISFACTORY_DELAY = 500
const val PIPE_MEAN_DELAY = 1550
const val TIME_TO_THINK_BETTER_PIPE_EXISTS = 2 * PIPE_MEAN_DELAY
const val TIME_TO_WAIT_BETWEEN_SCANS = 3 * PIPE_MEAN_DELAY
const val WARMUP_TIME = 4 * PIPE_MEAN_DELAY

lateinit var apiUrl: String
lateinit var token: String

enum class Method { GET, PUT }

enum class Direction { Up, Down, Unknown }

fun main(args: Array<String>) {
    apiUrl = "http://${args[0]}/api/pipe"
    token = args[1]

    val robot = Robot()
    robot.collectInfoAboutPipes()
    robot.locallyFindBestPipe()
    while (true) {
        robot.bestPipe.collect()
        val currentBestPipe = robot.bestPipe
        robot.scanForPipesIfNeeded()
        robot.fixTendencyIfNeeded(currentBestPipe)
    }
}

class Robot {
    var gameTime: Int = 0
    lateinit var bestPipe: Pipe
    lateinit var lastTouchedPipe: Pipe

    private var lastScanTime: Int = WARMUP_TIME
    private var lastTimeFixTendency: Int = WARMUP_TIME

    private val pipes = listOf(
        Pipe(robot = this, number = 1),
        Pipe(robot = this, number = 2),
        Pipe(robot = this, number = 3)
    )

    fun locallyFindBestPipe() {
        var bestPipeValue = 0

        pipes.forEach {
            it.recalculateOutputValue()

            val peoplesOnPipe =
                if (it == lastTouchedPipe || it.peoplesOnPipe == 1) it.peoplesOnPipe
                else it.peoplesOnPipe - 1

            var localPipeValue = 0
            var localValue = it.value
            when (it.direction) {
                Direction.Down -> for (i in 0 until WARMUP_TIME step it.delay) {
                    for (j in 0 until peoplesOnPipe) {
                        localValue--
                        if (localValue < 1) localValue = PIPE_MAX_VALUE
                    }
                    localPipeValue += localValue
                }

                else -> for (i in 0 until WARMUP_TIME step it.delay) {
                    for (j in 0 until peoplesOnPipe) {
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
    }

    fun collectInfoAboutPipes() = pipes.shuffled().forEach {
        it.collect()
        if (it.delay <= SATISFACTORY_DELAY) return
    }

    fun scanForPipesIfNeeded() {
        if (bestPipe.delay >= SIT_DELAY + (gameTime / TIME_TO_THINK_BETTER_PIPE_EXISTS)
            && (gameTime - bestPipe.timeAllPeopleOnPipe) >= TIME_TO_THINK_BETTER_PIPE_EXISTS
            && (gameTime - lastScanTime) >= TIME_TO_WAIT_BETWEEN_SCANS
        ) {
            scan()
            lastScanTime = gameTime
            locallyFindBestPipe()
        }
    }

    private fun scan(exclude: Pipe = bestPipe) = getPipes(exclude = exclude).forEach {
        val firstPing = it.ping()
        val secondPing = it.ping()
        if (firstPing != secondPing) {
            it.collect()
            return
        }
        if (exclude.delay <= 400) return@forEach
        val thirdPing = it.ping()
        if (firstPing != thirdPing) {
            it.collect()
            return@forEach
        }
        if (exclude.delay <= 600) return@forEach
        val fourthPing = it.ping()
        if (firstPing != fourthPing) {
            it.collect()
            return@forEach
        }
    }

    private var pipeOrder = 0
    private fun getPipes(exclude: Pipe) = pipes.toMutableList().apply {
        removeAt(exclude.number - 1)
        if (pipeOrder++ % 2 == 0) this[0] = this[1].also { this[1] = this[0] }
    }

    fun fixTendencyIfNeeded(pipe: Pipe) {
        if (pipe == bestPipe && bestPipe.value % 2 == 1 && bestPipe.peoplesOnPipe % 2 == 0
            && (gameTime - lastTimeFixTendency) >= 10 * bestPipe.delay
        ) {
            fixTendency()
            lastTimeFixTendency = gameTime
        }
    }

    private fun fixTendency() {
        while (bestPipe.value % 2 == 1) {
            if (bestPipe.peoplesOnPipe % 2 == 0 && bestPipe.predictedNextValue in (1..5)) {
                val delay = (bestPipe.delay + 1) / 7
                Thread.sleep(delay.toLong())
                println("Postpone for $delay")
            }
            bestPipe.collect()
        }
    }
}

data class Pipe(
    val robot: Robot,
    val number: Int = 0,

    var value: Int = 0,
    var delay: Int = 3000,
    var direction: Direction = Direction.Unknown,

    var peoplesOnPipe: Int = 1,
    var predictedNextValue: Int = 0,
    var timeOfCollectedValue: Int = 0,
    var timeAllPeopleOnPipe: Int = WARMUP_TIME,
) {
    fun recalculateOutputValue() {
        val valueTickTimes = (robot.gameTime - timeOfCollectedValue) / delay
        if (valueTickTimes == 0) return

        timeOfCollectedValue += delay * valueTickTimes

        val peoplesOnPipe =
            if (this == robot.lastTouchedPipe || peoplesOnPipe == 1) peoplesOnPipe
            else peoplesOnPipe - 1

        var nextValue = value
        when (direction) {
            Direction.Down -> for (tick in 0 until valueTickTimes) {
                for (j in 0 until peoplesOnPipe) {
                    nextValue--
                    if (nextValue < 1) nextValue = PIPE_MAX_VALUE
                }
            }

            else -> for (tick in 0 until valueTickTimes) {
                for (j in 0 until peoplesOnPipe) {
                    nextValue++
                    if (nextValue > 10) nextValue = PIPE_MIN_VALUE
                }
            }
        }
        value = nextValue
    }

    fun collect() = sendRequest(
        method = Method.PUT,
        url = "$apiUrl/${this.number}",
    )

    private fun value() = sendRequest(
        method = Method.GET,
        url = "$apiUrl/${this.number}/value",
    )

    // todo: mean response delay difference from real pipe delay is 5.5 ms
    private fun sendRequest(
        method: Method,
        url: String,
    ) {
        val startTime = currentTimeMillis()
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method.name
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true

            val newValue: Int
            when (responseCode) {
                200 -> inputStream.bufferedReader().use {
                    newValue = PATTERN.find(it.readText())?.value?.toInt() ?: 0
                }

                else -> return
            }

            if (value != 0) {
                if (abs(newValue - value) <= 4)
                    direction = if (newValue > value) Direction.Up else Direction.Down

                peoplesOnPipe = when (direction) {
                    Direction.Down -> when (val peoplesOnPipe = value - newValue) {
                        -9 -> 1
                        -8 -> 2
                        -7 -> 3
                        -6 -> 4
                        else -> peoplesOnPipe
                    }

                    else -> when (val peoplesOnPipe = newValue - value) {
                        -9 -> 1
                        -8 -> 2
                        -7 -> 3
                        -6 -> 4
                        else -> peoplesOnPipe
                    }
                }

                if (peoplesOnPipe in (1..4)) {
                    var nextValue = newValue
                    when (direction) {
                        Direction.Down -> for (j in 0 until peoplesOnPipe) {
                            nextValue--
                            if (nextValue < 1) nextValue = PIPE_MAX_VALUE
                        }

                        else -> for (j in 0 until peoplesOnPipe) {
                            nextValue++
                            if (nextValue > 10) nextValue = PIPE_MIN_VALUE
                        }
                    }
                    predictedNextValue = nextValue
                }
                if (peoplesOnPipe > 3) timeAllPeopleOnPipe = robot.gameTime
            }

            value = newValue
            robot.lastTouchedPipe = this@Pipe

            val responseTime = (currentTimeMillis() - startTime).toInt()
            robot.gameTime += responseTime

            if (method == Method.PUT) {
                delay = responseTime
                timeOfCollectedValue = robot.gameTime
                println("Time: ${robot.gameTime}. Pipe: $number, peoples: $peoplesOnPipe. Collected $value in $delay")
            } else println("Time: ${robot.gameTime}. Pipe: $number, peoples: $peoplesOnPipe. Value $value")
        }
    }

    fun ping(): Int {
        value()
        return value
    }
}
