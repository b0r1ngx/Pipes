fun main() {
    val observedPipes = listOf(
        Pipe(1), Pipe(2), Pipe(3)
    )

    fun getShuffledObservedPipes(exclude: Pipe) =
        observedPipes.toMutableList().apply {
            removeAt(exclude.number - 1)
            shuffle()
        }

    val excluded = observedPipes.random()
    val newObserverd = getShuffledObservedPipes(excluded)
    println("excluded: ${excluded.number}")
    println(newObserverd.size)
}