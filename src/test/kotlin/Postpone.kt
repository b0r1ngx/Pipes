import java.util.*
import kotlin.concurrent.schedule

val timer = Timer("iWantToWinEverybodyInTheWorld", true)

fun postpone(delay: Long) = timer.schedule(delay = delay) { return@schedule }

fun main() {
    postpone(delay = 120)
    Thread.sleep(1000) // works !
    println("World")
}