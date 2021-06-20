package org.wBHARATmeet.adapters.messaging

import androidx.lifecycle.LiveData
import org.wBHARATmeet.model.AudibleState

interface AudibleBase {
    var audibleState: LiveData<Map<String, AudibleState>>?
    var audibleInteraction:AudibleInteraction?
}