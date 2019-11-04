package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.hostInfo
import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import com.sudothought.common.util.stackTraceAsString
import io.etcd.recipes.cache.PathChildrenCache
import io.etcd.recipes.common.asString
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.delete
import io.etcd.recipes.common.getChildrenKeys
import io.etcd.recipes.common.getValue
import io.etcd.recipes.common.putValue
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.withKvClient
import io.etcd.recipes.counter.DistributedAtomicLong
import io.etcd.recipes.election.LeaderSelector.Companion.getParticipants
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import mu.KLogging
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.seconds

class EtcdAdmin {
    companion object : KLogging() {
        const val VERSION = "1.0.17"

        @JvmStatic
        fun main(args: Array<String>) {
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val demoPath = "/demo/kvs"
            val keepAliveLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)
            val id = randomId()
            val className = EtcdAdmin::class.java.simpleName
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

            val cache = PathChildrenCache(urls, clientPath).start(true, true)

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondRedirect("/desc", permanent = true)
                        }
                        get("/desc") {
                            call.respondWith(desc)
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
                        get("/set") {
                            etcdExec(urls) { it.putValue(demoPath, "This is a test") }
                            call.respondWith("Key $demoPath set")
                        }
                        get("/get") {
                            var kval = "";
                            etcdExec(urls) { kval = it.getValue(demoPath, "$demoPath not present") }
                            call.respondWith("Key value = $kval")
                        }
                        get("/delete") {
                            etcdExec(urls) { it.delete(demoPath) }
                            call.respondWith("Key $demoPath deleted")
                        }
                        get("/count") {
                            val cnt = DistributedAtomicLong(urls, counterPath).get()
                            call.respondWith("Distributed count = $cnt")
                        }
                        get("/dump") {
                            var keys = emptyList<String>()
                            etcdExec(urls) { keys = it.getChildrenKeys("/").sorted() }
                            call.respondWith("${keys.size} keys:\n\n${keys.joinToString("\n")}")
                        }
                        get("/clients") {
                            val data =
                                cache.currentData
                                    .map { it.value.asString }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }
                            call.respondWith("${data.size} clients:\n\n${data.joinToString("\n")}\n\nReported by: $desc")
                        }
                        get("/leader") {
                            var kval = "";
                            etcdExec(urls) { kval = it.getValue(msgPath, "$msgPath not present") }
                            call.respondWith("Leader: $kval")
                        }
                        get("/election") {

                            val participants = getParticipants(urls, electionPath)
                            val data =
                                participants
                                    .map { it.toString() }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }
                            call.respondWith("${data.size} election participants:\n\n${data.joinToString("\n")}\n\nReported by: $desc")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                cache.close()
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