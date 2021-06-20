package org.wBHARATmeet.adapters.messaging.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.wBHARATmeet.R
import org.wBHARATmeet.adapters.messaging.holders.base.BaseReceivedHolder
import org.wBHARATmeet.common.extensions.setHidden
import org.wBHARATmeet.model.constants.DownloadUploadStat
import org.wBHARATmeet.model.realms.Message
import org.wBHARATmeet.model.realms.User
import org.wBHARATmeet.utils.Util

class ReceivedFileHolder(context: Context, itemView: View) : BaseReceivedHolder(context, itemView) {

    private val fileIcon: ImageView
    private val tvFileName: TextView
    private val tvFileExtension: TextView
    private val tvFileSize: TextView

    override fun bind(message: Message, user: User) {
        super.bind(message, user)
        //get file extension
        val fileExtension = Util.getFileExtensionFromPath(message.metadata).toUpperCase()
        tvFileExtension.text = fileExtension
        //set file name
        tvFileName.text = message.metadata

        //file size
        tvFileSize.text = message.fileSize

        fileIcon.setHidden(message.downloadUploadStat != DownloadUploadStat.SUCCESS, true)


    }

    init {
        fileIcon = itemView.findViewById(R.id.file_icon)
        tvFileName = itemView.findViewById(R.id.tv_file_name)
        tvFileExtension = itemView.findViewById(R.id.tv_file_extension)
        tvFileSize = itemView.findViewById(R.id.tv_file_size)
    }


}

