package com.sharkord.android.ui.emojipicker.domain.model
import com.sharkord.android.R

import com.google.gson.annotations.SerializedName

data class Emoji(
    @SerializedName("key") val id: String,
    @SerializedName("emoji") val emoji: String,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("category") val category: String
)
