package com.sharkord.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicInteger

/**
 * Encapsulates the tRPC-over-WebSocket protocol.
 *
 * The Sharkord server uses @trpc/server with a WebSocket adapter.
 * The protocol works as follows:
 *
 * 1. Client connects with `?connectionParams=1` query param.
 * 2. First message must be: `{ "data": { "token": "<jwt>" } }` — this is read
 *    by both the tRPC connectionParams handler AND the server's custom
 *    `ws.once('message')` listener to set the ws.token.
 * 3. After auth, client sends tRPC queries/mutations/subscriptions as JSON:
 *    `{ "id": N, "method": "query"|"mutation"|"subscription", "params": { "path": "...", "input": {...} } }`
 * 4. Server responds with:
 *    `{ "id": N, "result": { "type": "data", "data": {...} } }` for queries/mutations
 *    `{ "id": N, "result": { "type": "data", "data": {...} } }` for subscription events
 *    `{ "id": N, "result": { "type": "stopped" } }` when subscription ends
 */
object TrpcProtocol {
    private const val TAG = "TrpcProtocol"
    private val gson = Gson()
    private val nextId = AtomicInteger(1)

    /**
     * Resets the ID counter. Call when establishing a new connection.
     */
    fun resetIdCounter() {
        nextId.set(1)
    }

    /**
     * Returns the next unique message ID and increments the counter.
     */
    fun getNextId(): Int = nextId.getAndIncrement()

    /**
     * Builds the initial auth payload that must be sent as the first message.
     * This satisfies both the tRPC connectionParams and the server's ws.once('message') handler.
     */
    fun buildAuthPayload(token: String): String {
        val payload = JsonObject().apply {
            add("data", JsonObject().apply {
                addProperty("token", token)
            })
        }
        return gson.toJson(payload)
    }

    /**
     * Builds a tRPC query message.
     */
    fun buildQuery(id: Int, path: String, input: JsonObject = JsonObject()): String {
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

    /**
     * Builds a tRPC mutation message.
     */
    fun buildMutation(id: Int, path: String, input: JsonObject): String {
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

    /**
     * Builds a tRPC subscription message.
     */
    fun buildSubscription(id: Int, path: String, input: JsonObject? = null): String {
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

    /**
     * Parses an incoming WebSocket text message into a [TrpcResponse].
     */
    fun parseResponse(text: String): TrpcResponse {
        return try {
            val root = JsonParser.parseString(text).asJsonObject

            if (root.has("id")) {
                val id = root.get("id").asInt

                if (root.has("result")) {
                    val result = root.getAsJsonObject("result")

                    // Check if this is a subscription event vs a regular response
                    val type = result.get("type")?.asString
                    val data = result.get("data")

                    return when (type) {
                        "stopped" -> TrpcResponse.SubscriptionComplete(id)
                        "data" -> {
                            when {
                                data != null && data.isJsonObject ->
                                    TrpcResponse.Success(id, data.asJsonObject)
                                data != null ->
                                    // Wrap non-object data (e.g., a number or string) in an object
                                    TrpcResponse.Success(id, JsonObject().apply {
                                        add("value", data)
                                    })
                                else ->
                                    TrpcResponse.Success(id, JsonObject())
                            }
                        }
                        else -> {
                            // No type field — older format, treat as success with data at result level
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

            // Top-level error without id
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

/**
 * Parsed tRPC response from the server.
 */
sealed class TrpcResponse {
    /** Successful query/mutation response. */
    data class Success(val id: Int, val data: JsonObject) : TrpcResponse()

    /** Error response. */
    data class Error(val id: Int?, val error: TrpcError) : TrpcResponse()

    /** Subscription data event. */
    data class SubscriptionData(val id: Int, val data: JsonObject) : TrpcResponse()

    /** Subscription completed/stopped. */
    data class SubscriptionComplete(val id: Int) : TrpcResponse()

    /** Unrecognized message format. */
    data class Unknown(val rawText: String) : TrpcResponse()
}

/**
 * tRPC error details.
 */
data class TrpcError(
    val message: String,
    val code: String? = null
)
