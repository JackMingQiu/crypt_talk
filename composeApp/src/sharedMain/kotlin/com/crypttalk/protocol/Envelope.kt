package com.crypttalk.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Envelope(
    val v: Int = 1,
    val type: String,
    val from: String? = null,
    val pk: String? = null,
    val eph: String? = null,
    val sig: String? = null,
    val sid: String? = null,
    val seq: Long? = null,
    val nonce: String? = null,
    val ct: String? = null
)
