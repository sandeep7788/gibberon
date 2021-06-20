package org.wBHARATmeet.adapters.messaging.holders

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.wBHARATmeet.R
import org.wBHARATmeet.adapters.messaging.ContactHolderBase
import org.wBHARATmeet.adapters.messaging.ContactHolderInteraction
import org.wBHARATmeet.adapters.messaging.holders.base.BaseSentHolder
import org.wBHARATmeet.model.realms.Message
import org.wBHARATmeet.model.realms.User

class SentContactHolder(context: Context, itemView: View) : BaseSentHolder(context,itemView),ContactHolderBase {

    private val tvContactName: TextView = itemView.findViewById(R.id.tv_contact_name)
    private val btnMessageContact: Button = itemView.findViewById(R.id.btn_message_contact)

    override var contactHolderInteraction: ContactHolderInteraction? = null

    override fun bind(message: Message,user: User) {
        super.bind(message,user)
        //set contact name
        tvContactName.text = message.content


        //send a message to this contact if installed this app
        btnMessageContact.setOnClickListener {
            contactHolderInteraction?.onMessageClick(message.contact)
        }

    }



}

