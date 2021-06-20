package org.wBHARATmeet.adapters.messaging.holders

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vanniktech.emoji.EmojiTextView
import org.wBHARATmeet.R
import org.wBHARATmeet.adapters.messaging.holders.base.BaseReceivedHolder
import org.wBHARATmeet.model.realms.Message
import org.wBHARATmeet.model.realms.User

// received message with type text
class ReceivedTextHolder(context: Context, itemView: View) : BaseReceivedHolder(context,itemView) {

    private var tvMessageContent: EmojiTextView = itemView.findViewById(R.id.tv_message_content)
    private var image_view_chat_profile: ImageView = itemView.findViewById(R.id.image_view_chat_profile)
    var TAG="@@ReceivedTextHolder"

    override fun bind(message: Message,user: User) {
        super.bind(message,user)
        tvMessageContent.text = message.content

        if (user.userLocalPhoto != null) {
            Glide.with(context).load(user.userLocalPhoto).into(image_view_chat_profile)
        } else {
            Glide.with(context).load(user.thumbImg).into(image_view_chat_profile)
        }

    }
}