package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.randomId
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.etcdExec
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


suspend fun ApplicationCall.respondWith(str: String, contentType: ContentType = ContentType.Text.Plain) {
    apply {
        response.header("cache-control", "must-revalidate,no-cache,no-store")
        response.status(HttpStatusCode.OK)
        respondText(str, contentType)
    }
}

open class EtcdAbstract : KLogging() {
    val TZ = "America/Los_Angeles"
    val FMT = "M/d/y H:m:ss"
    val clientPath = "/clients"
    val electionPath = "/election/k8s-demo"
    val msgPath = "/msgs/leader"
    val pingPath = "/msgs/ping"
    val counterPath = "/counter/k8s-demo"
    val keepAliveTtl = 10L

    val finishLatch = CountDownLatch(1)

    val urls = listOf("http://etcd-client:2379", "http://localhost:2379")
    val id = randomId()
    val startDesc = localNow
    val startTime = MonoClock.markNow()

    val localNow get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
    val age get() = startTime.elapsedNow()

    fun ping(urls: List<String>): String {
        var msg = "";
        try {
            etcdExec(urls) { _, kvClient ->
                kvClient.putValue(pingPath, "pong")
                msg = kvClient.getValue(pingPath, "Missing key")
            }
        } catch (e: Throwable) {
            msg = e.stackTraceAsString
        }
        return msg
    }
}