package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.sleep
import io.etcd.recipes.cache.PathChildrenCache
import io.etcd.recipes.common.asString
import io.etcd.recipes.common.delete
import io.etcd.recipes.common.etcdExec
import io.etcd.recipes.common.getChildrenKeys
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.etcd.recipes.counter.DistributedAtomicLong
import io.etcd.recipes.election.LeaderSelector.Companion.getParticipants
import io.etcd.recipes.node.TtlNode
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
import kotlin.concurrent.thread
import kotlin.time.seconds

class EtcdAdmin {
    companion object : EtcdAbstract() {
        const val VERSION = "1.0.19"
        val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
        val className = EtcdAdmin::class.java.simpleName
        val desc get() = "$className:$VERSION $id ${hostInfo.hostName} [${hostInfo.ipAddress}] $startDesc"

        @JvmStatic
        fun main(args: Array<String>) {
            val demoPath = "/demo/kvs"

            logger.info { "Starting $desc" }

            val clientNode = TtlNode(urls, "$clientPath/$id", desc, keepAliveTtl).start()

            val cache = PathChildrenCache(urls, clientPath).start(true, true)

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            var leader = "";
                            etcdExec(urls) { _, kvClient ->
                                leader = kvClient.getValue(msgPath, "$msgPath not present")
                            }

                            val clients =
                                cache.currentData
                                    .map { it.value.asString }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }

                            val participants =
                                getParticipants(urls, electionPath)
                                    .map { it.toString() }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }

                            // Do not indent because it will screw up html output
                            val output = """
Reported by: $desc $age

Distributed count: ${DistributedAtomicLong(urls, counterPath).get()}

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
                            var keys = emptyList<String>()
                            etcdExec(urls) { _, kvClient -> keys = kvClient.getChildrenKeys("/").sorted() }
                            call.respondWith("${keys.size} keys:\n\n${keys.joinToString("\n")}")
                        }
                        get("/ping") {
                            call.respondWith("Ping result: ${ping(urls)}")
                        }
                        get("/set") {
                            etcdExec(urls) { _, kvClient -> kvClient.putValue(demoPath, "This is a test") }
                            call.respondWith("Key $demoPath set")
                        }
                        get("/get") {
                            var kval = "";
                            etcdExec(urls) { _, kvClient ->
                                kval = kvClient.getValue(demoPath, "$demoPath not present")
                            }
                            call.respondWith("Key value = $kval")
                        }
                        get("/delete") {
                            etcdExec(urls) { _, kvClient -> kvClient.delete(demoPath) }
                            call.respondWith("Key $demoPath deleted")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                cache.close()
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