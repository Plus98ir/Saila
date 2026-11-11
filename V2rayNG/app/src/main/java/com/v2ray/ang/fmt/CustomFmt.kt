package com.v2ray.ang.fmt

import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

const val SAILA_MASQUE = "masque"
const val SAILA_XDRIVE = "xdrive"

object CustomFmt : FmtBase() {
    /**
     * Parses a JSON string into a ProfileItem object.
     *
     * @param str the JSON string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        // Saila: MASQUE is not an EConfigType, so its outbound is looked up by protocol name.
        val outbound = fullConfig?.getProxyOutbound()
            ?: fullConfig?.outbounds?.firstOrNull { it.protocol.equals(SAILA_MASQUE, true) }

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()
        // Saila: MASQUE / XDRIVE rows show their own name instead of "CUSTOM".
        config.network = outbound?.streamSettings?.network
            ?.takeIf { it.equals(SAILA_MASQUE, true) || it.equals(SAILA_XDRIVE, true) }

        return config
    }
}