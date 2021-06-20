package org.wBHARATmeet.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.StorageReference
import com.theartofdev.edmodo.cropper.CropImage
import durdinapps.rxfirebase2.RxFirebaseDatabase
import io.reactivex.functions.Consumer
import org.wBHARATmeet.R
import org.wBHARATmeet.extensions.putFileRx
import org.wBHARATmeet.model.ImageItem
import org.wBHARATmeet.model.constants.GroupEventTypes
import org.wBHARATmeet.model.realms.GroupEvent
import org.wBHARATmeet.model.realms.User
import org.wBHARATmeet.utils.*
import org.wBHARATmeet.utils.network.FireManager
import org.wBHARATmeet.utils.network.FireManager.Companion.checkAndDownloadUserPhoto
import org.wBHARATmeet.utils.network.GroupManager
import java.io.File
import java.util.*


class ProfilePhotoActivity : BaseActivity() {
    private var toolbarProfile: Toolbar? = null
    private lateinit var profileFullScreen: ImageView
    var user: User? = null
    var profilePhotoPath: String? = null
    private val IMAGE_QUALITY_COMPRESS = 30
    private var isGroup = false
    private var isBroadcast = false
    private val groupManager = GroupManager()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_photo)
        toolbarProfile = findViewById(R.id.toolbar_profile)
        profileFullScreen = findViewById(R.id.profile_full_screen)
        setSupportActionBar(toolbarProfile)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        //if user viewing other user's photo
        if (intent.hasExtra(IntentUtils.UID)) {
            val uid = intent.getStringExtra(IntentUtils.UID)
            //getting the user from realm because the image may not be updated while fetching the user in the list
            user = RealmHelper.getInstance().getUser(uid)
            user?.let {
                isBroadcast = it.isBroadcastBool
                isGroup = it.isGroupBool
                profilePhotoPath = it.userLocalPhoto
                supportActionBar!!.setTitle(it.userName)
            }


            //if user is viewing his photo
        } else {
            val imgPath = intent.getStringExtra(IntentUtils.EXTRA_PROFILE_PATH)
            supportActionBar!!.setTitle(R.string.profile_photo)
            Glide.with(this).load(imgPath)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(profileFullScreen)
        }
    }

    private fun loadImage(profilePhotoPath: String?) {
        if (user == null) return

        val consumer = Consumer<String> { photoPath ->
            Glide.with(this@ProfilePhotoActivity).load(photoPath)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(profileFullScreen!!)
        }
        val errorConsumer = Consumer<Throwable> {

        }

        if(isGroup) disposables.add(fireManager.downloadUserPhoto(user!!.uid, user!!.userLocalPhoto, isGroup).subscribe(consumer, errorConsumer))
        if(isBroadcast) disposables.add(fireManager.downloadBroadcastPhoto(user!!.uid, user!!.userLocalPhoto, isBroadcast).subscribe(consumer, errorConsumer))


        /*if (isBroadcast) {
            //val drawable = AppCompatResources.getDrawable(this, R.drawable.ic_broadcast_with_bg)
            //profileFullScreen!!.setImageDrawable(drawable)
            //if the profilePhotoPath in Database is not exists

            if (user!!.thumbImg != null) {
                Glide.with(this).load(user!!.thumbImg).into(profileFullScreen!!)
            }
            //start getting full image
            disposables.add(fireManager.downloadBroadcastPhoto(user!!.uid, user!!.userLocalPhoto, isBroadcast).subscribe(consumer, errorConsumer))
        } else*/ if (profilePhotoPath == null) {
            //show the thumgImg while getting full Image
            if (user!!.thumbImg != null) {
                Glide.with(this).load(user!!.thumbImg).into(profileFullScreen!!)
            }
            //start getting full image

        } else {
            //otherwise check if the image stored in device
            //if it's stored then show it
            if (FileUtils.isFileExists(profilePhotoPath)) {
                Glide.with(this).load(profilePhotoPath).into(profileFullScreen!!)
            } else {
                //otherwise download the image

                disposables.add(fireManager.downloadUserPhoto(user!!.uid, user!!.userLocalPhoto, isGroup).subscribe(consumer, errorConsumer))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_profile_photo, menu)
        //show edit profile button if the user is viewing his photo or if group admin wants to update group profile photo

      try {
          if (isGroup && FireManager.isAdmin(user!!.group.adminsUids) || !intent.hasExtra(IntentUtils.UID)) {
              menu.findItem(R.id.edit_profile_item).isVisible = true
          }else {
              menu.findItem(R.id.edit_profile_item).isVisible = false
          }
      }catch (e:Exception) {
          Log.e("@@TAG", "onCreateOptionsMenu: "+e.message )
      }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        } else if (item.itemId == R.id.edit_profile_item) {
            editProfilePhoto()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        loadImage(profilePhotoPath)
    }

    override fun onStop() {
        super.onStop()
        //free up resources and avoid memory leaks
        disposables.dispose()
    }

    private fun editProfilePhoto() {

        pickDialog()
    }
    fun pickDialog() {
        val colors = arrayOf("Select Image","Remove image")

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getString(R.string.select_any_one))
        builder.setItems(colors, DialogInterface.OnClickListener { dialog, which ->

            if (colors[which].equals("Select Image")){
                pickImages()
            }else if (colors[which].equals("Remove image")){

                AlertDialog.Builder(this)
                    .setMessage("Are you sure you want to remove image?")
                    .setCancelable(false)
                    .setPositiveButton(
                        "Yes"
                    ) { dialog, id ->

                        when {
                            isGroup -> {

                                var fileName:String
                                var ref: StorageReference
                                var file:Uri

                                val random = Random().nextInt(61) + 2000

                                fileName = "imageName"+random.toString()
                                ref = FireConstants.imageProfileRef.child(fileName)
                                file = getURLForResource(R.drawable.groupicon)

                                ref.putFileRx(file)

                                val updateMap: MutableMap<String, Any> = HashMap()

                                updateMap["photo"] = "null"
                                updateMap["thumbImg"] = "null"

                                RxFirebaseDatabase.updateChildren(
                                    FireConstants.groupsRef.child(
                                        user!!.uid
                                    ).child("info"), updateMap
                                )

                                try {
                                    profileFullScreen.setImageDrawable(AppCompatResources.getDrawable(this@ProfilePhotoActivity, R.drawable.group_icon))
                                    Toast.makeText(this@ProfilePhotoActivity, R.string.image_removed, Toast.LENGTH_SHORT).show()

                                } catch (e: Exception) {
                                }

                            }
                            isBroadcast -> {
                                groupManager.changeBrodcastImage(null.toString(), user!!.uid).subscribe({
                                    try {
                                        profileFullScreen.setImageDrawable(AppCompatResources.getDrawable(this@ProfilePhotoActivity, R.drawable.ic_broadcast_with_bg))

                                        disposables.add(checkAndDownloadUserPhoto(user).subscribe({ imageItem: ImageItem? ->
                                            Toast.makeText(this@ProfilePhotoActivity, R.string.image_removed, Toast.LENGTH_SHORT).show()

                                        }) { throwable: Throwable? ->Toast.makeText(this@ProfilePhotoActivity, R.string.image_removed, Toast.LENGTH_SHORT).show()
                                        })

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    //GroupEvent(SharedPreferencesManager.getPhoneNumber(), GroupEventTypes.GROUP_SETTINGS_CHANGED, null).createGroupEvent(user, null)


                                }, { throwable ->
                                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                                })

                            }
                            else -> {

                                fireManager.updateMyPhoto(null.toString()).subscribe({

                                    try {
                                        profileFullScreen.setImageDrawable(AppCompatResources.getDrawable(this@ProfilePhotoActivity, R.drawable.user_img))

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    Toast.makeText(this@ProfilePhotoActivity, R.string.image_removed, Toast.LENGTH_SHORT).show()

                                }, { throwable ->

                                })
                            }
                        }


                    }
                    .setNegativeButton("No", null)
                    .show()

            }
        })
        builder.show()
    }
    fun getURLForResource(resourceId: Int): Uri {
        //use BuildConfig.APPLICATION_ID instead of R.class.getPackage().getName() if both are not same
        return Uri.parse(
            "android.resource://" + R::class.java.getPackage().getName() + "/" + resourceId
        )
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)
            if (resultCode == Activity.RESULT_OK) {
                val resultUri = result.uri
                val file = DirManager.generateUserProfileImage()

                //it is not recommended to change IMAGE_QUALITY_COMPRESS as it may become
                //too big and this may cause the app to crash due to large thumbImg
                //therefore the thumb img may became un-parcelable through activities
                BitmapUtils.compressImage(resultUri.path, file, IMAGE_QUALITY_COMPRESS)
                when {
                    isGroup -> {
                        groupManager.changeGroupImage(file.path, user!!.uid).subscribe({
                            try {
                                Glide.with(this@ProfilePhotoActivity)
                                        .load(file)
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .into(profileFullScreen!!)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            GroupEvent(SharedPreferencesManager.getPhoneNumber(), GroupEventTypes.GROUP_SETTINGS_CHANGED, null).createGroupEvent(user, null)
                            Toast.makeText(this@ProfilePhotoActivity, R.string.image_changed, Toast.LENGTH_SHORT).show()

                        }, { throwable ->
                            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                        })

                    }
                    isBroadcast -> {
                        groupManager.changeBrodcastImage(file.path, user!!.uid).subscribe({
                            try {
                                Glide.with(this@ProfilePhotoActivity)
                                            .load(file)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .skipMemoryCache(true)
                                            .into(profileFullScreen!!)

                                //loadImage("")

                                //load user info once it's downloaded

                                //check if there is a new image for this user
                                //if yes ,download it and show it
                                //if (!isBroadcast)
                                disposables.add(checkAndDownloadUserPhoto(user).subscribe({ imageItem: ImageItem? ->
                                    Toast.makeText(this@ProfilePhotoActivity, R.string.image_changed, Toast.LENGTH_SHORT).show()

                                }) { throwable: Throwable? ->Toast.makeText(this@ProfilePhotoActivity, R.string.image_changed, Toast.LENGTH_SHORT).show()
                                })

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            //GroupEvent(SharedPreferencesManager.getPhoneNumber(), GroupEventTypes.GROUP_SETTINGS_CHANGED, null).createGroupEvent(user, null)


                        }, { throwable ->
                            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                        })

                    }
                    else -> {

                        fireManager.updateMyPhoto(file.path).subscribe({
                            //skip cache because the img name will still the same
                            //and glide will think this is same image,therefore it
                            //will still show the old image
                            try {
                                Glide.with(this@ProfilePhotoActivity)
                                        .load(file)
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .into(profileFullScreen!!)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            //
                            Toast.makeText(this@ProfilePhotoActivity, R.string.image_changed, Toast.LENGTH_SHORT).show()

                        }, { throwable ->

                        })
                    }
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, R.string.could_not_get_this_image, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun enablePresence() = false

    private fun pickImages() {
        CropImageRequest.getCropImageRequest().start(this)
    }


    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

}