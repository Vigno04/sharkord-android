package com.sharkord.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicInteger

// encapsulates the tRPC-over-WebSocket protocol
// the Sharkord server uses @trpc/server with a WebSocket adapter
// the protocol works as follows:
// 1. Client connects with `?connectionParams=1` query param
// 2. First message must be: `{ "data": { "token": "<jwt>" } }` — this is read
// by both the tRPC connectionParams handler AND the server's custom
// `ws.once('message')` listener to set the ws.token
// 3. After auth, client sends tRPC queries/mutations/subscriptions as JSON:
// `{ "id": N, "method": "query"|"mutation"|"subscription", "params": { "path": "...", "input": {...} } }`
// 4. Server responds with:
// `{ "id": N, "result": { "type": "data", "data": {...} } }` for queries/mutations
// `{ "id": N, "result": { "type": "data", "data": {...} } }` for subscription events
// `{ "id": N, "result": { "type": "stopped" } }` when subscription ends
object TrpcProtocol {
    private const val TAG = "TrpcProtocol"
    private val gson = Gson()
    private val nextId = AtomicInteger(1)

    // resets the ID counter. Call when establishing a new connection
    fun resetIdCounter() {
        nextId.set(1)
    }

    // returns the next unique message ID and increments the counter
    fun getNextId(): Int = nextId.getAndIncrement()

    // builds the initial auth payload that must be sent as the first message
    // this satisfies both the tRPC connectionParams and the server's ws.once('message') handler
    fun buildAuthPayload(token: String): String {
        val payload = JsonObject().apply {
            add("data", JsonObject().apply {
                addProperty("token", token)
            })
        }
        return gson.toJson(payload)
    }

    // builds a tRPC query message
    fun buildQuery(id: Int, path: String, input: com.google.gson.JsonElement = JsonObject()): String {
        val message = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", "query")
            add("params", JsonObject().apply {
                addProperty("path", path)
                add("input", input)
            })
        }
        return gson.toJson(message)
    }

    // builds a tRPC mutation message
    fun buildMutation(id: Int, path: String, input: com.google.gson.JsonElement): String {
        val message = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", "mutation")
            add("params", JsonObject().apply {
                addProperty("path", path)
                add("input", input)
            })
        }
        return gson.toJson(message)
    }

    // builds a tRPC subscription message
    fun buildSubscription(id: Int, path: String, input: com.google.gson.JsonElement? = null): String {
        val message = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", "subscription")
            add("params", JsonObject().apply {
                addProperty("path", path)
                if (input != null) {
                    add("input", input)
                } else {
                    add("input", JsonObject())
                }
            })
        }
        return gson.toJson(message)
    }

    // builds a tRPC subscription unsubscribe message
    fun buildUnsubscribe(id: Int): String {
        val message = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", "subscription.stop")
        }
        return gson.toJson(message)
    }

    // parses an incoming WebSocket text message into a [TrpcResponse]
    fun parseResponse(text: String): TrpcResponse {
        // tRPC v11 string-based keepAlive
        if (text == "PING") return TrpcResponse.Ping(null)
        if (text == "PONG") return TrpcResponse.Pong

        return try {
            val root = JsonParser.parseString(text).asJsonObject

            // 1. Check for application-level ping/pong method first to bypass id checks
            if (root.has("method")) {
                val method = root.get("method").asString
                if (method == "ping") {
                    val pingId = if (root.has("id") && !root.get("id").isJsonNull) root.get("id").asInt else null
                    return TrpcResponse.Ping(pingId)
                } else if (method == "pong") {
                    return TrpcResponse.Pong
                }
            }

            // 2. Standard tRPC response with a valid, non-null ID
            if (root.has("id") && !root.get("id").isJsonNull) {
                val id = root.get("id").asInt

                if (root.has("result")) {
                    val result = root.getAsJsonObject("result")

                    // check if this is a subscription event vs a regular response
                    val type = result.get("type")?.asString
                    val data = result.get("data")

                    return when (type) {
                        "started" -> TrpcResponse.SubscriptionStarted(id)
                        "stopped" -> TrpcResponse.SubscriptionComplete(id)
                        "data" -> {
                            when {
                                data != null && data.isJsonObject ->
                                    TrpcResponse.Success(id, data.asJsonObject)
                                data != null ->
                                    // wrap non-object data (e.g., a number or string) in an object
                                    TrpcResponse.Success(id, JsonObject().apply {
                                        add("value", data)
                                    })
                                else ->
                                    TrpcResponse.Success(id, JsonObject())
                            }
                        }
                        else -> {
                            // no type field — older format, treat as success with data at result level
                            if (result.has("data")) {
                                val d = result.getAsJsonObject("data")
                                TrpcResponse.Success(id, d)
                            } else {
                                TrpcResponse.Success(id, result)
                            }
                        }
                    }
                }

                if (root.has("error")) {
                    val errorJson = root.getAsJsonObject("error")
                    val message = errorJson.get("message")?.asString ?: "Unknown error"
                    val code = errorJson.get("code")?.asString
                    return TrpcResponse.Error(id, TrpcError(message, code))
                }
            }

            // top-level error without id
            if (root.has("error")) {
                val errorJson = root.getAsJsonObject("error")
                val message = errorJson.get("message")?.asString ?: "Unknown error"
                val code = errorJson.get("code")?.asString
                return TrpcResponse.Error(null, TrpcError(message, code))
            }

            TrpcResponse.Unknown(text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tRPC response: ${e.message}")
            TrpcResponse.Unknown(text)
        }
    }
}

// parsed tRPC response from the server
// note: tRPC sends both one-shot query/mutation responses AND subscription push events
// with `"type":"data"`, so both map to [Success]. Subscription routing is done in
// [WebSocketManager] by checking [WebSocketManager.activeSubscriptions] — the id
// determines whether a Success is a one-shot response or a real-time push
sealed class TrpcResponse {
    // successful query/mutation response, or a subscription push event (discriminated by id)
    data class Success(val id: Int, val data: JsonObject) : TrpcResponse()

    // error response
    data class Error(val id: Int?, val error: TrpcError) : TrpcResponse()

    // subscription successfully started (server sent `"type":"started"`)
    data class SubscriptionStarted(val id: Int) : TrpcResponse()

    // subscription completed/stopped (server sent `"type":"stopped"`)
    data class SubscriptionComplete(val id: Int) : TrpcResponse()

    // ping request from the server (e.g., `{"id":123,"method":"ping"}`)
    data class Ping(val id: Int?) : TrpcResponse()

    // pong response from the server
    object Pong : TrpcResponse()

    // unrecognized message format
    data class Unknown(val rawText: String) : TrpcResponse()
}

// tRPC error details
data class TrpcError(
    val message: String,
    val code: String? = null
)
