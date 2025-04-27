package com.veshikov.yousify.auth

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

class LocalAuthServer(
    private val port: Int = 8888,
    private val onCodeReceived: (String) -> Unit
) {
    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(CIO, port) {
            routing {
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    if (code != null) {
                        // Ответ пользователю в браузере
                        call.respondText(
                            "<html><body><h3>Авторизация прошла успешно.<br/>Вернитесь в приложение.</h3></body></html>",
                            ContentType.Text.Html
                        )
                        onCodeReceived(code)
                    } else {
                        call.respondText("Missing code", ContentType.Text.Plain)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 1000)
    }
}