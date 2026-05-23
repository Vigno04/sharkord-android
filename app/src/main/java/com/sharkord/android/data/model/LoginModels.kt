package com.sharkord.android.data.model

import com.google.gson.annotations.SerializedName

// HTTP Login Request
data class LoginRequest(
    val identity: String,
    val password: String
)

// HTTP Login Response
data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val error: String?,
    val errors: Map<String, String>?
)

// tRPC Channel Model
data class Channel(
    val id: Int,
    val name: String,
    val isDm: Boolean,
    val isVoice: Boolean
)

// tRPC User Model
data class User(
    val id: Int,
    val name: String
)

// tRPC Response for joinServer
data class JoinServerData(
    val ownUserId: Int,
    val serverName: String,
    val channels: List<Channel>,
    val users: List<User>
)

data class ServerLogo(
    val id: Int,
    val name: String,
    val originalName: String,
    val mimeType: String
)

data class ServerInfoResponse(
    val serverId: String,
    val version: String,
    val name: String,
    val description: String?,
    val logo: ServerLogo?,
    val allowNewUsers: Boolean
)

