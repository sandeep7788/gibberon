package org.wBHARATmeet.adapters.messaging

import org.wBHARATmeet.model.realms.Message

interface AudibleInteraction {
    fun onSeek(message:Message,progress:Int,max:Int)
}