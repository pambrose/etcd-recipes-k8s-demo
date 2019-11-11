package io.etcd.recipes.examples.k8s

import com.sudothought.common.concurrent.BooleanMonitor
import com.sudothought.common.concurrent.thread
import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.counter.withDistributedAtomicLong
import io.etcd.recipes.keyvalue.TransientKeyValue
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.concurrent.thread
import kotlin.time.seconds

class EtcdCounter {
    companion object : EtcdService() {
        private const val VERSION = "1.0.20"
        private val port = Integer.parseInt(System.getProperty("PORT") ?: "8082")
        private val className = EtcdCounter::class.java.simpleName
        private val desc get() = "$className:$VERSION $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $startDesc"

        @JvmStatic
        fun main(args: Array<String>) {
            val endCounter = BooleanMonitor(false)

            logger.info { "Starting $desc" }

            connectToEtcd(urls) { client ->
                TransientKeyValue(client, "$clientPath/$id", desc, keepAliveTtl).use {

                    thread {
                        try {
                            withDistributedAtomicLong(client, counterPath) {
                                while (!endCounter.get()) {
                                    increment()
                                    endCounter.waitUntilTrue(1.seconds)
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
                                    thread(finishLatch) {
                                        endCounter.set(true)
                                        sleep(1.seconds)
                                    }
                                    val msg = "Terminating $desc"
                                    logger.info { msg }
                                    call.respondWith(msg)
                                }
                            }
                        }

                    httpServer.start(wait = false)

                    finishLatch.await()
                }
            }
            logger.info { "Shutting down $desc" }
        }
    }
}