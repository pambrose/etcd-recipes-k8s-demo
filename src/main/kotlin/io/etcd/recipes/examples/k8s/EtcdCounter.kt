package io.etcd.recipes.examples.k8s

import com.sudothought.common.concurrent.BooleanMonitor
import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.withKvClient
import io.etcd.recipes.counter.DistributedAtomicLong
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import mu.KLogging
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.seconds

class EtcdCounter {
    companion object : KLogging() {
        const val VERSION = "1.0.17"

        @JvmStatic
        fun main(args: Array<String>) {
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val keepAliveLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)
            val endCounter = BooleanMonitor(false)
            val id = randomId()
            val className = EtcdCounter::class.java.simpleName
            val startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
            val desc = "$className $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $VERSION $startTime"

            logger.info { "Starting $desc" }

            thread {
                connectToEtcd(urls) { client ->
                    client.withKvClient { kvClient ->
                        logger.info { "Keep-alive started for $desc" }
                        kvClient.putValueWithKeepAlive(client, "$clientPath/$id", desc, keepAliveTtl) {
                            keepAliveLatch.await()
                            logger.info { "Keep-alive terminated for $desc" }
                        }
                    }
                }
            }

            thread {
                try {
                    DistributedAtomicLong(urls, counterPath)
                        .use { counter ->
                            while (true) {
                                counter.increment()
                                if (endCounter.get())
                                    break
                                sleep(1.seconds)
                            }
                        }
                } catch (e: Throwable) {
                    logger.warn(e) { e.stackTraceAsString }
                }
            }

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondRedirect("/desc", permanent = true)
                        }
                        get("/desc") {
                            call.respondWith(desc)
                        }
                        get("/ping") {
                            var msg = "";
                            try {
                                etcdExec(urls) {
                                    it.putValue(pingPath, "pong")
                                    msg = it.getValue(pingPath, "Missing key")
                                }
                            } catch (e: Throwable) {
                                msg = e.stackTraceAsString
                            }
                            call.respondWith("Ping result: $msg")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                endCounter.set(true)
                                keepAliveLatch.countDown()
                                finishLatch.countDown()
                            }
                            val msg = "Terminating $desc"
                            logger.info { msg }
                            call.respondWith(msg)
                        }
                    }
                }

            httpServer.start(wait = false)

            finishLatch.await()

            logger.info { "Shutting down $desc" }
        }
    }
}