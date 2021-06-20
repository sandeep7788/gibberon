package org.wBHARATmeet.adapters.messaging

import org.wBHARATmeet.model.realms.RealmContact

interface ContactHolderInteraction {
    fun onMessageClick(contact:RealmContact)
    fun onAddContactClick(contact:RealmContact)
}