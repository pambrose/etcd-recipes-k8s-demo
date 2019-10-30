package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import io.etcd.recipes.cache.PathChildrenCache
import io.etcd.recipes.common.*
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
import java.io.PrintWriter
import java.io.StringWriter
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.seconds

val Throwable.stackTraceAsString: String
    get() {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        printStackTrace(pw)
        return sw.toString()
    }

val hostInfo by lazy {
    try {
        val hostname = InetAddress.getLocalHost().hostName!!
        val address = InetAddress.getLocalHost().hostAddress!!
        //logger.debug { "Hostname: $hostname Address: $address" }
        Pair(hostname, address)
    } catch (e: UnknownHostException) {
        Pair("Unknown", "Unknown")
    }
}

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
            val urls = listOf("http://etcd-client:2379")
            val path = "/testkey"
            val idPath = "/clients"
            val id = randomId()
            val keepAliveLatch = CountDownLatch(1)
            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")

            val cache = PathChildrenCache(urls, idPath)
            cache.start(true)

            val httpServer =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondWith("index.html requested here 1.0.3")
                        }
                        get("/id") {
                            call.respondWith("Id: $id")
                        }
                        get("/clients") {
                            val data = cache.currentData.map { it.key }
                            call.respondWith("Clients: $data")
                        }
                        get("/testurl") {
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
                            call.respondWith("Test result: $msg")
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
                        get("/hostinfo") {
                            call.respondWith(hostInfo.toString())
                        }
                        get("/terminate") {
                            cache.close()
                            keepAliveLatch.countDown()
                            System.exit(1)
                        }
                    }
                }

            thread {
                val msg: String
                try {
                    DistributedAtomicLong(urls, path)
                        .use { counter ->
                            while (true) {
                                counter.increment()
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
                        kvClient.putValueWithKeepAlive(client, "$idPath/$id", id, 2) {
                            keepAliveLatch.await()
                        }
                    }
                }
            }

            httpServer.start(wait = true)
        }
    }
}