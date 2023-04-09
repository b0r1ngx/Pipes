import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val MIN_TIME_BETWEEN_REQUESTS = 13370
const val TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING = 6000
const val TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE = 12000

val PATTERN = Regex("""\d+""")

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0
)

enum class Direction { Up, Down, Unknown }

data class Pipe(
    val number: Int = 0, // 1, 2, 3
    var value: Int = 0,
    var delay: Int = 3000,
    var timeOfCollectedValue: Int = 0,
    var direction: Direction = Direction.Unknown
)

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

            var value = 0
            when (responseCode) {
                200 -> inputStream.bufferedReader().use {
                    if (method != Method.POST) value = PATTERN.find(it.readText())?.value?.toInt() ?: 0
                    else println("Applied modifier $type to pipe with url: $url")
                }

                422 -> println("Failed to apply modifier")
                else -> println("Unexpected status code $responseCode")
            }

            if (pipe.value != 0)
                pipe.direction = if (value > pipe.value) Direction.Up else Direction.Down
            pipe.value = value
            robot.resources += value

            if (method == Method.PUT) {
                pipe.delay = (currentTimeMillis() - t1).toInt()
                robot.gameTime += pipe.delay
                pipe.timeOfCollectedValue = robot.gameTime
            }

            println("collect ${pipe.value} in ${pipe.delay}")
            println("total resources: ${robot.resources}")
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

    fun collectInfoAboutPipes(exclude: Pipe? = null) {
        if (exclude != null) {
            val pipes = observedPipes.toMutableList()
            pipes.removeAt(exclude.number - 1)
            pipes.forEach {
                it.collect()
                when {
                    it.value == 10 || (TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING / it.delay) * it.value < 96 -> it.value()
                    else -> it.collect()
                }
            }
        } else observedPipes.forEach {
            it.collect()
            when {
                it.value == 10 || (TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING / it.delay) * it.value < 96 -> it.value()
                else -> it.collect()
            }
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

    collectInfoAboutPipes()
    var bestPipe = locallyFindBestPipe()

    var collectTimes = 0
    var notCollectTimes = 0
    var timeBetweenPingRequests = MIN_TIME_BETWEEN_REQUESTS

    while (true) {
        bestPipe.collect()

        if (robot.gameTime % timeBetweenPingRequests <= bestPipe.delay) {
            if (timeBetweenPingRequests > MIN_TIME_BETWEEN_REQUESTS) {
                collectInfoAboutPipes(exclude = bestPipe)
            } else if (notCollectTimes >= 3) {
                collectInfoAboutPipes(exclude = bestPipe)
                collectTimes++
                notCollectTimes = 0
            } else if (collectTimes >= 3) {
                timeBetweenPingRequests = when {
                    robot.gameTime <= 150000 -> 90000
                    robot.gameTime <= 200000 -> 60000
                    robot.gameTime <= 250000 -> 30000
                    else -> 120000
                }
                collectTimes = 0
            } else notCollectTimes++
        }

        bestPipe = locallyFindBestPipe()
    }
}
