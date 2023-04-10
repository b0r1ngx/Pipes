package s1

import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val MIN_PING_TO_SIT = 450
const val TIME_BETWEEN_REQUESTS = 60000
const val TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE = 12000
const val TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING = 6000
const val TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE = 3 * 1550

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

            val isPost = method == Method.POST

            var value = 0
            when (responseCode) {
                200 -> inputStream.bufferedReader().use {
                    if (!isPost) value = PATTERN.find(it.readText())?.value?.toInt() ?: 0
                    else println("Applied modifier $type to pipe with url: $url")
                }

                422 -> println("Failed to apply modifier")
                else -> println("Unexpected status code $responseCode")
            }

            if (!isPost) {
                if (pipe.value != 0) pipe.direction = if (value > pipe.value) Direction.Up else Direction.Down
                pipe.value = value

                if (method == Method.PUT) {
                    robot.resources += value

                    pipe.delay = (currentTimeMillis() - t1).toInt()
                    robot.gameTime += pipe.delay
                    pipe.timeOfCollectedValue = robot.gameTime
                }

                println("Time: ${robot.gameTime}")
                println("Collect ${pipe.value} in ${pipe.delay}")
                println("Total: ${robot.resources}")
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

    fun Pipe.collectAndValueOrCollect() {
        collect()
        when {
            value == 10 || (TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING / delay) * value < 96 -> value()
            else -> collect()
        }
    }

    fun collectInfoAboutPipes(exclude: Pipe? = null, isFirstTime: Boolean = false) {
        if (!isFirstTime) {
            var pipesWithDelayHigher1S = 0
            observedPipes.forEach {
                if (it.delay > 1000) pipesWithDelayHigher1S++
            }
            if (pipesWithDelayHigher1S == 3) with(observedPipes.random()) {
                modifier(type = Modifiers.shuffle.name)
                collectAndValueOrCollect()
                return
            }
        }

        if (exclude != null) {
            val pipes = observedPipes.toMutableList()
            pipes.removeAt(exclude.number - 1)
            pipes.shuffle()
            pipes.forEach {
                it.collectAndValueOrCollect()
            }
        } else observedPipes.shuffled().forEach {
            it.collectAndValueOrCollect()
        }
    }

    fun Pipe.recalculateOutputValue() {
        val valueTickTimes = (robot.gameTime - timeOfCollectedValue) / delay
        if (valueTickTimes == 0) return

        timeOfCollectedValue += delay * valueTickTimes
        var newValue = value

        when (direction) {
            Direction.Down -> for (tick in 0 until valueTickTimes) {
                newValue--
                if (newValue < 1) newValue = PIPE_MAX_VALUE
            }

            else -> for (tick in 0 until valueTickTimes) {
                newValue++
                if (newValue > 10) newValue = PIPE_MIN_VALUE
            }
        }
        value = newValue
    }

    fun locallyFindBestPipe(): Pipe {
        var bestPipe = Pipe()
        var bestPipeValue = 0

        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            when (it.direction) {
                Direction.Down -> for (i in 0 until TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE step it.delay) {
                    localPipeValue += localValue--
                    if (localValue < 1) localValue = PIPE_MAX_VALUE
                }

                else -> for (i in 0 until TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE step it.delay) {
                    localPipeValue += localValue++
                    if (localValue > 10) localValue = PIPE_MIN_VALUE
                }
            }
            if (localPipeValue > bestPipeValue) {
                bestPipeValue = localPipeValue
                bestPipe = it
            }
        }
        return bestPipe
    }

    fun checkOtherPipes(exclude: Pipe) {
        val pipes = observedPipes.toMutableList()
        pipes.removeAt(exclude.number - 1)

    }

    collectInfoAboutPipes(isFirstTime = true)
    var bestPipe = locallyFindBestPipe()
    while (true) {
        with(bestPipe) {
            collect()
            if (delay > MIN_PING_TO_SIT && robot.gameTime % TIME_BETWEEN_REQUESTS <= delay)
                collectInfoAboutPipes(exclude = this)

            bestPipe = locallyFindBestPipe()
        }
    }
}
