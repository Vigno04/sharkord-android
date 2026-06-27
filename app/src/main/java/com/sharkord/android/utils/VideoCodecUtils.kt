package com.sharkord.android.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

object VideoCodecUtils {

    fun getSupportedHardwareEncoders(): List<String> {
        val supportedCodecs = mutableListOf<String>()
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        
        var hasH264 = false
        var hasHEVC = false
        var hasAV1 = false

        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            
            // We only want hardware accelerated encoders for optimal performance and battery
            val isHardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                codecInfo.isHardwareAccelerated
            } else {
                val name = codecInfo.name.lowercase()
                !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains("sw.")
            }
            
            if (!isHardwareAccelerated) continue
            
            val types = codecInfo.supportedTypes
            if (types.contains("video/av01") || types.contains("video/av1x") || types.contains("video/av1")) hasAV1 = true
            if (types.contains("video/hevc")) hasHEVC = true
            if (types.contains("video/avc")) hasH264 = true
        }

        // Order by efficiency / modern standard
        if (hasAV1) supportedCodecs.add("AV1")
        if (hasHEVC) supportedCodecs.add("HEVC")
        if (hasH264) supportedCodecs.add("H.264")
        
        // Fallback if no hardware encoders found, software fallback might still work for these
        if (supportedCodecs.isEmpty()) {
            supportedCodecs.addAll(listOf("AV1", "HEVC", "H.264"))
        }

        return supportedCodecs
    }
}
