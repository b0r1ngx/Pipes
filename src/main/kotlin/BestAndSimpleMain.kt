import java.lang.System.currentTimeMillis
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val SIT_DELAY = 230
const val CHECK_DELAY = 290
const val BAD_DELAY = 830
const val PIPE_MEAN_DELAY = 1550
const val TIME_TO_DETERMINE_PIPE_VALUE = 3 * PIPE_MEAN_DELAY
const val TIME_TO_THINK_BETTER_PIPE_EXISTS = 3000

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

    var peoplesOnPipe: Int = 1,
    var timeAllPeopleOnPipe: Int = 10000,
    var timeOfCollectedValue: Int = 0
)

enum class Modifiers(val cost: Int) {
    reverse(40), double(50), slow(40), shuffle(10), min(10)
}

enum class Method { GET, PUT, POST }

fun HttpClient.send(request: HttpRequest) =
    send(request, HttpResponse.BodyHandlers.ofString())

fun dropRestrictions() {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Referer")
    System.setProperty("jdk.httpclient.redirects.retrylimit", "3")
    System.setProperty("jdk.httpclient.disableRetryConnect", "true")
    System.setProperty("jdk.httpclient.enableAllMethodRetry", "true")
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Content-Length")
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host")
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Keep-Alive")
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Connection")
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true")
    System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true")
    System.setProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow", "|{}")
}

fun main(args: Array<String>) {
    dropRestrictions()
    val host = args[0]
    val apiUrl = "http://$host/api"
    val token = args[1]

    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    val baseRequest = HttpRequest.newBuilder()
        .setHeader("Connection", "keep-alive")
        .setHeader("Keep-Alive", "true")
        .setHeader("Authorization", "Bearer $token")

    val robot = Robot()
    val observedPipes = listOf(Pipe(1), Pipe(2), Pipe(3))

    fun Pipe.sendRequest(
        method: Method,
        url: String,
        token: String,
        type: Modifiers? = null
    ) {
        val t1 = currentTimeMillis()

        val request = when (method) {
            Method.GET -> baseRequest.uri(URI(url)).GET().build()
            Method.PUT -> baseRequest.uri(URI(url)).PUT(HttpRequest.BodyPublishers.noBody()).build()
            Method.POST -> baseRequest.setHeader("Content-Type", "application/json").uri(URI(url))
                .POST(HttpRequest.BodyPublishers.ofString("""{"type":"${type!!.name}"}""")).build()
        }

        println("Request headers: ${request.headers()}")
        val response = client.send(request)
        println("Response headers: ${response.headers()}")
        val responseCode = response.statusCode()

        if (method == Method.POST) when (responseCode) {
            200 -> {
                robot.resources -= type!!.cost
                println("apply modifier $type to pipe with url: $url")
            }

            422 -> println("failed to apply modifier")
            else -> println("$method: unexpected status code $responseCode")
        }
        else {
            val newValue: Int
            when (responseCode) {
                200 -> newValue = PATTERN.find(response.body())?.value?.toInt() ?: 0

                else -> {
                    println("$method: unexpected status code $responseCode")
                    return
                }
            }

            if (value != 0) {
                if (newValue - value <= 4)
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

                if (peoplesOnPipe >= 3) timeAllPeopleOnPipe = robot.gameTime
            }

            value = newValue
            delay = (currentTimeMillis() - t1).toInt()

            if (method == Method.PUT) {
                robot.resources += value
                robot.gameTime += delay
                timeOfCollectedValue = robot.gameTime
            }

            println(
                "Time: ${robot.gameTime}. Pipe: $number, peoples: $peoplesOnPipe. " +
                        "Collected $value in $delay (total: ${robot.resources})"
            )
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
        val valueTickTimes = (robot.gameTime - timeOfCollectedValue + delay) / delay
        if (valueTickTimes == 0) return

        timeOfCollectedValue += delay * valueTickTimes

        var nextValue = value
        val peoplesOnPipe = if (this == robot.bestPipe) this.peoplesOnPipe else this.peoplesOnPipe - 1

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

    fun Pipe.collectAndSkipOrValueOrCollect() {
        collect()
        if (delay > BAD_DELAY) return

        recalculateOutputValue()
        when {
            delay <= SIT_DELAY || (TIME_TO_DETERMINE_PIPE_VALUE / delay) * value >= 63 -> collect()
            else -> value()
        }
    }

    fun getObservedPipes(exclude: Pipe) = observedPipes.toMutableList().apply {
        removeAt(exclude.number - 1)
        shuffle()
    }

    fun collectInfoAboutPipes() = observedPipes.shuffled().forEach {
        it.collectAndSkipOrValueOrCollect()
        if (it.delay <= SIT_DELAY) return
    }

    fun ifAllPipesBadShuffleBaddestPipe() {
        var badDelay = 0
        observedPipes.forEach { if (it.delay >= BAD_DELAY) badDelay++ }
        if (badDelay == 3) with(observedPipes.maxBy { it.delay }) {
            modifier(type = Modifiers.shuffle)
            collectAndSkipOrValueOrCollect()
        }
    }

    // 1 MS MAX
    fun findBestPipe() {
        var bestPipeValue = 0
        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            val peoplesOnPipe = if (it == robot.bestPipe) it.peoplesOnPipe else it.peoplesOnPipe - 1

            when (it.direction) {
                Direction.Down -> for (i in 0 until TIME_TO_DETERMINE_PIPE_VALUE step it.delay) {
                    for (j in 0 until peoplesOnPipe) {
                        localValue--
                        if (localValue < 1) localValue = PIPE_MAX_VALUE
                    }
                    localPipeValue += localValue
                }

                else -> for (i in 0 until TIME_TO_DETERMINE_PIPE_VALUE step it.delay) {
                    for (j in 0 until peoplesOnPipe) {
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

    // 200 MS
    fun Pipe.pingWithValue(): Int {
        value()
        return value
    }

    // (200 + some excludePipe.collect() + 200 + pipeWhereValueChanged.delay) * 2
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
            robot.bestPipe.delay >= SIT_DELAY + (robot.gameTime / PIPE_MEAN_DELAY) &&
            (robot.gameTime - robot.bestPipe.timeAllPeopleOnPipe) >= TIME_TO_THINK_BETTER_PIPE_EXISTS
        ) scanForPipes(exclude = robot.bestPipe)

        findBestPipe()

        if (robot.bestPipe.delay > BAD_DELAY) ifAllPipesBadShuffleBaddestPipe()
    }
}
