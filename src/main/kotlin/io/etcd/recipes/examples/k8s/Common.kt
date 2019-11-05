package io.etcd.recipes.examples.k8s

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respondText

const val TZ = "America/Los_Angeles"
const val FMT = "M/d/y H:m:ss"
const val clientPath = "/clients"
const val electionPath = "/election/k8s-demo"
const val msgPath = "/msgs/leader"
const val pingPath = "/msgs/ping"
const val counterPath = "/counter/k8s-demo"
const val keepAliveTtl = 10L

val urls = listOf("http://etcd-client:2379", "http://localhost:2379")

suspend fun ApplicationCall.respondWith(str: String, contentType: ContentType = ContentType.Text.Plain) {
    apply {
        response.header("cache-control", "must-revalidate,no-cache,no-store")
        response.status(HttpStatusCode.OK)
        respondText(str, contentType)
    }
}