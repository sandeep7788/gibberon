/*
 * Created by Devlomi on 2021
 */

package org.wBHARATmeet

import android.content.Context
import android.view.View
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.EmojiUtils
import org.wBHARATmeet.adapters.messaging.holders.base.BaseSentHolder
import org.wBHARATmeet.model.realms.Message
import org.wBHARATmeet.model.realms.User

class DisappearHolder (context: Context, itemView: View) : BaseSentHolder(context,itemView) {

    override fun bind(message: Message, user: User) {
        super.bind(message,user)


    }

}