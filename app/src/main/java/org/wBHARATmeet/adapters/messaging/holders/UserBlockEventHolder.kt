package org.wBHARATmeet.adapters.messaging.holders

import android.content.Context
import android.view.View
import android.widget.TextView
import org.wBHARATmeet.R
import org.wBHARATmeet.adapters.messaging.holders.base.BaseSentHolder
import org.wBHARATmeet.interfaces.OnClickListener

class UserBlockEventHolder (context: Context, itemView: View,var mOnClickListener: OnClickListener) : BaseSentHolder(context,itemView) {

    private val tv_user_blocked_msg: TextView = itemView.findViewById(R.id.tv_user_blocked_msg)


    fun bind(status:Boolean) {

        if (status) tv_user_blocked_msg.visibility = View.VISIBLE else tv_user_blocked_msg.visibility = View.GONE

        tv_user_blocked_msg.setOnClickListener {
            mOnClickListener.onClick(true)
        }
    }

}