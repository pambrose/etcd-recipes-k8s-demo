package io.etcd.recipes.examples.k8s

import com.sudothought.common.concurrent.BooleanMonitor
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

class EtcdAdmin {
    companion object : KLogging() {
        const val VERSION = "1.0.16"

        @JvmStatic
        fun main(args: Array<String>) {
            val id = randomId()
            val className = EtcdAdmin::class.java.simpleName
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val counterPath = "/counter/count1"
            val demoPath = "/counter/basics"
            val keepAliveLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)
            val endCounter = BooleanMonitor(false)
            val startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FMT).withZone(ZoneId.of(TZ)))
            val desc = "$className $id ${hostInfo.first} [${hostInfo.second}] $VERSION $startTime"

            logger.info { "Starting $desc" }

            val cache = PathChildrenCache(urls, clientPath).start(true)

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
                            call.respondRedirect("/version", permanent = true)
                        }
                        get("/version") {
                            call.respondWith("Version: $VERSION")
                        }
                        get("/id") {
                            call.respondWith(desc)
                        }
                        get("/ping") {
                            var msg = "Success";
                            try {
                                etcdExec(urls) { it.getValue(demoPath, "") }
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
                            val cnt = DistributedAtomicLong(urls, demoPath).get()
                            call.respondWith("Count = $cnt")
                        }
                        get("/dump") {
                            var keys = emptyList<String>()
                            etcdExec(urls) { keys = it.getChildrenKeys("/").sorted() }
                            call.respondWith("${keys.size} keys:\n\n${keys.joinToString("\n")}")
                        }
                        get("/admins") {
                            val data =
                                cache.currentData
                                    .map { it.value.asString }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }
                            call.respondWith("${data.size} admins:\n${data.joinToString("\n")}\n\nReported by: $desc")
                        }
                        get("/leader") {
                            var kval = "";
                            etcdExec(urls) { kval = it.getValue(msgPath, "$msgPath not present") }
                            call.respondWith("Leader: $kval")
                        }
                        get("/participants") {

                            val participants = getParticipants(urls, electionPath)
                            val data =
                                participants
                                    .map { it.toString() }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }
                            call.respondWith("${data.size} election participants:\n${data.joinToString("\n")}\n\nReported by: $desc")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                endCounter.set(true)
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