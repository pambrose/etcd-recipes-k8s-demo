package io.etcd.recipes.examples.k8s

import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.stackTraceAsString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respondText
import mu.KLogging
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import kotlin.time.MonoClock

suspend fun ApplicationCall.respondWith(str: String,
                                        contentType: ContentType = ContentType.Text.Plain,
                                        status: HttpStatusCode = HttpStatusCode.OK) {
    apply {
        response.header("cache-control", "must-revalidate,no-cache,no-store")
        response.status(status)
        respondText(str, contentType, status)
    }
}

abstract class EtcdService : KLogging() {
    val tz = "America/Los_Angeles"
    val fmt = "M/d/y H:m:ss"
    val clientPath = "/clients"
    val electionPath = "/election/k8s-demo"
    val msgPath = "/msgs/leader"
    val pingPath = "/msgs/ping"
    val counterPath = "/counter/k8s-demo"
    val keepAliveTtl = 2L

    val finishLatch = CountDownLatch(1)

    val urls = listOf("http://etcd-client:2379", "http://localhost:2379")
    val id = randomId()
    val startDesc = localNow
    val startTime = MonoClock.markNow()

    val localNow: String get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern(fmt).withZone(ZoneId.of(tz)))
    val age get() = startTime.elapsedNow()

    fun ping(urls: List<String>): String {
        var msg = ""
        try {
            connectToEtcd(urls) { client ->
                client.putValue(pingPath, "pong")
                msg = client.getValue(pingPath, "Missing key")
            }
        } catch (e: Throwable) {
            msg = e.stackTraceAsString
        }
        return msg
    }
}