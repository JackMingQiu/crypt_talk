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

/** 加密前的消息明文结构：发送者信息放在密文内部，解密后再校验 */
@Serializable
data class MessagePayload(
    val from: String,
    val pk: String,
    val text: String
)
