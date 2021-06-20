package org.wBHARATmeet.activities

import android.content.Intent
import android.os.Bundle
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_agree_privacy_policy.*
import org.wBHARATmeet.R
import org.wBHARATmeet.activities.authentication.AuthenticationActivity
import org.wBHARATmeet.activities.main.MainActivity
import org.wBHARATmeet.activities.setup.SetupUserActivity
import org.wBHARATmeet.utils.DetachableClickListener
import org.wBHARATmeet.utils.PermissionsUtil
import org.wBHARATmeet.utils.SharedPreferencesManager
import org.wBHARATmeet.utils.network.FireManager


class AgreePrivacyPolicyActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 451

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agree_privacy_policy)

        setTitle("Privacy Policy")

        webView.webViewClient = WebViewClient()
        webView.loadUrl(getString(R.string.privacy_policy_link))
        webView.settings.javaScriptEnabled = true
        webView.settings.setSupportZoom(true)
        chb_agree.text = "By Checking this, You agree to the collection and use of information in accordance with this Privacy Policy"
        btn_agree.setOnClickListener {
            if (chb_agree.isChecked) {
                SharedPreferencesManager.setAgreedToPrivacyPolicy(true)
                if (!FireManager.isLoggedIn())
                    startLoginActivity()
                else
                    startNextActivity()
            } else {
                Toast.makeText(this@AgreePrivacyPolicyActivity, "Please agree with privacy policy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PermissionsUtil.permissions, PERMISSION_REQUEST_CODE)
    }

    private fun startLoginActivity() {
        val intent = Intent(this, AuthenticationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun startNextActivity() {
        if (SharedPreferencesManager.isUserInfoSaved()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, SetupUserActivity::class.java)
            startActivity(intent)
            finish()
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionsUtil.permissionsGranted(grantResults)) {
            if (!FireManager.isLoggedIn())
                startLoginActivity()
            else
                startNextActivity()
        } else
            showAlertDialog()
    }

    private fun showAlertDialog() {

        val positiveClickListener = DetachableClickListener.wrap { dialogInterface, i -> requestPermissions() }

        val negativeClickListener = DetachableClickListener.wrap { dialogInterface, i -> finish() }


        val builder = AlertDialog.Builder(this)
                .setTitle(R.string.missing_permissions)
                .setMessage(R.string.you_have_to_grant_permissions)
                .setPositiveButton(R.string.ok, positiveClickListener)
                .setNegativeButton(R.string.no_close_the_app, negativeClickListener)
                .create()

        //avoid memory leaks
        positiveClickListener.clearOnDetach(builder)
        negativeClickListener.clearOnDetach(builder)
        builder.show()
    }
}
