import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.system.measureTimeMillis

val PIPES = mutableListOf(1, 2, 3)
val PATTERN = Regex("""\d+""")
val client = HttpClient.newBuilder().build()

enum class Method {
    GET, PUT, POST
}

enum class Modifiers(cost: Int) {
    reverse(40), double(50), slow(40), shuffle(10), min(10)
}

fun main(args: Array<String>) {
    val host = args[0]
    val apiUrl = "http://$host/api"
    val token = args[1]

    fun collect(pipe: Int) = sendRequest(
        method = Method.PUT.name,
        url = "$apiUrl/pipe/$pipe",
        token = token
    )

    fun value(pipe: Int) = sendRequest(
        method = Method.GET.name,
        url = "$apiUrl/pipe/$pipe/value",
        token = token
    )

    fun modifier(pipe: Int, type: String) = sendRequest(
        method = Method.POST.name,
        url = "$apiUrl/pipe/$pipe/modifier",
        token = token,
        type = type
    )

    var resources = 0
    var timeInMillisPassedFromStart = 0L

    fun timeForOnePipeCollect(pipe: Int) = measureTimeMillis {
        resources += collect(pipe).body.toInt() // FormatException, cos this my prev solution, update it with other network layer
    }

    val myPipe = PIPES.random() // 1, 2, 3
    PIPES.remove(myPipe)
    var timeInMillis = timeForOnePipeCollect(myPipe)
    while (true) {
        timeInMillisPassedFromStart += timeInMillis
        timeInMillis = timeForOnePipeCollect(myPipe)
        println(timeInMillis)
        if (timeInMillis > 350) modifier(myPipe, Modifiers.shuffle.name)
//        else if (timeInMillisPassedFromStart % 7500 < 600L) for (pipe in PIPES) {
//            modifier(pipe, Modifiers.min.name)
//            modifier(pipe, Modifiers.min.name)
//        }
    }
}

fun value(token: String, url: String): Int {
    val req = HttpRequest.newBuilder()
        .header("Authorization", "Bearer $token")
        .uri(URI(url)).GET().build()

    val response = client.send(req, HttpResponse.BodyHandlers.ofString())
    println(response.body())
    return if (response.statusCode() == 200) PATTERN.find(response.body())!!.value.toInt()
    else 0
}

fun collect(token: String, url: String): Int {
    val req = HttpRequest.newBuilder()
        .header("Authorization", "Bearer $token")
        .uri(URI(url)).PUT(HttpRequest.BodyPublishers.noBody()).build()

    val response = client.send(req, HttpResponse.BodyHandlers.ofString())
    println(response.body())
    return if (response.statusCode() == 200) PATTERN.find(response.body())!!.value.toInt()
    else 0
}

fun modifier(token: String, url: String, type: String) {
    val req = HttpRequest.newBuilder()
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json")
        .uri(URI(url))
        .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"$type\"}"))
        .build()

    val response = client.send(req, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() == 200) println("Applied modifier $type to pipe $url")
    else if (response.statusCode() == 422) println("Failed to apply modifier")
    else println("Unexpected status code: ${response.statusCode()}")
}

data class Response(
    val body: String,
    val timeOut: Long
)

fun sendRequest(
    method: String,
    url: String,
    token: String,
    type: String? = null
): Response {
    val startRequest = System.currentTimeMillis()
    with(URL(url).openConnection() as HttpURLConnection) {
        requestMethod = method
        setRequestProperty("Authorization", "Bearer $token")
        doOutput = true

        if (type != null) {
            setRequestProperty("Content-Type", "application/json")
            outputStream.bufferedWriter().use { it.write("{\"type\":\"$type\"}") }
        }

        val stringBuilder = StringBuilder()
        inputStream.bufferedReader().use {
            val response = it.readText()
            println(response)
            stringBuilder.append(response)
        }

        return Response(
            body = stringBuilder.toString(),
            timeOut = System.currentTimeMillis() - startRequest
        )
    }
}
