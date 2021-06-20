package org.wBHARATmeet.activities.main.chats

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cjt2325.cameralibrary.ResultCodes
import com.droidninja.imageeditengine.ImageEditor
import com.google.android.gms.ads.AdView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.zhihu.matisse.Matisse
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.RealmResults
import kotlinx.android.synthetic.main.fragment_chats.*
import kotlinx.android.synthetic.main.fragment_status.*
import org.wBHARATmeet.R
import org.wBHARATmeet.activities.ProfilePhotoDialog
import org.wBHARATmeet.activities.ViewStatusActivity
import org.wBHARATmeet.activities.main.MainActivity
import org.wBHARATmeet.activities.main.MainViewModel
import org.wBHARATmeet.activities.main.chats.ChatsAdapter.ChatsHolder
import org.wBHARATmeet.activities.main.messaging.ChatActivity
import org.wBHARATmeet.activities.main.status.StatusFragmentEvent
import org.wBHARATmeet.adapters.HomePageStatusAdapter
import org.wBHARATmeet.fragments.BaseFragment
import org.wBHARATmeet.interfaces.FragmentCallback
import org.wBHARATmeet.interfaces.StatusFragmentCallbacks
import org.wBHARATmeet.model.constants.*
import org.wBHARATmeet.model.realms.*
import org.wBHARATmeet.utils.*
import org.wBHARATmeet.utils.GroupTyping.GroupTypingListener
import org.wBHARATmeet.utils.network.FireManager
import org.wBHARATmeet.utils.network.GroupManager
import org.wBHARATmeet.utils.network.StatusManager
import org.wBHARATmeet.views.HeaderViewDecoration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


class FragmentChats : BaseFragment(), GroupTypingListener, ActionMode.Callback, ChatsAdapter.ChatsAdapterCallback,HomePageStatusAdapter.OnClickListener {

    private var rvChats: RecyclerView? = null
    var adapter: ChatsAdapter? = null
    var linearLayoutManager: LinearLayoutManager? = null
    var chatList: RealmResults<Chat>? = null
    var changeListener: OrderedRealmCollectionChangeListener<RealmResults<Chat>>? = null
    var typingEventListener: ValueEventListener? = null
    var voiceMessageListener: ValueEventListener? = null
    var lastMessageStatListener: ValueEventListener? = null
    var groupTypingList: MutableList<GroupTyping>? = null
    var fireListener: FireListener? = null
    override var adView: AdView? = null
    private var callback: FragmentCallback? = null
    private var actionMenu: Menu? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: ChatsFragmentViewModel by activityViewModels()
    var actionMode: ActionMode? = null
    private var selectedChats = ArrayList<Chat>()

    private val groupManager = GroupManager()
    override val disposables = CompositeDisposable()

    //status
            var mStatusAdapter: HomePageStatusAdapter? = null
            var statusesList: RealmResults<UserStatuses>? = null
            private var myStatuses: UserStatuses? = null
            private var decor: HeaderViewDecoration? = null
            private var header1pos = 0
            private var header2pos = 0
            private var header1Title: String? = null
            private var header2Title = ""
            private var callbacks: StatusFragmentCallbacks? = null
            private val statusManager = StatusManager()
            private var MAX_STATUS_VIDEO_TIME = 0
            private lateinit var profileImage: CircleImageView
            private lateinit var rowStatusContainer: LinearLayout
            private val viewModelSatus: MainViewModel by  activityViewModels()
            private lateinit var rv_status: RecyclerView
            var TAG="@@FragmentChats"

    private val isHasMutedItem: Boolean
        get() {
            val selectedItems = selectedChats

            for (chat in selectedItems) {
                if (chat.isMuted)
                    return true
            }
            return false
        }

    private val isHasGroupItem: Boolean
        get() {
            val selectedItems = selectedChats

            for (chat in selectedItems) {
                val user = chat.user
                if (user.isGroupBool && user.group.isActive)
                    return true
            }
            return false
        }

    private fun updateMutedIcon(menuItem: MenuItem?, isMuted: Boolean) {
        menuItem?.setIcon(if (isMuted) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
    }

    private fun setMenuItemVisibility(b: Boolean) {

        actionMenu?.findItem(R.id.menu_item_mute)?.isVisible = b

    }

    private fun updateGroupItems() {
        actionMenu?.findItem(R.id.menu_item_delete)?.isVisible = !isHasGroupItem
        actionMenu?.findItem(R.id.exit_group_item)?.isVisible = areAllOfChatsGroups()
    }

    private fun areAllOfChatsGroups(): Boolean {

        var b = false

        val selectedItems = selectedChats
        for (chat in selectedItems) {
            val user = chat.user
            if (user.isGroupBool && user.group.isActive)
                b = true
            else {
                return false
            }
        }

        return b

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_chats, container, false)
        init(view)

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? FragmentCallback
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fireListener = FireListener()
        chatList = RealmHelper.getInstance().allChats
        setTheAdapter()
        listenForTypingStat()
        listenForVoiceMessageStat()
        listenForLastMessageStat()
        listenForMessagesChanges()
        adViewInitialized(adView)

        mainViewModel.queryTextChange.observe(viewLifecycleOwner, androidx.lifecycle.Observer { text ->
            onQueryTextChange(text)
        })

    }

    override fun showAds(): Boolean {
        return resources.getBoolean(R.bool.is_calls_ad_enabled)
    }

    private fun init(view: View) {
        rvChats = view.findViewById(R.id.rv_chats)
        adView = view.findViewById(R.id.ad_view)


        // status
        initializationStatusIDs(view)
        rowStatusContainer.setOnClickListener {
            try {
                (activity as MainActivity?)?.onClickStatusProfile()
            }catch (e:Exception) {
                Log.e(TAG, "init: "+e.message)
            }
        }



   initMyStatuses()

   MAX_STATUS_VIDEO_TIME = resources.getInteger(R.integer.max_status_video_time)
        statusesList = RealmHelper.getInstance().allStatuses

        initAdapter()
//        setMyStatus()


            try {
                val menu_container = (context as MainActivity?)!!.findViewById<LinearLayout>(R.id.menu_container)

                if (statusesList?.size!! > 9) {

                    rvChats?.onFlingListener = object : RecyclerView.OnFlingListener() {
                        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                            if (velocityY < 0) {

                                val animation: Animation
                                animation = AnimationUtils.loadAnimation(
                                    context,
                                    R.anim.bottom_to_original
                                )
                                menu_container.visibility = View.VISIBLE
                                menu_container.setAnimation(animation)


                            }else if (velocityY > 0){
                                menu_container.visibility = View.GONE
                            }
                            //Code to show the UI
                            return false
                        }
                    }
                } else {
                    menu_container.visibility = View.VISIBLE
                }

            }catch (e:Exception) {

            }



        viewModelSatus.statusLiveData.observe(viewLifecycleOwner, androidx.lifecycle.Observer { statusFragmentEvent ->
            when (statusFragmentEvent) {
                is StatusFragmentEvent.StatusInsertedEvent -> statusInserted()
                is StatusFragmentEvent.OnActivityResultEvent -> {
                    val requestCode = statusFragmentEvent.requestCode
                    val resultCode = statusFragmentEvent.resultCode
                    val data = statusFragmentEvent.data

                    if (requestCode == MainActivity.CAMERA_REQUEST) {
                        onCameraActivityResult(resultCode, data)

                    } else if (requestCode == ImageEditor.RC_IMAGE_EDITOR && resultCode == Activity.RESULT_OK) {
                        data.getStringExtra(ImageEditor.EXTRA_EDITED_PATH)?.let { imagePath ->
                            onImageEditSuccess(imagePath)
                        }

                    } else if (requestCode == MainActivity.REQUEST_CODE_TEXT_STATUS && resultCode == Activity.RESULT_OK) {
                        data.getParcelableExtra<TextStatus>(IntentUtils.EXTRA_TEXT_STATUS)?.let { textStatus ->
                            onTextStatusResult(textStatus)
                        }

                    }
                }
            }
        })

        viewModelSatus.queryTextChange.observe(viewLifecycleOwner, androidx.lifecycle.Observer { newText ->
            onQueryTextChange(newText)
        })
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
                        Toast.makeText(activity, MyApp.context().resources.getString(R.string.image_video_not_found), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(activity, MyApp.context().resources.getString(R.string.video_length_is_too_long), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    //if it's only one image open image editor
                    if (mPaths.size == 1) ImageEditorRequest.open(activity, mPaths[0]) else for (path in mPaths) {
                        uploadImageStatus(path)
                    }
                }
            }
        }
    }
    private fun uploadVideoStatus(path: String) {
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(activity, MyApp.context().resources.getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, R.string.uploading_status, Toast.LENGTH_SHORT).show()
        disposables.add(statusManager.uploadStatus(path, StatusType.VIDEO, true)!!.subscribe { status, throwable ->
            if (throwable != null) {
                Toast.makeText(activity, MyApp.context().resources.getString(R.string.error_uploading_status), Toast.LENGTH_SHORT).show()
            } else {
//                setMyStatus()
                Toast.makeText(activity, MyApp.context().resources.getString(R.string.status_uploaded), Toast.LENGTH_SHORT).show()
            }
        })


    }
    fun statusInserted() {
        try {
            //Fix for crash 'fragment not attached to context'
            updateHeaders()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun updateHeaders() {
        if (decor != null) {
            setupHeaders()
            decor?.updateHeaders(header1pos, header2pos, header1Title, header2Title)
            adapter?.notifyDataSetChanged()
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
    override fun onStatusClick(view: View?, userStatuses: UserStatuses?) {
        val intent = Intent(activity, ViewStatusActivity::class.java)
        intent.putExtra(IntentUtils.UID, userStatuses?.userId)
        startActivity(intent)
    }
    fun onImageEditSuccess(imagePath: String) {
        uploadImageStatus(imagePath)
    }
    private fun uploadImageStatus(path: String) {
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(MyApp.context(), MyApp.context().resources.getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(MyApp.context(), MyApp.context().resources.getString(R.string.uploading_status), Toast.LENGTH_SHORT).show()
        val mPath = compressImage(path)
        statusManager.uploadStatus(mPath, StatusType.IMAGE, false)!!.subscribe { status, throwable ->
            if (throwable != null) {
                Toast.makeText(activity, MyApp.context().resources.getString(R.string.error_uploading_status), Toast.LENGTH_SHORT).show()
            } else {
//                setMyStatus()
                Toast.makeText(activity, MyApp.context().resources.getString(R.string.status_uploaded), Toast.LENGTH_SHORT).show()
            }
        }.addTo(disposables)
    }
    private fun compressImage(imagePath: String): String {
        //generate file in sent images folder
        val file = DirManager.generateFile(MessageType.SENT_IMAGE)
        //compress image and copy it to the given file
        BitmapUtils.compressImage(imagePath, file)
        return file.path
    }

/*
    fun setMyStatus() {
        Log.e(TAG, "setMyStatus: "+myStatuses)
        if (myStatuses == null) initMyStatuses()
        if (myStatuses != null
            && myStatuses?.filteredStatuses?.isNotEmpty() == true) {
            val lastStatus = myStatuses?.statuses?.last()
            val statusTime = TimeHelper.getStatusTime(lastStatus?.timestamp ?: Date().time)



            if (lastStatus?.type == StatusType.IMAGE || lastStatus?.type == StatusType.VIDEO) {

                profileImage.visibility = View.VISIBLE
                Glide.with(requireActivity()).load(lastStatus.thumbImg).into(profileImage)
            } else if (lastStatus?.type == StatusType.TEXT) {

                profileImage.visibility = View.GONE
                val textStatus = lastStatus.textStatus

            }
        } else {


            profileImage.visibility = View.VISIBLE
            Glide.with(requireActivity()).load(SharedPreferencesManager.getThumbImg()).into(profileImage)
        }
    }
*/

    fun onTextStatusResult(textStatus: TextStatus) {
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(MyApp.context(), R.string.no_internet_connection, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(MyApp.context(), R.string.uploading_status, Toast.LENGTH_SHORT).show()
/*            statusManager.uploadTextStatus(textStatus).subscribe ({
//                setMyStatus()
            },{throwable ->

            }).addTo(disposables)*/

        }
    }
    fun initializationStatusIDs(view: View) {

        rowStatusContainer = view.findViewById(R.id.row_status_container)
        profileImage = view.findViewById(R.id.profile_image)
        rv_status = view.findViewById(R.id.rv_status)

    }
    private fun initMyStatuses() {
        myStatuses = RealmHelper.getInstance().getUserStatuses(FireManager.uid)
    }
    private fun initAdapter() {

        rv_status.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        rv_status.setLayoutManager(layoutManager)
        rv_status.setNestedScrollingEnabled(false)
        mStatusAdapter = HomePageStatusAdapter(statusesList, true, context, this@FragmentChats)
        rv_status.setAdapter(mStatusAdapter)

    }

    //add a listener for the last message if the user has replied from the notification
    private fun listenForMessagesChanges() {
        changeListener = OrderedRealmCollectionChangeListener<RealmResults<Chat>> { chats, changeSet ->
            val modifications = changeSet.changeRanges
            if (modifications.size != 0) {
                val chat = chats[modifications[0].startIndex]
                val lastMessage = chat!!.lastMessage
                if (lastMessage != null && lastMessage.messageStat == MessageStat.PENDING
                        || lastMessage != null && lastMessage.messageStat == MessageStat.SENT) {
                    addMessageStatListener(chat.chatId, lastMessage)
                }
            }
        }
    }

    //listen for lastMessage stat if it's received or read by the other user
    private fun listenForLastMessageStat() {
        lastMessageStatListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value == null) return
                val `val` = dataSnapshot.getValue(Int::class.java)!!
                val key = dataSnapshot.key
                val chatId = dataSnapshot.ref.parent!!.key
                RealmHelper.getInstance().updateMessageStatLocally(key, chatId, `val`)
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        }
    }

    private fun addVoiceMessageStatListener() {
        for (chat in chatList!!) {
            val lastMessage = chat.lastMessage
            val user = chat.user ?: continue
            if (!user.isBroadcastBool && lastMessage != null && lastMessage.type != MessageType.GROUP_EVENT && lastMessage.isVoiceMessage
                    && lastMessage.fromId == FireManager.uid && !lastMessage.isVoiceMessageSeen) {
                val reference = FireConstants.voiceMessageStat.child(lastMessage.chatId).child(lastMessage.messageId)
                fireListener!!.addListener(reference, voiceMessageListener)
            }
        }
    }

    private fun addMessageStatListener() {
        for (chat in chatList ?: emptyList<Chat>()) {
            val lastMessage = chat.lastMessage
            val user = chat.user ?: continue
            if (user.isBroadcastBool && lastMessage != null && lastMessage.type != MessageType.GROUP_EVENT && lastMessage.messageStat != MessageStat.READ) {
                val reference = FireConstants.messageStat.child(chat.chatId).child(lastMessage.messageId)
                fireListener!!.addListener(reference, lastMessageStatListener)
            }
        }
    }

    private fun addMessageStatListener(chatId: String, lastMessage: Message?) {
        if (lastMessage != null && lastMessage.type != MessageType.GROUP_EVENT && lastMessage.messageStat != MessageStat.READ) {
            val reference = FireConstants.messageStat.child(chatId).child(lastMessage.messageId)
            fireListener!!.addListener(reference, lastMessageStatListener)
        }
    }

    //if the lastMessage is a Voice message then we want to
    //listen if it's listened by the other user
    private fun listenForVoiceMessageStat() {
        voiceMessageListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value == null) {
                    return
                }
                val key = dataSnapshot.key
                val chatId = dataSnapshot.ref.parent!!.key
                RealmHelper.getInstance().updateVoiceMessageStatLocally(key, chatId)
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        }
    }

    //listen if other user is typing to this user
    private fun listenForTypingStat() {
        typingEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value == null) return
                val stat = dataSnapshot.getValue(Int::class.java)!!
                val uid = dataSnapshot.ref.parent!!.key

                //create temp chat object to get the index of the uid
                val chat = Chat()
                chat.chatId = uid
                val i = chatList?.indexOf(chat) ?: -1
                //if chat is not exists in the list return
                if (i == -1) return
                val vh = rvChats!!.findViewHolderForAdapterPosition(i) as ChatsHolder? ?: return
                adapter!!.typingStatHashmap[chat.chatId] = stat
                val typingTv = vh.tvTypingStat
                val lastMessageTv = vh.tvLastMessage
                val lastMessageReadIcon = vh.imgReadTagChats


                //if other user is typing or recording to this user
                //then hide last message textView with all its contents
                if (stat == TypingStat.TYPING || stat == TypingStat.RECORDING) {
                    lastMessageTv.visibility = View.GONE
                    lastMessageReadIcon.visibility = View.GONE
                    typingTv.visibility = View.VISIBLE
                    if (stat == TypingStat.TYPING) typingTv.text = resources.getString(R.string.typing) else if (stat == TypingStat.RECORDING) typingTv.text = resources.getString(R.string.recording)

                    //in case there is no typing or recording event
                    //revert back to normal mode and show last message
                } else {
                    adapter!!.typingStatHashmap.remove(chat.chatId)
                    typingTv.visibility = View.GONE
                    lastMessageTv.visibility = View.VISIBLE
                    val lastMessage = chatList!![i]!!.lastMessage
                    if (lastMessage != null && lastMessage.type != MessageType.GROUP_EVENT && !MessageType.isDeletedMessage(lastMessage.type)
                            && lastMessage.fromId == FireManager.uid) {
                        lastMessageReadIcon.visibility = View.VISIBLE
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        }
    }

    //adding typing listeners for all chats
    private fun addTypingStatListener() {
        if (!FireManager.isLoggedIn()) return
        for (chat in chatList!!) {
            val user = chat.user ?: continue
            if (user.isGroupBool && user.group.isActive) {
                if (groupTypingList == null) groupTypingList = ArrayList()
                val groupTyping = GroupTyping(user.group.users, user.uid, this)
                groupTypingList!!.add(groupTyping)
            } else {
                val receiverUid = user.uid
                val typingStat = FireConstants.mainRef.child("typingStat").child(receiverUid)
                        .child(FireManager.uid)
                fireListener!!.addListener(typingStat, typingEventListener)
            }
        }
    }


    private fun setTheAdapter() {
        adapter = ChatsAdapter(chatList, true, requireActivity(), this)
        linearLayoutManager = LinearLayoutManager(activity)
        rvChats!!.layoutManager = linearLayoutManager
        rvChats!!.adapter = adapter
    }

    override fun onTyping(state: Int, groupId: String, user: User?) {
        val tempChat = Chat()
        tempChat.chatId = groupId
        val i = chatList!!.indexOf(tempChat)
        if (i == -1) return
        if (user == null) return
        val chat = chatList!![i]
        val vh = rvChats!!.findViewHolderForAdapterPosition(i) as ChatsHolder? ?: return
        adapter!!.typingStatHashmap[chat!!.chatId] = state
        val typingTv = vh.tvTypingStat
        val lastMessageTv = vh.tvLastMessage
        val lastMessageReadIcon = vh.imgReadTagChats


        //if other user is typing or recording to this user
        //then hide last message textView with all its contents
        if (state == TypingStat.TYPING || state == TypingStat.RECORDING) {
            lastMessageTv.visibility = View.GONE
            lastMessageReadIcon.visibility = View.GONE
            typingTv.visibility = View.VISIBLE
            typingTv.text = user.userName + " is " + TypingStat.getStatString(activity, state)
        }
    }

    override fun onAllNotTyping(groupId: String) {
        val tempChat = Chat()
        tempChat.chatId = groupId
        val i = chatList!!.indexOf(tempChat)
        if (i == -1) return
        val chat = chatList!![i]
        val vh = rvChats!!.findViewHolderForAdapterPosition(i) as ChatsHolder? ?: return
        val typingTv = vh.tvTypingStat
        val lastMessageTv = vh.tvLastMessage
        val lastMessageReadIcon = vh.imgReadTagChats
        adapter!!.typingStatHashmap.remove(chat!!.chatId)
        typingTv.visibility = View.GONE
        lastMessageTv.visibility = View.VISIBLE
        val lastMessage = chatList!![i]!!.lastMessage
        if (lastMessage != null && lastMessage.type != MessageType.GROUP_EVENT && !MessageType.isDeletedMessage(lastMessage.type)
                && lastMessage.fromId == FireManager.uid) {
            lastMessageReadIcon.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        addTypingStatListener()
        addVoiceMessageStatListener()
        addMessageStatListener()
        chatList?.addChangeListener(changeListener)
    }

    override fun onPause() {
        super.onPause()
        fireListener!!.cleanup()
        if (groupTypingList != null) {
            for (groupTyping in groupTypingList!!) {
                groupTyping.cleanUp()
            }
        }
        chatList?.removeChangeListener(changeListener)
        adapter?.exitActionMode()
        actionMode?.finish()

    }


    override fun onQueryTextChange(newText: String?) {
        super.onQueryTextChange(newText)
        adapter?.filter(newText)
    }

    override fun onSearchClose() {
        super.onSearchClose()

    }


    override fun onClick(chat: Chat, view: View) {
//        if isInAction mode then select or remove the clicked chat from selectedActionList
        if (isInActionMode()) {
            //if it's selected ,remove it
            if (selectedChats.contains(chat))
                itemRemoved(view, chat);

            //otherwise add it to list
            else
                itemAdded(view, chat);
            //if it's not in actionMode start the chatActivity
        } else {
            chat.let {
                if (it.user != null) {
                    val user = it.user
                    val intent = Intent(context, ChatActivity::class.java)
                    intent.putExtra(IntentUtils.UID, user.uid)
                    startActivity(intent);
                }
            }

        }
    }

    private fun isInActionMode() = actionMode != null

    private fun itemAdded(itemView: View, chat: Chat) {

        selectedChats.add(chat)
        adapter?.itemAdded(itemView, chat)

        val itemsCount = selectedChats.size

        actionMode?.title = itemsCount.toString() + ""

        updateActionMenuItems(itemsCount)
    }

    private fun updateActionMenuItems(itemsCount: Int) {
        if (itemsCount > 1) {
            if (isHasMutedItem)
                setMenuItemVisibility(false)
            else
                updateMutedIcon(actionMenu?.findItem(R.id.menu_item_mute), false)//if there is no muted item then the user may select multiple chats and mute them all in once


        } else if (itemsCount == 1 && selectedChats.size == 1) {

            val isMuted = selectedChats[0].isMuted
            //in case if it's hidden before
            setMenuItemVisibility(true)
            updateMutedIcon(actionMenu?.findItem(R.id.menu_item_mute), isMuted)

        }

        updateGroupItems()
    }

    private fun itemRemoved(itemView: View, chat: Chat) {
        selectedChats.remove(chat)
        adapter?.itemRemoved(itemView, chat)
        actionMode?.title = selectedChats.size.toString() + ""
        if (selectedChats.isEmpty())
            exitActionMode()
        else
            updateActionMenuItems(selectedChats.size)
    }

    private fun exitActionMode() {
        actionMode?.finish()
    }

    override fun onLongClick(chat: Chat, view: View) {
        if (!isInActionMode()) {
            callback!!.startTheActionMode(this)
            itemAdded(view, chat)
        }


    }


    override fun userProfileClicked(user: User) {
        //start user profile (Dialog-Like Activity)
        val intent = Intent(requireContext(), ProfilePhotoDialog::class.java)
        intent.putExtra(IntentUtils.UID, user.uid)
        startActivity(intent)

    }

    override fun onBind(pos: Int, chat: Chat?) {
        chat?.let { chat ->
            chat.user?.let { user ->
                viewModel.fetchUserImage(pos, user)

            }
        }
    }

    override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        this.actionMode = actionMode

        actionMode.menuInflater.inflate(R.menu.menu_action_chat_list, menu)
        this.actionMenu = menu
        actionMode.title = "1"
        return true
    }

    override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onDestroyActionMode(actionMode: ActionMode) {
        this.actionMode = null
        selectedChats.clear()
        adapter?.exitActionMode()
    }

    override fun onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean {
        if (actionMode != null && menuItem != null) {
            when (menuItem.itemId) {
                R.id.menu_item_delete -> deleteItemClicked()

                R.id.menu_item_mute -> muteItemClicked()

                R.id.exit_group_item -> exitGroupClicked()

            }

            return true
        }
        return false
    }

    private fun muteItemClicked() {
        val selectedItems = selectedChats
        for (chat in selectedItems) {
            if (chat.isMuted) {
                RealmHelper.getInstance().setMuted(chat.chatId, false)
            } else {
                RealmHelper.getInstance().setMuted(chat.chatId, true)
            }
        }

        exitActionMode()
    }

    private fun exitGroupClicked() {
        if (!NetworkHelper.isConnected(MyApp.context()))
            return

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.confirmation)
                .setMessage(R.string.exit_group)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, DialogInterface.OnClickListener { _, _ ->
                    val selectedItems = selectedChats
                    for (chat in selectedItems) {
                        disposables.add(groupManager.exitGroup(chat.chatId, FireManager.uid).subscribe({
                            RealmHelper.getInstance().exitGroup(chat.chatId)
                            val groupEvent = GroupEvent(SharedPreferencesManager.getPhoneNumber(), GroupEventTypes.USER_LEFT_GROUP, null)
                            groupEvent.createGroupEvent(chat.user, null)
                        }, { throwable ->
                            Toast.makeText(requireContext(), R.string.error , Toast.LENGTH_SHORT).show();
                        })
                        )
                    }
                    exitActionMode()
                })
                .show()


    }

    private fun deleteItemClicked() {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(R.string.confirmation)
                .setMessage(R.string.delete_conversation_confirmation)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, DialogInterface.OnClickListener { _, _ ->
                    val selectedItems = selectedChats
                    for (chat in selectedItems) {
                        RealmHelper.getInstance().deleteChat(chat.chatId)
                    }
                    exitActionMode()
                })
                .show()

    }

    override fun onDestroy() {
        super.onDestroy()
        adapter?.onDestroy()
    }



}
