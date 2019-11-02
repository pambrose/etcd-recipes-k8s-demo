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
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respondText
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

suspend fun ApplicationCall.respondWith(str: String, contentType: ContentType = ContentType.Text.Plain) {
    apply {
        response.header("cache-control", "must-revalidate,no-cache,no-store")
        response.status(HttpStatusCode.OK)
        respondText(str, contentType)
    }
}

class Basics {
    companion object : KLogging() {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val urls = listOf("http://etcd-client:2379", "http://localhost:2379")
            val path = "/counter/basics"
            val idPath = "/clients"
            val id = randomId()
            val keepAliveLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)
            val endCounter = BooleanMonitor(false)
            val startTime =
                LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("M/d/y H:m:ss").withZone(ZoneId.of("America/Los_Angeles")))
            val version = "1.0.15"

            logger.info("Starting client $id $hostInfo")

            val cache = PathChildrenCache(urls, idPath).start(true)

            thread {
                val msg: String
                try {
                    DistributedAtomicLong(urls, path)
                        .use { counter ->
                            while (true) {
                                counter.increment()
                                if (endCounter.get())
                                    break
                                sleep(1.seconds)
                            }
                        }
                } catch (e: Throwable) {
                    msg = e.stackTraceAsString
                    logger.info(e) { msg }
                }
            }

            thread {
                connectToEtcd(urls) { client ->
                    client.withKvClient { kvClient ->
                        kvClient.putValueWithKeepAlive(client,
                                                       "$idPath/$id",
                                                       "$id ${hostInfo.first} [${hostInfo.second}] $version $startTime",
                                                       2) {
                            keepAliveLatch.await()
                        }
                    }
                }
            }

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondWith("Version $version")
                        }
                        get("/id") {
                            call.respondWith("Id: $id $hostInfo")
                        }
                        get("/clients") {
                            val data =
                                cache.currentData
                                    .map { it.value.asString }.sorted()
                                    .mapIndexed { i, s -> "${i + 1}) $s" }
                            call.respondWith("${data.size} clients:\n${data.joinToString("\n")}\n\nReported by: $id ${hostInfo.first} [${hostInfo.second}]")
                        }
                        get("/ping") {
                            var msg = "Success";
                            try {
                                connectToEtcd(urls) { client ->
                                    client.withKvClient { kvClient ->
                                        kvClient.getValue(path, "")
                                    }
                                }
                            } catch (e: Throwable) {
                                msg = e.stackTraceAsString
                            }
                            call.respondWith("Ping result: $msg")
                        }
                        get("/set") {
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kvClient.putValue(path, "This is a test")
                                }
                            }
                            call.respondWith("Key $path set")
                        }
                        get("/get") {
                            var kval = "";
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kval = kvClient.getValue(path, "key $path not found")
                                }
                            }
                            call.respondWith("Key value = $kval")
                        }
                        get("/delete") {
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kvClient.delete(path)
                                }
                            }
                            call.respondWith("Key $path deleted")
                        }
                        get("/count") {
                            val cnt = DistributedAtomicLong(urls, path).get()
                            call.respondWith("Count = $cnt")
                        }
                        get("/keys") {
                            var keys = ""
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    keys = kvClient.getChildrenKeys("/").sorted().joinToString("\n")
                                }
                            }
                            call.respondWith("Keys:\n$keys")
                        }
                        get("/terminate") {
                            thread {
                                sleep(1.seconds)
                                endCounter.set(true)
                                cache.close()
                                keepAliveLatch.countDown()
                                finishLatch.countDown()
                            }
                            val msg = "Terminating client $id $hostInfo"
                            logger.info(msg)
                            call.respondWith(msg)
                        }
                    }
                }

            httpServer.start(wait = false)

            finishLatch.await()

            logger.info("Shutting down client $id $hostInfo")
        }
    }
}