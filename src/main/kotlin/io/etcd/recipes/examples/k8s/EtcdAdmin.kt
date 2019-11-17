package io.etcd.recipes.examples.k8s

import com.github.pambrose.common.concurrent.thread
import com.github.pambrose.common.util.hostInfo
import com.github.pambrose.common.util.sleep
import io.etcd.recipes.cache.PathChildrenCache
import io.etcd.recipes.common.asString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.deleteKey
import io.etcd.recipes.common.getChildrenKeys
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.etcd.recipes.counter.DistributedAtomicLong
import io.etcd.recipes.election.LeaderSelector.Companion.getParticipants
import io.etcd.recipes.keyvalue.withTransientKeyValue
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlin.time.seconds

class EtcdAdmin {
    companion object : EtcdService() {
        private const val VERSION = "1.0.22"
        private val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
        private val className: String = EtcdAdmin::class.java.simpleName
        private val desc get() = "$className:$VERSION $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $startDesc"
        private val demoPath = "/demo/kvs"

        @JvmStatic
        fun main(args: Array<String>) {
            logger.info { "Starting $desc" }

            connectToEtcd(urls) { client ->
                withTransientKeyValue(client, "$clientPath/$id", desc, keepAliveTtl) {
                    PathChildrenCache(client, clientPath).start(true, true).use { cache ->
                        DistributedAtomicLong(client, counterPath).use { counter ->

                            val httpServer =
                                embeddedServer(CIO, port = port) {
                                    routing {
                                        get("/") {
                                            val leader = client.getValue(msgPath, "$msgPath not present")

                                            val clients =
                                                cache.currentData
                                                    .map { it.value.asString }.sorted()
                                                    .mapIndexed { i, s -> "${i + 1}) $s" }

                                            val participants =
                                                getParticipants(client, electionPath)
                                                    .map { it.toString() }.sorted()
                                                    .mapIndexed { i, s -> "${i + 1}) $s" }

                                            // Do not indent because it will screw up html output
                                            val output = """
Reported by: $desc $age

Distributed count: ${counter.get()}

Leader: $leader

${participants.size} election participants:
${participants.joinToString("\n")}

${clients.size} clients:
${clients.joinToString("\n")}
                            """.trimIndent()
                                            call.respondWith(output)
                                        }
                                        get("/html") {
                                            val sbld = StringBuilder()
                                            val str =
                                                sbld.appendHTML().html {
                                                    head {}
                                                    body {}
                                                }
                                            call.respondWith(desc, ContentType.Text.Html)
                                        }
                                        get("/keys") {
                                            val keys = client.getChildrenKeys("/").sorted()
                                            call.respondWith("${keys.size} keys:\n\n${keys.joinToString("\n")}")
                                        }
                                        get("/ping") {
                                            call.respondWith("Ping result: ${ping(urls)}")
                                        }
                                        get("/set") {
                                            client.putValue(demoPath, "This is a test")
                                            call.respondWith("Key $demoPath set")
                                        }
                                        get("/get") {
                                            val kval = client.getValue(demoPath, "$demoPath not present")
                                            call.respondWith("Key value = $kval")
                                        }
                                        get("/delete") {
                                            client.deleteKey(demoPath)
                                            call.respondWith("Key $demoPath deleted")
                                        }
                                        get("/terminate") {
                                            thread(finishLatch) {
                                                sleep(5.seconds)
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
                }
            }
            logger.info { "Shutting down $desc" }
        }
    }
}