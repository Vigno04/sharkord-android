package com.sharkord.android.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sharkord.android.data.model.MessagesPage
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.network.TrpcCallException
import com.sharkord.android.data.network.WebSocketNotConnectedException

/**
 * Repository for channel message operations.
 *
 * All calls go through the existing tRPC WebSocket connection managed by [SharkordClient.webSocket].
 * Uses the awaitable query/mutation helpers added to [WebSocketManager] so callers get
 * clean suspend functions instead of managing message IDs manually.
 */
class ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val DEFAULT_LIMIT = 50
    }

    private val webSocket get() = SharkordClient.webSocket
    private val gson = Gson()

    // ─── Public API ───────────────────────────────────────────

    /**
     * Fetches a page of messages for [channelId].
     *
     * @param channelId  The channel to load messages for.
     * @param cursor     Timestamp cursor for pagination (from [MessagesPage.nextCursor]).
     *                   Pass `null` to fetch the newest page.
     * @return [Result.success] with [MessagesPage] on success, [Result.failure] on error.
     */
    suspend fun getMessages(channelId: Int, cursor: Long? = null): Result<MessagesPage> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
                addProperty("limit", DEFAULT_LIMIT)
                if (cursor != null) addProperty("cursor", cursor)
            }
            val response = webSocket.sendQueryAwait("messages.get", input)
            val page = gson.fromJson(response, MessagesPage::class.java)
            Log.d(TAG, "getMessages: channelId=$channelId, count=${page.messages.size}, nextCursor=${page.nextCursor}")
            Result.success(page)
        } catch (e: TrpcCallException) {
            Log.e(TAG, "getMessages tRPC error: ${e.message} (${e.code})")
            Result.failure(e)
        } catch (e: WebSocketNotConnectedException) {
            Log.e(TAG, "getMessages: WebSocket not connected")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "getMessages: unexpected error", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a plain-text message to [channelId].
     *
     * @return [Result.success] with the new message id on success.
     */
    suspend fun sendMessage(channelId: Int, content: String, replyToMessageId: Int? = null): Result<Int> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
                addProperty("content", content)
                if (replyToMessageId != null) {
                    addProperty("replyToMessageId", replyToMessageId)
                }
            }
            val response = webSocket.sendMutationAwait("messages.send", input)
            // Server returns the new message id as a bare integer wrapped in {"value": <id>}
            // by TrpcProtocol.parseResponse (non-object scalar wrapping).
            val messageId = response.get("value")?.asInt
                ?: response.entrySet().firstOrNull()?.value?.asInt
                ?: error("sendMessage: unexpected response format: $response")
            Log.d(TAG, "sendMessage: sent to channelId=$channelId, messageId=$messageId")
            Result.success(messageId)
        } catch (e: TrpcCallException) {
            Log.e(TAG, "sendMessage tRPC error: ${e.message} (${e.code})")
            Result.failure(e)
        } catch (e: WebSocketNotConnectedException) {
            Log.e(TAG, "sendMessage: WebSocket not connected")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: unexpected error", e)
            Result.failure(e)
        }
    }
}
