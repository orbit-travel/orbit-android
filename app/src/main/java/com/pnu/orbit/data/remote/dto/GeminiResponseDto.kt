package com.pnu.orbit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent?
)
