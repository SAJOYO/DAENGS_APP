package com.daengs.app.ui.walk

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread

/** Loopback HTTP transports frozen backend bytes. It does not claim to run the backend. */
internal class MeasurementFixtureHttp(private val wire: JSONObject) : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val origin = "http://127.0.0.1:${socket.localPort}"
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val responses = buildMap {
        val summary = JSONObject(wire.getString("summary"))
        val base = "/app/walks/${summary.getString("walk_id")}/measurements"
        val id = summary.getJSONObject("measurement").getString("measurement_id")
        val version = "?version=${summary.getString("version")}"
        put("GET /app/walks/trajectory-capabilities", JSONObject().put("persisted_measurements_supported", true)
            .put("measurement_versions", JSONArray().put(summary.getString("version"))).toString())
        put("POST $base$version", wire.getString("summary"))
        val pages = wire.getJSONArray("pages")
        for (i in 0 until pages.length()) put("GET $base/$id/chunks/$i$version", pages.getString(i))
    }
    private val worker = thread(name = "measurement-fixture-http", isDaemon = true) {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            client.use {
                it.soTimeout = 10_000
                val reader = it.getInputStream().bufferedReader()
                val line = reader.readLine() ?: return@use
                var length = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                repeat(length) { reader.read() }
                requests.add(line)
                val parts = line.split(' ')
                val response = if (parts.size == 3 && parts[2] == "HTTP/1.1") responses["${parts[0]} ${parts[1]}"] else null
                val status = if (response != null) "200 OK" else "404 Not Found"
                val body = (response ?: "{\"error\":\"unexpected fixture request\"}").toByteArray(Charsets.UTF_8)
                it.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body); flush()
                }
            }
        }
    }
    override fun close() { socket.close(); worker.join(10_000); check(!worker.isAlive) }
}
