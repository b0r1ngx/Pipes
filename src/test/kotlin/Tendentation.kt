const val TIME_CALCULATING_TENDENCY = 228337
fun Pipe.tendency(): Pair<Pipe, Int> {
    var localPipeValue = 0
    var localValue = value

    when (direction) {
        Direction.Down -> for (i in 0 until TIME_CALCULATING_TENDENCY step delay) {
            for (j in 0 until peoplesOnPipe) {
                localValue--
                if (localValue < 1) localValue = PIPE_MAX_VALUE
            }
            localPipeValue += localValue
        }

        else -> for (i in 0 until TIME_CALCULATING_TENDENCY step delay) {
            for (j in 0 until peoplesOnPipe) {
                localValue++
                if (localValue > 10) localValue = PIPE_MIN_VALUE
            }
            localPipeValue += localValue
        }
    }

    println("$this, Total value: $localPipeValue")
    return this to localPipeValue
}

fun main() {
    val variousPipes = mutableListOf<Pipe>()
    var pipeNumber = 1
    for (value in 1..10)
//            for (delay in 0 until 10)
            variousPipes += Pipe(
                number = pipeNumber++,

                value = value,
                delay = 125, // Random.nextInt(100, 1000)
                direction = Direction.Unknown,

                peoplesOnPipe = 4
            )

    val t1 = System.currentTimeMillis()
    val pipesTendency = mutableListOf<Pair<Pipe, Int>>()
    variousPipes.forEach { pipesTendency.add(it.tendency()) }

    val bestPipeTendency = pipesTendency.maxBy { it.second }
    println("Best: $bestPipeTendency")
    println(System.currentTimeMillis() - t1)
}