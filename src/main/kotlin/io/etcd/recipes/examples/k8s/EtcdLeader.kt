package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.etcdExec
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.etcd.recipes.election.LeaderSelector
import io.etcd.recipes.node.TtlNode
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
import kotlin.random.Random
import kotlin.time.seconds

class EtcdLeader {
    companion object : KLogging() {
        const val VERSION = "1.0.18"

        @JvmStatic
        fun main(args: Array<String>) {
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val finishLatch = CountDownLatch(1)
            val id = randomId()
            val className = EtcdLeader::class.java.simpleName
            val startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
            val desc = "$className $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $VERSION $startTime"

            logger.info { "Starting $desc" }

            val clientNode = TtlNode(urls, "$clientPath/$id", desc, keepAliveTtl).start()

            thread {
                val leadershipAction = { selector: LeaderSelector ->
                    val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
                    val msg = "${selector.clientId} elected leader at $now"
                    logger.info { msg }
                    etcdExec(urls) { _, kvClient -> kvClient.putValue(msgPath, msg) }
                    val pause = Random.nextInt(2, 10).seconds
                    sleep(pause)
                    logger.info { "${selector.clientId} surrendering after $pause" }
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
                                etcdExec(urls) { _, kvClient ->
                                    kvClient.putValue(pingPath, "pong")
                                    msg = kvClient.getValue(pingPath, "Missing key")
                                }
                            } catch (e: Throwable) {
                                msg = e.stackTraceAsString
                            }
                            call.respondWith("Ping result: $msg")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                clientNode.close()
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