package com.kieronquinn.app.pcs.model

import androidx.annotation.StringRes
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.ClientGroup
import com.kieronquinn.app.pcs.R

enum class ClientGroupOverride(val clientGroup: ClientGroup?, @StringRes val title: Int) {
    DISABLED(null, R.string.client_group_override_disabled),
    ALL(ClientGroup.ALL, R.string.client_group_override_all),
    ALPHA(ClientGroup.ALPHA, R.string.client_group_override_alpha),
    BETA(ClientGroup.BETA, R.string.client_group_override_beta),
    THIRD_PARTY_EAP(ClientGroup.THIRD_PARTY_EAP, R.string.client_group_override_third_party_eap),
    THIRD_PARTY_EXPERIMENTAL(ClientGroup.THIRD_PARTY_EXPERIMENTAL, R.string.client_group_override_third_party_experiental);

    companion object {
        fun from(value: String?): ClientGroupOverride {
            return entries.firstOrNull { it.name == value } ?: DISABLED
        }
    }
}