import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val PIPE_VALUE_ESTIMATE_TIME = 4000 // 6000, 11250
const val TIME_BETWEEN_PING_REQUESTS = 200000 // 110000
val PATTERN = Regex("""\d+""")

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0,
    var collectingValue: Int = 0,
    var minDelayToLockCollect: Long = 500,
)

enum class Direction { Up, Down, Unknown }

data class Pipe(
    val number: Int = 0, // 1, 2, 3
    var value: Int = 0,
    var delay: Int = 3000,
    var timeOfCollectedValue: Int = 0,
    var direction: Direction = Direction.Unknown
    //    val pipeValue: Int = value * delay
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
        method: String,
        url: String,
        token: String,
        type: String? = null
    ): Pipe {
        val t1 = currentTimeMillis()
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true

            if (type != null) {
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write("""{"type":"$type"}""") }
            }

            var value = 0
            when (responseCode) {
                200 -> inputStream.bufferedReader().use {
                    if (method != Method.POST.name) value = PATTERN.find(it.readText())?.value?.toInt() ?: 0
                    else println("Applied modifier $type to pipe with url: $url")
                }

                422 -> println("Failed to apply modifier")
                else -> println("Unexpected status code $responseCode")
            }

            if (pipe.value != 0)
                pipe.direction = if (value > pipe.value) Direction.Up else Direction.Down
            pipe.value = value
            pipe.delay = (currentTimeMillis() - t1).toInt()

            robot.resources += value
            robot.gameTime += pipe.delay

            pipe.timeOfCollectedValue = robot.gameTime

            println("collect ${pipe.value} in ${pipe.delay}")
            println("total resources: ${robot.resources}")

            return pipe
        }
    }

    fun Pipe.collect() = sendRequest(
        pipe = this,
        method = Method.PUT.name,
        url = "$apiUrl/pipe/${this.number}",
        token = token
    )

    fun Pipe.value() = sendRequest(
        pipe = this,
        method = Method.GET.name,
        url = "$apiUrl/pipe/${this.number}/value",
        token = token
    )

    fun Pipe.modifier(type: String) = sendRequest(
        pipe = this,
        method = Method.POST.name,
        url = "$apiUrl/pipe/${this.number}/modifier",
        token = token,
        type = type
    )

    fun collectInfoAboutPipes(exclude: Pipe? = null) {
        if (exclude != null) {
            val pipes = observedPipes.toMutableList()
            pipes.removeAt(exclude.number - 1)
            pipes.forEach {
                it.collect()
                it.collect()
            }
        } else observedPipes.forEach {
            it.collect()
            it.collect()
        }
    }

    fun Pipe.recalculateOutputValue() {
        val valueTickTimes = (robot.gameTime - timeOfCollectedValue) % delay
        if (valueTickTimes == 0) return

        timeOfCollectedValue += delay * valueTickTimes
        var newValue = value

        when (direction) {
            Direction.Down -> for (tick in 0 until valueTickTimes) {
                newValue++
                if (newValue < 1) newValue = PIPE_MAX_VALUE
            }

            else -> for (tick in 0 until valueTickTimes) {
                newValue++
                if (newValue > 10) newValue = PIPE_MIN_VALUE
            }
        }

        value = newValue
    }

    fun findBestPipe(): Pipe {
        var bestPipe = Pipe()
        var pipeValue = 0

        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            when (it.direction) {
                Direction.Down -> for (i in 0 until PIPE_VALUE_ESTIMATE_TIME step it.delay) {
                    localPipeValue += localValue--
                    if (localValue < 1) localValue = PIPE_MAX_VALUE
                }

                else -> for (i in 0 until PIPE_VALUE_ESTIMATE_TIME step it.delay) {
                    localPipeValue += localValue++
                    if (localValue > 10) localValue = PIPE_MIN_VALUE
                }
            }

            if (localPipeValue > pipeValue) {
                pipeValue = localPipeValue
                bestPipe = it
            }
        }
        return bestPipe
    }

    collectInfoAboutPipes()
    var bestPipe: Pipe
    while (true) {
        bestPipe = findBestPipe()
        bestPipe.collect()
        if (bestPipe.delay > robot.minDelayToLockCollect && robot.gameTime % TIME_BETWEEN_PING_REQUESTS < bestPipe.delay + 50) {
            collectInfoAboutPipes(exclude = bestPipe)
        }
    }
}
