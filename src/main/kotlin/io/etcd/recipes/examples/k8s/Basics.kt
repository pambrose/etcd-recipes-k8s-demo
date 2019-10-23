package io.etcd.recipes.examples.k8s

import com.sudothought.common.util.randomId
import com.sudothought.common.util.sleep
import io.etcd.recipes.common.*
import io.etcd.recipes.counter.DistributedAtomicLong
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import mu.KLogging
import java.io.PrintWriter
import java.io.StringWriter
import java.net.InetAddress
import java.net.UnknownHostException
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

class Basics {
    companion object : KLogging() {
        @JvmStatic
        fun main(args: Array<String>) {
            val urls = listOf("http://etcd0:2379")
            val path = "/testkey"
            val id = randomId()

            val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
            val httpServer: CIOApplicationEngine =
                embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondText("index.html requested", ContentType.Text.Plain)
                        }
                        get("/id") {
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Id = $id", ContentType.Text.Plain)
                            }
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
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Test result: $msg", ContentType.Text.Plain)
                            }
                        }
                        get("/set") {
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kvClient.putValue(path, "This is a test")
                                }
                            }
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Key $path set", ContentType.Text.Plain)
                            }
                        }
                        get("/get") {
                            var kval = "";
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kval = kvClient.getValue(path, "key $path not found")
                                }
                            }
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Key value = $kval", ContentType.Text.Plain)
                            }
                        }
                        get("/delete") {
                            connectToEtcd(urls) { client ->
                                client.withKvClient { kvClient ->
                                    kvClient.delete(path)
                                }
                            }
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Key $path deleted", ContentType.Text.Plain)
                            }
                        }
                        get("/count") {
                            val cnt = DistributedAtomicLong(urls, path).get()
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText("Count = $cnt", ContentType.Text.Plain)
                            }
                        }
                        get("/hostinfo") {
                            val cnt = DistributedAtomicLong(urls, path).get()
                            call.apply {
                                response.header("cache-control", "must-revalidate,no-cache,no-store")
                                response.status(HttpStatusCode.OK)
                                respondText(hostInfo.toString(), ContentType.Text.Plain)
                            }
                        }
                        get("/terminate") {
                            System.exit(1)
                        }
                    }
                }

            thread {
                var msg = "";
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
                }
            }

            httpServer.start(wait = true)
        }
    }
}