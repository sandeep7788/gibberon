package org.wBHARATmeet.adapters.messaging.holders.base

import android.content.Context
import android.view.View
import android.widget.ImageView
import org.wBHARATmeet.R
import org.wBHARATmeet.model.realms.Message
import org.wBHARATmeet.model.realms.User
import org.wBHARATmeet.utils.AdapterHelper

open class BaseSentHolder(context: Context, itemView: View) : BaseHolder(context,itemView) {

    var messageStatImg:ImageView? = itemView.findViewById(R.id.message_stat_img)


    override fun bind(message: Message, user: User) {
        super.bind(message, user)


        //imgStat (received or read)
        messageStatImg?.setImageResource(AdapterHelper.getMessageStatDrawable(message.messageStat))


    }




}

