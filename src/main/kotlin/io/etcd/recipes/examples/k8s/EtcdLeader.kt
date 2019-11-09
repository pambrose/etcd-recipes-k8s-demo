package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.etcdExec
import io.etcd.recipes.common.putValue
import io.etcd.recipes.election.LeaderSelector
import io.etcd.recipes.node.TtlNode
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.seconds

class EtcdLeader {
    companion object : EtcdService() {
        private const val VERSION = "1.0.20"
        private val port = Integer.parseInt(System.getProperty("PORT") ?: "8081")
        private val className: String = EtcdLeader::class.java.simpleName
        private val desc get() = "$className:$VERSION $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $startDesc"

        @JvmStatic
        fun main(args: Array<String>) {

            logger.info { "Starting $desc" }

            val clientNode = TtlNode(urls, "$clientPath/$id", desc, keepAliveTtl)

            thread {
                val leadershipAction = { selector: LeaderSelector ->
                    val now = localNow
                    val pause = Random.nextInt(2, 10).seconds
                    val electMsg = "${selector.clientId} elected leader at $now for $pause"
                    logger.info { electMsg }
                    etcdExec(urls) { _, kvClient -> kvClient.putValue(msgPath, electMsg) }
                    sleep(pause)
                    val surrenderMsg = "${selector.clientId} surrendered after $pause"
                    etcdExec(urls) { _, kvClient -> kvClient.putValue(msgPath, surrenderMsg) }
                    logger.info { surrenderMsg }
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
                            call.respondWith("Ping result: ${ping(urls)}")
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