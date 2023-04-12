import kotlin.math.abs

//fun main() {
//    val observedPipes = listOf(
//        Pipe(1), Pipe(2), Pipe(3)
//    )
//
//    val robot = Robot(bestPipe = Pipe())
//
//    fun getShuffledObservedPipes(exclude: Pipe) =
//        observedPipes.toMutableList().apply {
//            removeAt(exclude.number - 1)
//            shuffle()
//        }
//
//    val excluded = observedPipes.random()
//    val newObserverd = getShuffledObservedPipes(excluded)
//    println("excluded: ${excluded.number}")
//    println(newObserverd.size)
//    println(29579 / 1000)
//
//    fun changeBestPipe() {
//        robot.bestPipe = observedPipes.random()
//    }
//
//    println("first: ${robot.bestPipe.number}")
//    changeBestPipe()
//    println("second: ${robot.bestPipe.number}")
//}

fun main(args: Array<String>) {
    val value = args[0].toInt()
    val newValue = args[1].toInt()
    var direction = Direction.Unknown
    if (abs(newValue - value) <= 4)
        direction = if (newValue > value) Direction.Up else Direction.Down
    val peoplesOnPipe = when (direction) {
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
    println(peoplesOnPipe)
}