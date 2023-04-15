fun main() {
    val robot = Robot().apply {
        bestPipe = pipes.first()
    }

    println("Before ${robot.pipes}")

    var counter = 5
    fun Pipe.ping(): Int {
        value = counter++
        return value
    }

    fun Pipe.mockCollect() {
        value = 4
        delay = 500
    }

//    robot.getPipes(exclude = robot.bestPipe).forEach {
//        val firstPingValue = it.ping()
//        val secondPingValue = it.ping()
//        if (firstPingValue != secondPingValue) it.mockCollect()
//        if (it.delay < robot.bestPipe.delay) return@forEach
//    }

    println("After ${robot.pipes}")
}