package org.wBHARATmeet.activities.main.status

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cjt2325.cameralibrary.ResultCodes
import com.devlomi.circularstatusview.CircularStatusView
import com.droidninja.imageeditengine.ImageEditor
import com.google.android.gms.ads.AdView
import com.google.firebase.database.DataSnapshot
import com.zhihu.matisse.Matisse
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.addTo
import io.realm.*
import io.realm.kotlin.isLoaded
import io.realm.kotlin.isValid
import kotlinx.android.synthetic.main.fragment_status.*
import org.greenrobot.eventbus.EventBus
import org.wBHARATmeet.R
import org.wBHARATmeet.activities.MyStatusActivity
import org.wBHARATmeet.activities.ViewStatusActivity
import org.wBHARATmeet.activities.main.MainActivity.Companion.CAMERA_REQUEST
import org.wBHARATmeet.activities.main.MainActivity.Companion.REQUEST_CODE_TEXT_STATUS
import org.wBHARATmeet.activities.main.MainViewModel
import org.wBHARATmeet.activities.main.status.StatusFragmentEvent.OnActivityResultEvent
import org.wBHARATmeet.activities.main.status.StatusFragmentEvent.StatusInsertedEvent
import org.wBHARATmeet.adapters.StatusAdapter
import org.wBHARATmeet.events.GroupActiveStateChanged
import org.wBHARATmeet.extensions.getDownloadUrlRx
import org.wBHARATmeet.extensions.putFileRx
import org.wBHARATmeet.extensions.updateChildrenRx
import org.wBHARATmeet.fragments.BaseFragment
import org.wBHARATmeet.interfaces.StatusFragmentCallbacks
import org.wBHARATmeet.job.DeleteStatusJob
import org.wBHARATmeet.model.constants.*
import org.wBHARATmeet.model.realms.*
import org.wBHARATmeet.utils.*
import org.wBHARATmeet.utils.network.FireManager
import org.wBHARATmeet.utils.network.StatusManager
import org.wBHARATmeet.views.HeaderViewDecoration
import org.wBHARATmeet.views.TextViewWithShapeBackground
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class StatusFragment : BaseFragment(), StatusAdapter.OnClickListener {


    private lateinit var adapter: StatusAdapter
    var statusesList: RealmResults<UserStatuses>? = null
    private var myStatuses: UserStatuses? = null
    private var decor: HeaderViewDecoration? = null
    private var header1pos = 0
    private var header2pos = 0
    private var header1Title: String? = null
    private var header2Title = ""
    override var adView: AdView? = null
    private var callbacks: StatusFragmentCallbacks? = null
    private val statusManager = StatusManager()
    private val TAG = "@@StatusFragment"

    private var MAX_STATUS_VIDEO_TIME = 0
    private var TEMP_localPath = ""

    private lateinit var btnViewMyStatuses: ImageButton
    private lateinit var tvLastStatusTime: TextView
    private lateinit var tvTextStatus: TextViewWithShapeBackground
    private lateinit var circularStatusView: CircularStatusView
    private lateinit var profileImage: ImageView
    private lateinit var rowStatusContainer: ConstraintLayout


    private val viewModel: MainViewModel by activityViewModels()
    override fun showAds(): Boolean {
        return resources.getBoolean(R.bool.is_status_ad_enabled)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as StatusFragmentCallbacks

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //since we are using <include> the app sometimes crashes, to solve that we are instantiate it using findViewById
        btnViewMyStatuses = view.findViewById(R.id.btn_view_my_statuses)
        circularStatusView = view.findViewById(R.id.circular_status_view)
        tvLastStatusTime = view.findViewById(R.id.tv_last_status_time)
        tvTextStatus = view.findViewById(R.id.tv_text_status)
        rowStatusContainer = view.findViewById(R.id.row_status_container)
        profileImage = view.findViewById(R.id.profile_image)

        adView = ad_view
        adViewInitialized(adView)

        MAX_STATUS_VIDEO_TIME = resources.getInteger(R.integer.max_status_video_time)
        btnViewMyStatuses.setOnClickListener(View.OnClickListener {
            if (myStatuses == null) return@OnClickListener
            startActivity(Intent(activity, MyStatusActivity::class.java))
        })
        statusesList = RealmHelper.getInstance()?.getAllStatuses()

        Log.d(TAG, "onViewCreated: "+statusesList)

        for (i in 0 until statusesList!!.size) {
            Log.d(TAG, "onViewCreated: $i "+statusesList?.get(i)?.userId)
        }


        initMyStatuses()
        circularStatusView.visibility = View.GONE
        initAdapter()
        rowStatusContainer.setOnClickListener {
            if (myStatuses?.filteredStatuses?.isNotEmpty() == true) {
                val intent = Intent(activity, ViewStatusActivity::class.java)
                intent.putExtra(IntentUtils.UID, myStatuses?.userId)
                startActivity(intent)
            } else {
                callbacks?.openCamera()
            }
        }

        viewModel.statusLiveData.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { statusFragmentEvent ->
                when (statusFragmentEvent) {
                    is StatusInsertedEvent -> statusInserted()
                    is OnActivityResultEvent -> {
                        val requestCode = statusFragmentEvent.requestCode
                        val resultCode = statusFragmentEvent.resultCode
                        val data = statusFragmentEvent.data

                        if (requestCode == CAMERA_REQUEST) {
                            onCameraActivityResult(resultCode, data)


                        } else if (requestCode == ImageEditor.RC_IMAGE_EDITOR && resultCode == Activity.RESULT_OK) {
                            data.getStringExtra(ImageEditor.EXTRA_EDITED_PATH)?.let { imagePath ->
                                onImageEditSuccess(imagePath)
                            }

                        } else if (requestCode == REQUEST_CODE_TEXT_STATUS && resultCode == Activity.RESULT_OK) {
                            data.getParcelableExtra<TextStatus>(IntentUtils.EXTRA_TEXT_STATUS)
                                ?.let { textStatus ->
                                    onTextStatusResult(textStatus)
                                }

                        }
                    }
                }
            })

        viewModel.queryTextChange.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { newText ->
                onQueryTextChange(newText)
            })
    }

    private fun initMyStatuses() {
        myStatuses = RealmHelper.getInstance()?.getUserStatuses(FireManager.uid)
    }

    fun setMyStatus() {
        if (myStatuses == null) initMyStatuses()
        if (myStatuses != null
            && myStatuses?.filteredStatuses?.isNotEmpty() == true
        ) {
            val lastStatus = myStatuses?.statuses?.last()
            val statusTime = TimeHelper.getStatusTime(lastStatus?.timestamp ?: Date().time)
            tvLastStatusTime.text = statusTime
            btnViewMyStatuses.visibility = View.VISIBLE
            circularStatusView.visibility = View.VISIBLE
            if (lastStatus?.type == StatusType.IMAGE || lastStatus?.type == StatusType.VIDEO) {
                tvTextStatus.visibility = View.GONE
                profileImage.visibility = View.VISIBLE
                Glide.with(requireActivity()).load(lastStatus.thumbImg).into(profileImage)
            } else if (lastStatus?.type == StatusType.TEXT) {
                tvTextStatus.visibility = View.VISIBLE
                profileImage.visibility = View.GONE
                val textStatus = lastStatus.textStatus
                tvTextStatus.text = textStatus.text
                tvTextStatus.setShapeColor(
                    Color.parseColor(
                        textStatus?.backgroundColor
                            ?: "#000000"
                    )
                )
            }
        } else {
            circularStatusView.visibility = View.GONE
            tvTextStatus.visibility = View.GONE
            profileImage.visibility = View.VISIBLE
            Glide.with(requireActivity()).load(SharedPreferencesManager.getThumbImg())
                .into(profileImage)
            btnViewMyStatuses.visibility = View.GONE
            tvLastStatusTime.text = getString(R.string.tap_to_add_status)
        }
    }


    fun onCameraActivityResult(resultCode: Int, data: Intent) {
        if (resultCode != ResultCodes.CAMERA_ERROR_STATE) {
            if (resultCode == ResultCodes.IMAGE_CAPTURE_SUCCESS) {
                val path = data.getStringExtra(IntentUtils.EXTRA_PATH_RESULT)
                ImageEditorRequest.open(activity, path)
            } else if (resultCode == ResultCodes.VIDEO_RECORD_SUCCESS) {
                data.getStringExtra(IntentUtils.EXTRA_PATH_RESULT)?.let { path ->
                    uploadVideoStatus(path)
                }
            } else if (resultCode == ResultCodes.PICK_IMAGE_FROM_CAMERA) {
                val mPaths = Matisse.obtainPathResult(data)
                for (mPath in mPaths) {
                    if (!FileUtils.isFileExists(mPath)) {
                        Toast.makeText(
                            activity,
                            MyApp.context().resources.getString(R.string.image_video_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                }


                //Check if it's a video
                if (FileUtils.isPickedVideo(mPaths[0])) {

                    //check if video is longer than 30sec
                    val mediaLengthInMillis = Util.getMediaLengthInMillis(context, mPaths[0])
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(mediaLengthInMillis)
                    if (seconds <= MAX_STATUS_VIDEO_TIME) {
                        for (mPath in mPaths) {
                            uploadVideoStatus(mPath)
                        }
                    } else {
                        Toast.makeText(
                            activity,
                            MyApp.context().resources.getString(R.string.video_length_is_too_long),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    //if it's only one image open image editor
                    if (mPaths.size == 1) ImageEditorRequest.open(
                        activity,
                        mPaths[0]
                    ) else for (path in mPaths) {
                        uploadImageStatus(path)
                    }
                }
            }
        }
    }

    private fun initAdapter() {
        adapter = StatusAdapter(statusesList, true, context, this@StatusFragment)
        rv_status.layoutManager = LinearLayoutManager(context)
        rv_status.adapter = adapter
        decor = HeaderViewDecoration(context)
        decor?.let {
            rv_status.addItemDecoration(it)
        }

    }

    private fun setupHeaders() {
        header1pos = -1
        header2pos = -1
        statusesList?.let {


            for (userStatuses in it) {
                if (!userStatuses.isAreAllSeen) {
                    if (header1pos == -1) {
                        header1pos = it.indexOf(userStatuses)
                    }
                } else {
                    if (header2pos == -1) {
                        header2pos = it.indexOf(userStatuses)
                        break
                    }
                }
            }
        }
        //if the statuses are all seen,then set the header title as Viewed updates
        if (header1pos == -1) {
            header1Title = MyApp.context().resources.getString(R.string.viewed_statuses)
            header2Title = MyApp.context().resources.getString(R.string.viewed_statuses)
        } else {
            header1Title = MyApp.context().resources.getString(R.string.recent_updates)
            header2Title = MyApp.context().resources.getString(R.string.viewed_statuses)
        }
    }

    private fun uploadVideoStatus(path: String) {

        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(
                activity,
                MyApp.context().resources.getString(R.string.no_internet_connection),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        Toast.makeText(activity, R.string.uploading_status, Toast.LENGTH_SHORT).show()
        disposables.add(uploadStatus(path, StatusType.VIDEO, true).subscribe { status, throwable ->

            if (throwable != null) {
                Toast.makeText(
                    activity,
                    MyApp.context().resources.getString(R.string.error_uploading_status),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                setMyStatus()
                Toast.makeText(
                    activity,
                    MyApp.context().resources.getString(R.string.status_uploaded),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun uploadImageStatus(path: String) {
        Log.d(TAG, "@@method: uploadImageStatus 01")
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(
                MyApp.context(),
                MyApp.context().resources.getString(R.string.no_internet_connection),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        Toast.makeText(
            MyApp.context(),
            MyApp.context().resources.getString(R.string.uploading_status),
            Toast.LENGTH_SHORT
        ).show()
        val mPath = compressImage(path)
        uploadStatus(mPath, StatusType.IMAGE, false).subscribe { status, throwable ->

            if (throwable != null) {
                Toast.makeText(
                    activity,
                    MyApp.context().resources.getString(R.string.error_uploading_status),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                setMyStatus()

                Toast.makeText(
                    activity,
                    MyApp.context().resources.getString(R.string.status_uploaded),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.addTo(disposables)
    }

    fun onTextStatusResult(textStatus: TextStatus) {
        Log.d(TAG, "@@method: 01")
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(MyApp.context(), R.string.no_internet_connection, Toast.LENGTH_SHORT)
                .show()
        } else {
            Toast.makeText(MyApp.context(), R.string.uploading_status, Toast.LENGTH_SHORT).show()
            uploadTextStatus(textStatus).subscribe({
                setMyStatus()
            }, { throwable ->
                Log.e(TAG, "onTextStatusResult: 010011")
                if (throwable != null) {
                    Toast.makeText(
                        activity,
                        MyApp.context().resources.getString(R.string.error_uploading_status),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        MyApp.context().resources.getString(R.string.status_uploaded),
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }).addTo(disposables)

        }
    }
    /////////////////////////////////////

    fun uploadTextStatus(textStatus: TextStatus): Completable {
        Log.d(TAG, "@@method: 02")

        val status = StatusCreator.createTextStatus(textStatus)
        return FireConstants.getMyStatusRef(StatusType.TEXT).child(status.statusId)
            .updateChildrenRx(status.toMap() as MutableMap<String, Any>).doOnComplete {
                Log.d(TAG, "@@method: 03")
            RealmHelper.getInstance()?.saveStatus(FireManager.uid, status)
            DeleteStatusJob.schedule(status.userId, status.statusId)
        }
    }


    fun uploadStatus(filePath: String, statusType: Int, isVideo: Boolean): Single<Status> {
        Log.d(TAG, "@@method: uploadImageStatus 02")

        val fileName = Util.getFileNameFromPath(filePath)
        val status: Status =
            if (isVideo) StatusCreator.createVideoStatus(filePath) else StatusCreator.createImageStatus(
                filePath
            )

        return FireManager.getRef(FireManager.STATUS_TYPE, fileName)
            .putFileRx(Uri.fromFile(File(filePath))).flatMap { uploadTask ->
                Log.d("@@TAG", "uploadImageStatus: 03 1")
                if (isVideo) {
                    val filePathBucket = uploadTask.storage.path
                    status.content = filePathBucket
                    return@flatMap FireConstants.getMyStatusRef(statusType).child(status.statusId)
                        .updateChildrenRx(status.toMap() as Map<String, Any>).toSingle<Any> {}
                        .map { status }
                } else {
                    return@flatMap uploadTask.storage.getDownloadUrlRx()
                        .flatMapSingle { downloadUrl ->
                            status.content = downloadUrl.toString()
                            return@flatMapSingle FireConstants.getMyStatusRef(statusType)
                                .child(status.statusId)
                                .updateChildrenRx(status.toMap() as Map<String, Any>)
                                .toSingle<Any> {}.map { status }
                        }
                }
            }.doOnSuccess { status ->
                Log.d(TAG, "@@method: uploadImageStatus 03 2 "+status.isValid()+" _ "+status.isLoaded()+" _ "+status.localPath+" _  "+ status.isLoaded())
                TEMP_localPath=status.localPath

         /*       if (status.isValid()) {
                    Log.d(TAG, "@@method: uploadImageStatus 03 3")
                    RealmHelper.getInstance()?.saveStatus(FireManager.uid, status)
                    DeleteStatusJob.schedule(status.userId, status.statusId)
                    refreshStatus(true)
                }*/
                refreshStatus(true)
            }
    }


    override fun onResume() {
        super.onResume()
        updateHeaders()
        setMyStatus()
        callbacks?.fetchStatuses()
    }

    fun refreshStatus(isVideo: Boolean) {
        var timeDelay: Long = 4000
        if (isVideo) {
            timeDelay = 12000
        }
        val handler = Handler()
        handler.postDelayed({

            updateHeaders()
            setMyStatus()
            callbacks?.fetchStatuses()
        }, timeDelay)
    }

    private fun updateHeaders() {
        if (decor != null) {
            setupHeaders()
            decor?.updateHeaders(header1pos, header2pos, header1Title, header2Title)
            adapter.notifyDataSetChanged()
        }
    }

    fun statusInserted() {
        try {
            //Fix for crash 'fragment not attached to context'
            updateHeaders()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onQueryTextChange(newText: String?) {
        super.onQueryTextChange(newText)
        if (adapter != null) {

            adapter.filter(newText)
        }
    }

    override fun onSearchClose() {
        super.onSearchClose()
        adapter = StatusAdapter(statusesList, true, activity, this@StatusFragment)

        rv_status?.let {
            it.adapter = adapter
        }

    }

    override fun onStatusClick(view: View, userStatuses: UserStatuses) {
        val intent = Intent(activity, ViewStatusActivity::class.java)
        intent.putExtra(IntentUtils.UID, userStatuses.userId)
        startActivity(intent)
    }

    //compress image when user chooses an image from gallery
    private fun compressImage(imagePath: String): String {
        //generate file in sent images folder
        val file = DirManager.generateFile(MessageType.SENT_IMAGE)
        //compress image and copy it to the given file
        BitmapUtils.compressImage(imagePath, file)
        return file.path
    }

    fun onImageEditSuccess(imagePath: String) {
        uploadImageStatus(imagePath)
    }

/*    inner class RealmHelper {

        private val counter = 0
        private val realm: Realm

        private var instance: RealmHelper? = null



        fun saveStatus(userId: String?, status: Status) {
            Log.d(TAG, "@@method: 04")

            val userStatuses = getUserStatuses(userId)
            val user = getUser(userId)
            realm.beginTransaction()
            if (userStatuses == null) {
                val statuses = RealmList<Status>()
                statuses.add(status)
                val mUserStatuses = UserStatuses(userId, status.timestamp, user, statuses)
                realm.copyToRealmOrUpdate(mUserStatuses)
            } else {
                val statuses = userStatuses.statuses
                if (!statuses.contains(status)) {
                    statuses.add(status)
                    userStatuses.lastStatusTimestamp = status.timestamp
                    userStatuses.user = user
                    userStatuses.isAreAllSeen = false
                }
            }
            realm.commitTransaction()
        }

        fun getInstance(): RealmHelper? {
            instance = RealmHelper()
            return instance
        }

        //save a message
        fun saveObjectToRealm(`object`: RealmObject) {
            realm.beginTransaction()
            if (`object` is Message) realm.copyToRealm<RealmObject>(`object`) else realm.copyToRealmOrUpdate(
                `object`
            )
            realm.commitTransaction()
        }

        //get all chats ordered by the time (the newest shows on top)

        private fun saveChatLastMessageTimestamp(chat: Chat, timestamp: String) {
            realm.beginTransaction()
            chat.lastMessageTimestamp = timestamp
            realm.commitTransaction()
        }

        //get certain message
        fun getMessage(id: String?): Message? {
            return realm.where(Message::class.java).equalTo(DBConstants.MESSAGE_ID, id).findFirst()
        }

        fun getMessage(messageId: String?, chatId: String?): Message? {
            return if (chatId == null) getMessage(messageId) else realm.where(Message::class.java)
                .equalTo(DBConstants.MESSAGE_ID, messageId).equalTo(DBConstants.CHAT_ID, chatId)
                .findFirst()
        }

        //get certain message
        fun getMessages(messageId: String?): RealmResults<Message> {
            return realm.where(Message::class.java).equalTo(DBConstants.MESSAGE_ID, messageId)
                .findAll()
        }

        //get all messages in chat sorted by time
        fun getMessagesInChat(chatId: String?): RealmResults<Message> {
            return realm.where(Message::class.java).equalTo(DBConstants.CHAT_ID, chatId).findAll()
                .sort(DBConstants.TIMESTAMP)
        }


        fun getUnProcessedNetworkRequests(): RealmResults<Message> {
            return realm.where(Message::class.java)
                .equalTo(DBConstants.DOWNLOAD_UPLOAD_STAT, DownloadUploadStat.LOADING)
                .findAll()
        }

        //update upload state when it's finished whether it's success ,failed,loading or cancelled
        fun updateDownloadUploadStat(messageId: String?, downloadUploadStat: Int) {
            //we are getting all messages because it's may be a broadcast ,if so we want to update the state of all of them
            val messages = getMessages(messageId)
            realm.beginTransaction()
            for (message in messages) {
                //if upload state is success ,update the message state to sent
                if (downloadUploadStat == DownloadUploadStat.SUCCESS) message.messageStat =
                    MessageStat.SENT
                message.downloadUploadStat = downloadUploadStat
            }
            realm.commitTransaction()
        }

        //update download state when it's finished whether it's success ,failed,loading or cancelled
        fun updateDownloadUploadStat(
            messageId: String?,
            downloadUploadStat: Int,
            localPath: String?
        ) {
            realm.beginTransaction()
            for (message in getMessages(messageId)) {
                if (downloadUploadStat == DownloadUploadStat.SUCCESS) message.messageStat =
                    MessageStat.SENT
                message.downloadUploadStat = downloadUploadStat
                //save the downloaded file path
                message.localPath = localPath
            }
            realm.commitTransaction()
        }

        //get video and image in chat


        //get all users that have installed this app and sort them by name

        //get all users that have installed this app and sort them by name

        //update user img if it's different

        //get certain user
        fun getUser(uid: String?): User? {
            return realm.where(User::class.java).equalTo(DBConstants.UID, uid).findFirst()
        }

        fun getUserByNumber(phone: String?): User? {
            return realm.where(User::class.java).equalTo(DBConstants.PHONE, phone).findFirst()
        }

        //check if messages is exists in database
        fun isExists(messageId: String?): Boolean {
            return !realm.where(Message::class.java).equalTo(DBConstants.MESSAGE_ID, messageId)
                .findAll().isEmpty()
        }

        //delete chat
        fun deleteChat(chatId: String?) {
            realm.beginTransaction()
            //delete chat
            realm.where(Chat::class.java).equalTo(DBConstants.CHAT_ID, chatId).findAll()
                .deleteFirstFromRealm()
            //delete all messages in this chat
            getMessagesInChat(chatId).deleteAllFromRealm()
            realm.commitTransaction()
        }

        //set chat muted

        //search for a Chat by the given name

        //search for a user by the given name or number

        //search for a text message in certain chat with the given query

        //save the firebase storage path for a file in realm to use it late when forwarding

        //update video thumb (not blurred version)
        fun setVideoThumb(messageId: String?, chatId: String?, videoThumb: String?) {
            val message = getMessage(messageId, chatId) ?: return
            realm.beginTransaction()
            message.videoThumb = videoThumb
            realm.commitTransaction()
        }

        //this is called when user has no internet connection and he opened a chat to see a message
        //that is NOT read before,therefore we want to save this to update the message with read state
        //once there is an internet connection
        // and the same is applied for received state
        fun saveUnUpdatedMessageStat(myUid: String?, messageId: String?, statToBeSaved: Int) {
            val unUpdatedStat = UnUpdatedStat(myUid, messageId, statToBeSaved)
            realm.beginTransaction()
            realm.copyToRealmOrUpdate(unUpdatedStat)
            realm.commitTransaction()
        }

        //same idea as saveUnUpdatedMessageStat
        fun saveUnUpdatedVoiceMessageStat(
            myUid: String?,
            messageId: String?,
            isVoiceMessageSeen: Boolean
        ) {
            val unUpdatedVoiceMessageStat =
                UnUpdatedVoiceMessageStat(myUid, messageId, isVoiceMessageSeen)
            realm.beginTransaction()
            realm.copyToRealmOrUpdate(unUpdatedVoiceMessageStat)
            realm.commitTransaction()
        }

        //get not updated messages state to update them
        fun getUnUpdateMessageStat(): RealmResults<UnUpdatedStat> {
            return realm.where(UnUpdatedStat::class.java).findAll()
        }

        fun getUnUpdatedVoiceMessageStat(): RealmResults<UnUpdatedVoiceMessageStat> {
            return realm.where(UnUpdatedVoiceMessageStat::class.java).findAll()
        }

        //delete UnUpdatedVoiceMessageStat once it's updated
        fun deleteUnUpdatedVoiceMessageStat(messageId: String?) {
            realm.where(UnUpdatedVoiceMessageStat::class.java)
                .equalTo(DBConstants.MESSAGE_ID, messageId).findAll().deleteAllFromRealm()
        }

        //delete deleteUnUpdateStat once it's updated
        fun deleteUnUpdateStat(messageId: String?) {
            realm.where(UnUpdatedStat::class.java).equalTo(DBConstants.MESSAGE_ID, messageId)
                .findAll()
                .deleteAllFromRealm()
        }

        fun setUserBlocked(user: User?, isBlocked: Boolean) {
            if (user == null) {
                return
            }
            realm.beginTransaction()
            user.isBlocked = isBlocked
            realm.copyToRealmOrUpdate(user)
            realm.commitTransaction()
        }

        //update user info if it's different from stored user

        fun updateThumbImg(uid: String?, thumbImg: String?) {
            val user = realm.where(
                User::class.java
            ).equalTo(DBConstants.UID, uid).findFirst()
                ?: return
            realm.beginTransaction()
            user.thumbImg = thumbImg
            realm.copyToRealmOrUpdate(user)
            realm.commitTransaction()
        }

        fun getPendingGroupCreationJobs(): RealmResults<PendingGroupJob> {
            return realm.where(PendingGroupJob::class.java).findAll()
        }

        fun getPendingGroupJob(groupId: String?): PendingGroupJob? {
            return realm.where(PendingGroupJob::class.java).equalTo(DBConstants.GROUP_ID, groupId)
                .findFirst()
        }

        fun deletePendingGroupCreationJob(groupId: String?) {
            val pendingGroupJobs = realm.where(
                PendingGroupJob::class.java
            ).equalTo("groupId", groupId).findAll()
            if (!pendingGroupJobs.isEmpty()) {
                realm.beginTransaction()
                pendingGroupJobs.deleteAllFromRealm()
                realm.commitTransaction()
            }
        }

        fun deleteGroupMember(groupId: String?, userToDeleteUid: String?) {
            val groupUser = getUser(groupId)
            val userToDelete = getUser(userToDeleteUid)
            if (groupUser!!.group != null && userToDelete != null) {
                val users = groupUser.group.users
                val adminsUids = groupUser.group.adminsUids
                realm.beginTransaction()
                users.remove(userToDelete)
                adminsUids.remove(userToDeleteUid)
                realm.commitTransaction()
            }
        }


        fun setGroupAdmin(groupId: String?, adminUid: String?, setAdmin: Boolean) {
            val user = getUser(groupId)
            if (user != null && user.group != null) {
                realm.beginTransaction()
                val adminsUids = user.group.adminsUids
                if (!setAdmin) {
                    adminsUids.remove(adminUid)
                } else if (!adminsUids.contains(adminUid)) {
                    adminsUids.add(adminUid)
                }
                realm.commitTransaction()
            }
        }

        fun changeGroupName(groupId: String, groupTitle: String?) {
            val user = getUser(groupId)
            if (user != null && user.group != null) {
                realm.beginTransaction()
                if (user.userName != groupId) {
                    user.userName = groupTitle
                }
                realm.commitTransaction()
            }
        }


        fun saveDeletedMessage(messageId: String?) {
            val deletedMessage = DeletedMessage(messageId)
            saveObjectToRealm(deletedMessage)
        }

        fun getDeletedMessage(messageId: String?): DeletedMessage? {
            return realm.where(DeletedMessage::class.java)
                .equalTo(DBConstants.MESSAGE_ID, messageId)
                .findFirst()
        }

        private fun deleteDeletedMessage(messageId: String?) {
            val deletedMessage = getDeletedMessage(messageId) ?: return
            realm.beginTransaction()
            deletedMessage.deleteFromRealm()
            realm.commitTransaction()
        }

        //set message as deleted (Delete for everyone)

        fun generateNotificationId(): Int {
            val maxId =
                realm.where(Chat::class.java).max(DBConstants.NOTIFICATION_ID)

            // If there are no rows, currentId is null, so the next id must be 1
            // If currentId is not null, increment it by 1
            return if (maxId == null) 1 else maxId.toInt() + 1
        }

        fun generateJobId(): Int {
            val maxId = realm.where(JobId::class.java).max(DBConstants.JOB_ID)

            // If there are no rows, currentId is null, so the next id must be 1
            // If currentId is not null, increment it by 1
            return if (maxId == null) 1 else maxId.toInt() + 1
        }

        fun setOnlyAdminsCanPost(groupId: String?, b: Boolean) {
            val user = getUser(groupId)
            if (user != null) {
                if (user.group.isOnlyAdminsCanPost == b) return
                realm.beginTransaction()
                user.group.isOnlyAdminsCanPost = b
                realm.commitTransaction()
            }
        }

        //this will update the group, add,remove a user,set a user as an admin,
        //check for group info change,etc..
        fun updateGroup(
            groupId: String?,
            info: DataSnapshot,
            usersSnapshot: DataSnapshot
        ): List<String>? {
            val groupUser = getUser(groupId) ?: return null
            val group = groupUser.group
            val onlyAdminsCanPost = info.child("onlyAdminsCanPost").getValue(
                Boolean::class.java
            )!!
            val groupName = info.child("name").getValue(String::class.java)!!
            val thumbImg = info.child("thumbImg").getValue(String::class.java)!!
            val users = group.users
            val adminsUids = group.adminsUids
            val unfetchedUsers: MutableList<String> = ArrayList()
            val serverUids: MutableList<String?> = ArrayList()
            val storedUids: MutableList<String> = ArrayList()
            for (user in group.users) {
                storedUids.add(user.uid)
            }
            realm.beginTransaction()
            if (group.isOnlyAdminsCanPost != onlyAdminsCanPost) group.isOnlyAdminsCanPost =
                onlyAdminsCanPost
            if (groupUser.userName != groupName) groupUser.userName = groupName
            if (groupUser.thumbImg != thumbImg) groupUser.thumbImg = thumbImg
            for (dataSnapshot in usersSnapshot.children) {
                val uid = dataSnapshot.key
                val isAdmin = dataSnapshot.getValue(Boolean::class.java)!!
                serverUids.add(uid)
                if (isAdmin) {
                    if (!adminsUids.contains(uid)) {
                        adminsUids.add(uid)
                    }
                } else {
                    adminsUids.remove(uid)
                }
            }

            //get only unique items from two lists and act against it
            val distinct = ListUtil.distinct(storedUids, serverUids)
            for (uid in distinct) {
                //addition event
                if (serverUids.contains(uid)) {
                    val user = getUser(uid)
                    if (user != null) {
                        users.add(user)
                        if (usersSnapshot.child(uid).getValue(Boolean::class.java)!!) {
                            adminsUids.add(uid)
                        }
                    } else {
                        //if it's a new user then add him to hashmap to fetch his data late
                        unfetchedUsers.add(uid)
                    }

                    //if the uid is current user's id then set the group as active
                    if (uid == FireManager.uid) {
                        group.isActive = true
                        EventBus.getDefault().post(GroupActiveStateChanged(groupId, true))
                    }
                } else {
                    //get user from group
                    val userById = ListUtil.getUserById(uid, users)
                    //check if exists
                    if (userById != null) {
                        //remove him from group
                        users.remove(userById)
                        //if current user is removed set group active to false
                        if (uid == FireManager.uid) {
                            group.isActive = false
                            EventBus.getDefault().post(GroupActiveStateChanged(groupId, false))
                        }
                    }
                }
            }
            realm.commitTransaction()
            return unfetchedUsers
        }

        fun saveJobId(jobId: JobId) {
            realm.beginTransaction()
            realm.copyToRealm(jobId)
            realm.commitTransaction()
        }

        fun getJobId(messageId: String?, isVoiceMessage: Boolean): Int {
            val jobId = realm.where(JobId::class.java).equalTo(DBConstants.ID, messageId)
                .equalTo(DBConstants.isVoiceMessage, isVoiceMessage).findFirst()
            return jobId?.jobId ?: -1
        }

        fun getJobId(id: Int, isVoiceMessage: Boolean): String {
            val jobId = realm.where(JobId::class.java).equalTo(DBConstants.JOB_ID, id)
                .equalTo(DBConstants.isVoiceMessage, isVoiceMessage).findFirst()
            return if (jobId != null) {
                jobId.id
            } else ""
        }

        fun deleteJobId(id: String?, isVoiceMessage: Boolean) {
            val jobId = realm.where(JobId::class.java).equalTo(DBConstants.ID, id)
                .equalTo(DBConstants.isVoiceMessage, isVoiceMessage).findFirst()
            if (jobId != null) {
                realm.beginTransaction()
                jobId.deleteFromRealm()
                realm.commitTransaction()
            }
        }

        //get a list of userStatuses that are not passed 24 hours
        fun getAllStatuses(): RealmResults<UserStatuses> {
            return realm.where(UserStatuses::class.java)
                .not()
                .`in`(DBConstants.statusUserId, arrayOf(FireManager.uid))
                .between(
                    DBConstants.lastStatusTimestamp,
                    TimeHelper.getTimeBefore24Hours(),
                    Long.MAX_VALUE
                )
                .isNotEmpty("statuses")
                .findAll()
                .sort(
                    DBConstants.ARE_ALL_STATUSES_SEEN,
                    Sort.ASCENDING,
                    DBConstants.lastStatusTimestamp,
                    Sort.DESCENDING
                )
        }

        fun setStatusContentAfterUpload(statusId: String?, uri: String) {
            val status = getStatus(statusId)
            if (status != null) {
                realm.beginTransaction()
                status.content = uri
                realm.commitTransaction()
            }
        }

        //get all statuses except current user's status
        fun getStatuses(): RealmResults<Status> {
            return realm.where(Status::class.java)
                .notEqualTo(DBConstants.statusUserId, FireManager.uid)
                .findAll()
        }

        fun getUserStatuses(userId: String?): UserStatuses? {

            return realm.where(UserStatuses::class.java).equalTo(DBConstants.statusUserId, userId)
                .isNotEmpty("statuses").findFirst()
        }

        fun getStatus(statusId: String?): Status? {
            return realm.where(Status::class.java).equalTo(DBConstants.statusId, statusId)
                .findFirst()
        }


        fun deleteStatus(userId: String, statusId: String?) {
            val status = getStatus(statusId)
            val userStatuses = getUserStatuses(userId)
            if (status != null && userStatuses != null) {
                realm.beginTransaction()
                //if the status was not uploaded by the user then delete the status locally
                if (userId != FireManager.uid) {
                    if (status.localPath != null) FileUtils.deleteFile(status.localPath)
                }
                userStatuses.statuses.remove(status)
                status.deleteFromRealm()
                realm.commitTransaction()
            }
        }

        fun setLocalPathForVideoStatus(statusId: String?, path: String?) {
            val status = getStatus(statusId)
            if (status != null) {
                realm.beginTransaction()
                status.localPath = path
                realm.commitTransaction()
            }
        }

        fun getAllCalls(): RealmResults<FireCall> {
            return realm.where(FireCall::class.java).findAll()
                .sort(DBConstants.TIMESTAMP, Sort.DESCENDING)
        }

        fun updateUserObjectForCall(uid: String?, callId: String?) {
            val user = getUser(uid)
            val fireCall = getFireCall(callId)
            if (user == null || fireCall == null) return
            realm.beginTransaction()
            fireCall.user = user
            realm.commitTransaction()
        }

        fun getFireCall(callId: String?): FireCall? {
            return realm.where(FireCall::class.java).equalTo(DBConstants.CALL_ID, callId)
                .findFirst()
        }

        fun setCallAsMissed(callId: String?) {
            val fireCall = getFireCall(callId) ?: return
            realm.beginTransaction()
            fireCall.direction = FireCallDirection.MISSED
            realm.commitTransaction()
        }

        fun updateCallInfoOnCallEnded(callId: String?, duration: Int) {
            val fireCall = getFireCall(callId) ?: return
            realm.beginTransaction()
            fireCall.duration = duration
            realm.commitTransaction()
        }

        fun setCallAsAnswered(callId: String?) {
            val fireCall = getFireCall(callId) ?: return
            realm.beginTransaction()
            fireCall.direction = FireCallDirection.ANSWERED
            realm.commitTransaction()
        }

        fun deleteCall(fireCall: FireCall?) {
            if (fireCall == null) return
            realm.beginTransaction()
            fireCall.deleteFromRealm()
            realm.commitTransaction()
        }

        fun setStatusAsSeen(statusId: String?) {
            val status = getStatus(statusId) ?: return
            realm.beginTransaction()
            status.isSeen = true
            realm.commitTransaction()
        }

        fun setAllStatusesAsSeen(userId: String?) {
            val userStatuses = getUserStatuses(userId) ?: return
            realm.beginTransaction()
            userStatuses.isAreAllSeen = true
            realm.commitTransaction()
        }

        fun searchForCall(newText: String?): RealmResults<FireCall> {
            return realm.where(FireCall::class.java)
                .contains(DBConstants.USER_USERNAME, newText, Case.INSENSITIVE)
                .findAll()
                .sort(DBConstants.TIMESTAMP, Sort.DESCENDING)
        }

        fun searchForStatus(newText: String?): RealmResults<UserStatuses> {
            return realm.where(UserStatuses::class.java)
                .not()
                .`in`(DBConstants.statusUserId, arrayOf(FireManager.uid))
                .between(
                    DBConstants.lastStatusTimestamp,
                    TimeHelper.getTimeBefore24Hours(),
                    Date().time
                )
                .contains(DBConstants.USER_USERNAME, newText, Case.INSENSITIVE)
                .findAll()
                .sort(
                    DBConstants.ARE_ALL_STATUSES_SEEN,
                    Sort.ASCENDING,
                    DBConstants.lastStatusTimestamp,
                    Sort.DESCENDING
                )
        }


        fun setStatusCount(statusId: String?, count: Int) {
            val status = getStatus(statusId) ?: return
            if (count == status.seenCount) return
            realm.beginTransaction()
            status.seenCount = count
            realm.commitTransaction()
        }

        fun isCallCancelled(callId: String?): Boolean {
            if (callId == null) return true
            val fireCall = getFireCall(callId) ?: return true
            return fireCall.direction == FireCallDirection.MISSED
        }

        fun clearChat(chatId: String?, deleteMedia: Boolean) {
            val messages = realm.where(
                Message::class.java
            ).equalTo(DBConstants.CHAT_ID, chatId).findAll()
            if (messages.isEmpty()) return
            realm.beginTransaction()
            if (deleteMedia) {
                for (message in messages) {
                    FileUtils.deleteFile(message.localPath)
                }
            }
            messages.deleteAllFromRealm()
            realm.commitTransaction()
        }

        fun refresh() {
            realm.refresh()
        }

        fun deleteBroadcastMember(broadcastId: String?, userToDeleteUid: String?) {
            val broadcast = getBroadcast(broadcastId) ?: return
            val users = broadcast.users
            realm.beginTransaction()
            users.remove(ListUtil.getUserById(userToDeleteUid, users))
            realm.commitTransaction()
        }

        private fun getBroadcast(broadcastId: String?): Broadcast? {
            return realm.where(Broadcast::class.java).equalTo(DBConstants.BROADCAST_ID, broadcastId)
                .findFirst()
        }

        fun addUserToBroadcast(broadcastId: String?, user: User?) {
            val broadcast = getBroadcast(broadcastId) ?: return
            val users = broadcast.users ?: return
            realm.beginTransaction()
            users.add(user)
            realm.commitTransaction()
        }

        fun changeBroadcastName(broadcastId: String?, newTitle: String?) {
            val broadcast = getUser(broadcastId) ?: return
            realm.beginTransaction()
            broadcast.userName = newTitle
            realm.commitTransaction()
        }

        //this is used to copy the chat from backup when restoring
        //because if we only copy it it will duplicate it
        //so we need to add only the saved message
        fun migrateChat(mChat: Chat) {
            val chat = realm.copyFromRealm(mChat)
            realm.beginTransaction()
            if (chat.lastMessage != null) chat.lastMessage =
                getMessage(chat.lastMessage.messageId, chat.chatId)
            chat.unreadMessages.clear()
            realm.copyToRealmOrUpdate(chat)
            realm.commitTransaction()
        }

        fun saveSeenByList(statusId: String?, seenByList: List<StatusSeenBy?>) {
            val status = getStatus(statusId) ?: return
            realm.executeTransaction { realm: Realm? ->
                val seenByRealmList = status.seenBy
                for (statusSeenBy in seenByList) {
                    if (!seenByRealmList.contains(statusSeenBy)) seenByRealmList.add(statusSeenBy)
                }
            }
        }

        fun getSeenByList(seenByList: RealmList<StatusSeenBy?>): RealmResults<StatusSeenBy?> {
            return seenByList.sort("seenAt", Sort.DESCENDING)
        }

        fun setLastImageSyncDate(uid: String?, time: Long) {
            val user = getUser(uid)
            if (user != null) {
                realm.executeTransaction { transaction: Realm? ->
                    user.lastTimeFetchedImage = time
                }
            }
        }

        fun updateUserStatus(uid: String, status: String) {
            val user = getUser(uid) ?: return
            realm.executeTransaction { transaction: Realm? ->
                user.status = status
            }
        }


        //get instance of real
        init {
            realm = Realm.getDefaultInstance()
        }
    }*/


}
