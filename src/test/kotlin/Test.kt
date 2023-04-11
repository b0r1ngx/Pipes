fun main() {
    val observedPipes = listOf(
        Pipe(1), Pipe(2), Pipe(3)
    )

    val robot = Robot(bestPipe = Pipe())

    fun getShuffledObservedPipes(exclude: Pipe) =
        observedPipes.toMutableList().apply {
            removeAt(exclude.number - 1)
            shuffle()
        }

    val excluded = observedPipes.random()
    val newObserverd = getShuffledObservedPipes(excluded)
    println("excluded: ${excluded.number}")
    println(newObserverd.size)
    println(29579 / 1000)

    fun changeBestPipe() {
        robot.bestPipe = observedPipes.random()
    }

    println("first: ${robot.bestPipe.number}")
    changeBestPipe()
    println("second: ${robot.bestPipe.number}")
}