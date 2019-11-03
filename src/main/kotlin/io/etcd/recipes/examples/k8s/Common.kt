package io.etcd.recipes.examples.k8s

import io.etcd.jetcd.KV
import io.etcd.recipes.common.connectToEtcd
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.withKvClient
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respondText
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

const val TZ = "America/Los_Angeles"
const val FMT = "M/d/y H:m:ss"
const val clientPath = "/clients"
const val electionPath = "/election/k8s-demo"
const val msgPath = "/msgs/leader"
const val pingPath = "/msgs/ping"
const val counterPath = "/counter/k8s-demo"

val urls = listOf("http://etcd-client:2379", "http://localhost:2379")

suspend fun ApplicationCall.respondWith(str: String, contentType: ContentType = ContentType.Text.Plain) {
    apply {
        response.header("cache-control", "must-revalidate,no-cache,no-store")
        response.status(HttpStatusCode.OK)
        respondText(str, contentType)
    }
}

fun etcdExec(urls: List<String>, block: (kvClient: KV) -> Unit) {
    connectToEtcd(urls) { client ->
        client.withKvClient { kvClient ->
            block(kvClient)
        }
    }
}

fun addKeepAliveClient(id: String, desc: String): CountDownLatch {
    val keepAliveLatch = CountDownLatch(1)
    thread {
        connectToEtcd(urls) { client ->
            client.withKvClient { kvClient ->
                kvClient.putValueWithKeepAlive(client, "$clientPath/$id", desc, 2) {
                    keepAliveLatch.await()
                }
            }
        }
    }
    return keepAliveLatch
}

