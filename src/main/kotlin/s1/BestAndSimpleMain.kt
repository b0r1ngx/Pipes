package s1

import java.lang.System.currentTimeMillis
import java.net.HttpURLConnection
import java.net.URL

const val PIPE_MIN_VALUE = 1
const val PIPE_MAX_VALUE = 10
const val MIN_PING_TO_SIT = 200
const val PING_TO_CHECK = 450
const val TIME_BETWEEN_REQUESTS = 60000
const val TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE = 12000
const val TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING = 6000
const val TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE = 3 * 1550 // 3000
const val BAD_PIPE_DELAY = 850 // 1000

val PATTERN = Regex("""\d+""")

enum class Status { PINGING, COLLECTING }

data class Robot(
    var resources: Int = 0,
    var gameTime: Int = 0,
    var status: Status = Status.COLLECTING
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
                    pipe.direction = if (newValue > pipe.value) Direction.Up else Direction.Down
                    pipe.peoplesOnPipe = newValue - pipe.value
                    if (pipe.peoplesOnPipe >= 2) pipe.timeMostPeopleOnPipe = robot.gameTime
                }
                pipe.value = newValue

                if (method == Method.PUT) {
                    robot.resources += pipe.value

                    pipe.delay = (currentTimeMillis() - t1).toInt()
                    robot.gameTime += pipe.delay
                    pipe.timeOfCollectedValue = robot.gameTime
                }

                println(
                    "Time: ${robot.gameTime}. Peoples on pipe: ${pipe.peoplesOnPipe}. " +
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

    fun Pipe.collectAndValueOrCollect() {
        collect()
        if (delay > BAD_PIPE_DELAY) {
            value()
            return
        }

        recalculateOutputValue()
        val pipeValuable = (TIME_TO_NOT_COLLECT_SECOND_TIME_WHEN_PING / delay) * value < 96
        when {
            value == 10 && direction == Direction.Down && peoplesOnPipe > 1 -> collect()
            value == 10 && direction == Direction.Up -> value()
            value == 5 && direction == Direction.Down -> value()
            else -> collect()
        }
    }

    fun getShuffledObservedPipes(exclude: Pipe): List<Pipe> {
        val pipes = observedPipes.toMutableList()
        pipes.removeAt(exclude.number - 1)
        pipes.shuffle()
        return pipes
    }

    fun collectInfoAboutPipes(exclude: Pipe? = null, isFirstTime: Boolean = false) {
        if (!isFirstTime) {
            var pipesWithDelayHigherConst = 0
            observedPipes.forEach {
                if (it.delay >= BAD_PIPE_DELAY) pipesWithDelayHigherConst++
            }
            if (pipesWithDelayHigherConst == 3) with(observedPipes.random()) {
                modifier(type = Modifiers.shuffle.name)
                collectAndValueOrCollect()
                return
            }
        }

        if (exclude != null) getShuffledObservedPipes(exclude).forEach {
            it.collectAndValueOrCollect()
        }
        else observedPipes.shuffled().forEach {
            it.collectAndValueOrCollect()
        }
    }

    fun findBestPipe(): Pipe {
        var bestPipe = Pipe()
        var bestPipeValue = 0

        observedPipes.forEach {
            it.recalculateOutputValue()

            var localPipeValue = 0
            var localValue = it.value

            when (it.direction) {
                Direction.Down -> for (i in 0 until TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE step it.delay) {
                    for (j in 0 until it.peoplesOnPipe - 1) {
                        localValue--
                        if (localValue < 1) localValue = PIPE_MAX_VALUE
                    }
                    localPipeValue += localValue
                }

                else -> for (i in 0 until TIME_TO_CALCULATE_ESTIMATE_PIPE_VALUE step it.delay) {
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

    fun checkPeopleOnOtherPipes(exclude: Pipe) {
        val pipes = getShuffledObservedPipes(exclude = exclude)

        var firstPingValue: Int
        var secondPingValue: Int
        pipes.forEach {
            it.value()
            firstPingValue = it.value

            for (i in 0 until BAD_PIPE_DELAY step exclude.delay) // + 1 ; also maybe use findBestPipeLocally()
                exclude.collect()

            it.value()
            secondPingValue = it.value

            if (firstPingValue != secondPingValue)
                it.collect()

            if (it.delay < exclude.delay) {
                robot.status = Status.COLLECTING
                return
            }
        }
        robot.status = Status.COLLECTING
    }

    fun Pipe.predictNextValue(): Int {
        var nextValue = value
        when (direction) {
            Direction.Down -> for (j in 0 until peoplesOnPipe - 1) {
                nextValue--
                if (nextValue < 1) nextValue = PIPE_MAX_VALUE
            }

            else -> for (j in 0 until peoplesOnPipe - 1) {
                nextValue++
                if (nextValue > 10) nextValue = PIPE_MIN_VALUE
            }
        }
        return nextValue
    }

    collectInfoAboutPipes(isFirstTime = true)
    var bestPipe = findBestPipe()

    while (true) {
        with(bestPipe) {
            collect()

            val nextValue = predictNextValue()

            if (delay > PING_TO_CHECK
                && robot.gameTime % TIME_BETWEEN_REQUESTS <= delay
            ) collectInfoAboutPipes(exclude = this)

            if (robot.gameTime >= 10000
                && (timeMostPeopleOnPipe - robot.gameTime) >= TIME_TO_THINK_THAT_OTHER_FIND_BEST_PIPE
                && delay > MIN_PING_TO_SIT
                && peoplesOnPipe <= 2
            ) robot.status = Status.PINGING

            if (robot.status == Status.PINGING)
                checkPeopleOnOtherPipes(exclude = this)

            bestPipe = findBestPipe()
        }
    }
}
