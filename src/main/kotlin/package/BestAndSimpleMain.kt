package `package`

import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_VALUE_ESTIMATE_TIME = 6000 // 11250
val PATTERN = Regex("""\d+""")

enum class Status {
    Pinging, Collecting
}

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0,
    var collectingValue: Int = 0,
    var minDelayToCollect: Long = 200,
)

enum class Direction { Up, Down, Unknown }

data class Pipe(
    val number: Int, // 1, 2, 3
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
            pipes.removeIf { it.number == exclude.number }
            pipes.forEach {
                it.collect()
            }
        }
        else {
            observedPipes.forEach {
                it.collect()
            }
        }
    }

    collectInfoAboutPipes()

    fun findBestPipe(): Pipe {
        var bestPipe = observedPipes.first()
        var pipeValue = 0

        observedPipes.forEach {
            var localPipeValue = 0
            var localValue = it.value
            for (i in 0..PIPE_VALUE_ESTIMATE_TIME step it.delay)
                localPipeValue += localValue++

            if (localPipeValue > pipeValue) {
                pipeValue = localPipeValue
                bestPipe = it
            }
        }
        return bestPipe
    }

    var bestPipe = findBestPipe()
    while (true) {
        bestPipe.collect()
        bestPipe = findBestPipe()
        if (robot.gameTime % 60000 <= 500) {
            if (bestPipe.delay > 250) {
                collectInfoAboutPipes(exclude = bestPipe)
            }
        }
    }
}
