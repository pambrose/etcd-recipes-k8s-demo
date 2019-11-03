package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.withKvClient
import io.etcd.recipes.election.LeaderSelector
import io.ktor.application.call
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
import kotlin.random.Random
import kotlin.time.seconds

class ElectionCandidate {
    companion object : KLogging() {
        const val VERSION = "1.0.1"

        @JvmStatic
        fun main(args: Array<String>) {
            val id = randomId()
            val className = ElectionCandidate::class.java.simpleName
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val keepAliveLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)
            val startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
            val desc = "$className $id ${hostInfo.first} [${hostInfo.second}] $VERSION $startTime"

            logger.info("Starting $desc")

            thread {
                val leadershipAction = { selector: LeaderSelector ->
                    println("${selector.clientId} elected leader")
                    val pause = Random.nextInt(1, 10).seconds
                    sleep(pause)
                    println("${selector.clientId} surrendering after $pause")
                }
                try {
                    LeaderSelector(urls, electionPath, leadershipAction)
                        .use { selector ->
                            while (true) {
                                selector.start()
                                selector.waitOnLeadershipComplete()
                            }
                        }
                } catch (e: Throwable) {
                    logger.warn(e) { e.stackTraceAsString }
                }
            }

            thread {
                connectToEtcd(urls) { client ->
                    client.withKvClient { kvClient ->
                        kvClient.putValueWithKeepAlive(client, "$clientPath/$id", desc, 2) {
                            keepAliveLatch.await()
                        }
                    }
                }
            }

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondWith("Version: $VERSION $desc")
                        }
                        get("/version") {
                            call.respondWith("Version: ${VERSION}")
                        }
                        get("/id") {
                            call.respondWith(desc)
                        }
                        get("/ping") {
                            var msg = "Success";
                            try {
                                etcdExec(urls) { it.getValue(electionPath, "") }
                            } catch (e: Throwable) {
                                msg = e.stackTraceAsString
                            }
                            call.respondWith("Ping result: $msg")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                keepAliveLatch.countDown()
                                finishLatch.countDown()
                            }
                            val msg = "Terminating $desc"
                            logger.info(msg)
                            call.respondWith(msg)
                        }
                    }
                }

            httpServer.start(wait = false)

            finishLatch.await()

            logger.info("Shutting down $desc")
        }
    }

}