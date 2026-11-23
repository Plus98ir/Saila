package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

internal object ProfileReplacement {

    /**
     * Finds the profile that should become selected after publishing a replacement batch.
     * The first profile becomes selected when the store has no current selection.
     */
    fun findSelectedReplacement(
        profiles: Map<String, ProfileItem>,
        currentSelection: String?,
        selectedProfile: ProfileItem?,
    ): String? {
        if (profiles.isEmpty()) return null
        if (currentSelection.isNullOrBlank()) return profiles.keys.first()
        if (selectedProfile == null) return null

        if (selectedProfile.remarks.isNotBlank()) {
            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks) &&
                        isSameText(candidate.server, selectedProfile.server) &&
                        isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                        isSameText(candidate.password, selectedProfile.password)
            }?.key?.let { return it }

            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks)
            }?.key?.let { return it }
        }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                    isSameText(candidate.password, selectedProfile.password)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server)
        }?.key?.let { return it }

        return profiles.keys.firstOrNull()
    }

    /**
     * Finds replaced payloads that are safe to remove.
     *
     * A null cross-group reference set means that at least one raw group index could not
     * be read. In that case deletion fails closed.
     */
    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServer: String?,
        serversReferencedByOtherGroups: Set<String>?,
    ): Set<String> {
        if (serversReferencedByOtherGroups == null) return emptySet()

        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid != protectedServer &&
                    guid !in replacementServers &&
                    guid !in serversReferencedByOtherGroups
        }
    }

    /**
     * Saila: a subscription update stores every server again under a new GUID. Pairs each new
     * profile with the old profile of the same server (same settings; the name is compared first,
     * then ignored, since panels put the remaining traffic in it; last, same type, address, port and
     * name, since 3X-UI picks a random Reality shortId per request), so its last ping can be kept.
     * Each old profile is used once.
     *
     * @return new GUID to old GUID.
     */
    fun findSameServers(
        oldProfiles: Map<String, ProfileItem>,
        newProfiles: Map<String, ProfileItem>,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val unused = oldProfiles.toMutableMap()
        val keys: List<(ProfileItem) -> Any?> = listOf(
            { serverKey(it, withRemarks = true) },
            { serverKey(it, withRemarks = false) },
            { p -> p.remarks.trim().takeIf { it.isNotEmpty() }?.let { listOf(p.configType, p.server, p.serverPort, it) } },
        )
        for (key in keys) {
            val byKey = unused.entries.filter { key(it.value) != null }
                .groupBy({ key(it.value) }, { it.key })
                .mapValues { it.value.toMutableList() }
            newProfiles.forEach { (newGuid, profile) ->
                if (newGuid in result) return@forEach
                val k = key(profile) ?: return@forEach
                val oldGuid = byKey[k]?.removeFirstOrNull() ?: return@forEach
                result[newGuid] = oldGuid
                unused.remove(oldGuid)
            }
        }
        return result
    }

    private fun serverKey(profile: ProfileItem, withRemarks: Boolean): ProfileItem =
        profile.copy(
            subscriptionId = "",
            addedTime = 0L,
            remarks = if (withRemarks) profile.remarks.trim() else "",
            description = null,
            sailaManual = false,
        )

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
