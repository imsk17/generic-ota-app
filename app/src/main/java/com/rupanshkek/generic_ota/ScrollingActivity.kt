/* Copyright 2019 Rupansh Sekar

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */


package com.rupanshkek.generic_ota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.JobIntentService
import br.com.simplepass.loadingbutton.customViews.CircularProgressButton
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.rupanshkek.generic_ota.Networking.checkNetwork
import com.rupanshkek.generic_ota.fetch_backends.Fetch
import com.rupanshkek.generic_ota.fetch_backends.JSONFetch
import com.rupanshkek.generic_ota.fetch_backends.XDAFetch
import kotlinx.android.synthetic.main.activity_scrolling.*
import kotlinx.coroutines.*
import java.security.InvalidParameterException
import kotlin.coroutines.CoroutineContext


class ScrollingActivity : AppCompatActivity(), CoroutineScope {
    private var doneNoti = false
    private lateinit var fabbut: CircularProgressButton

    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scrolling)
        mJob = Job()
        val fetchObject = when(resources.getString(R.string.fetchMode)) {
            "xda" -> XDAFetch(resources.getString(R.string.dlprefix), resources.getStringArray(R.array.devicearr))
            "json" -> JSONFetch(resources.getString(R.string.devicesJSON))
            else -> throw InvalidParameterException("Invalid Fetch Mode")
        }
        val updateExceptionHandler = CoroutineExceptionHandler { _, e ->
            when (e) {
                is InvalidDeviceException -> invalidDeviceDialog()
            }
        }
        fabbut = findViewById(R.id.fab)

        fab.setOnClickListener {
            if (!checkNetwork(this)) {
                MaterialDialog(this@ScrollingActivity).show {
                    icon(R.drawable.ic_no_wifi)
                    title(text = "No Internet Access")
                    message(text = "Turn on Cellular Data/WiFi to fetch updates!")
                    negativeButton(text = "Quit") { finish(); moveTaskToBack(true) }
                    onDismiss { finish(); moveTaskToBack(true) }
                }

                lat_button.visibility = INVISIBLE
                lat_zip.visibility = INVISIBLE
                dev_info.visibility = INVISIBLE
            }

            fabbut.startAnimation()
            Toast.makeText(this@ScrollingActivity, "Checking for updates!", Toast.LENGTH_SHORT).show()
            checkUpdate(fetchObject, updateExceptionHandler)

            if (!doneNoti) {
                JobIntentService.enqueueWork(this, UpdateNotificationJob::class.java, 1, Intent())
                doneNoti = true
            }
        }

        fab.performClick()

    }

    private fun changeCard(id: Int, arrow: Int){
        val card = findViewById<androidx.cardview.widget.CardView>(id)
        val imgview = findViewById<ImageView>(arrow)
        val fromAngle: Float

        if (card.visibility == GONE){
            card.visibility = VISIBLE
            fromAngle = 0f
        } else {
            card.visibility = GONE
            fromAngle = 180f
        }

        val anim = RotateAnimation(fromAngle, -(fromAngle-180), Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.interpolator = LinearInterpolator()
        anim.repeatCount = 0
        anim.duration = 300
        anim.isFillEnabled = true
        anim.fillAfter = true
        imgview.startAnimation(anim)
    }

    fun showInfo(@Suppress("UNUSED_PARAMETER") view: View){
        changeCard(R.id.rominfo, R.id.dev_info_ar)
    }

    fun showLatZip(@Suppress("UNUSED_PARAMETER") view: View){
        changeCard(R.id.latestzip, R.id.lat_zip_ar)
    }

    // Checks for an update
    private fun checkUpdate(fetchObject: Fetch, exceptionHandler: CoroutineExceptionHandler) {
        launch(exceptionHandler) {
            val latestButton = findViewById<TextView>(R.id.lat_button)
            latestButton.visibility = INVISIBLE
            val latName = findViewById<TextView>(R.id.lat_name)
            val device = findViewById<TextView>(R.id.device)
            val buildDate = findViewById<TextView>(R.id.builddt)
            val obuildDate = findViewById<TextView>(R.id.build_dt)
            val maintainer = findViewById<TextView>(R.id.maintainer_name)

            val romDl = withContext(Dispatchers.Default) {
                fetchObject.fetchData(android.os.Build.DEVICE)
            }?: throw InvalidDeviceException("Device Not Found!")
            val latestRes = fetchObject.getLatest(romDl)

            latestButton.visibility = VISIBLE
            latestButton.setOnClickListener{
                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(romDl.download)
                startActivity(openURL)
            }

            latName.text = romDl.zipName
            buildDate.text = resources.getString(R.string.devbuild).format(latestRes.second)
            device.text = resources.getString(R.string.device).format(android.os.Build.DEVICE)
            obuildDate.text = resources.getString(R.string.curbuild).format(latestRes.first)
            maintainer.text = romDl.maintainer

            xda_thread.setOnClickListener {
                MaterialDialog(this@ScrollingActivity).show {
                    title(text = "Are You sure?")
                    message(text = "This will open a browser window")
                    positiveButton(text = "Yes") {
                        val openURL = Intent(Intent.ACTION_VIEW)
                        openURL.data = Uri.parse(romDl.xdaThread)
                        startActivity(openURL)
                    }
                    negativeButton(text = "Cancel") { }
                }
            }

            if (latestRes.first < latestRes.second) {
                MaterialDialog(this@ScrollingActivity).show {
                    icon(R.drawable.ic_update)
                    title(text = "Update available!")
                    message(text = "Latest Build: ${latestRes.second}\nDownload?")
                    positiveButton(text = "Yes") {
                        val openURL = Intent(Intent.ACTION_VIEW)
                        openURL.data = Uri.parse(romDl.download)
                        startActivity(openURL)
                    }
                    negativeButton(text = "Cancel") { }
                }
            } else {
                MaterialDialog(this@ScrollingActivity).show {
                    icon(R.drawable.ic_checkmark)
                    title(text = "You are up-to-date!")
                    negativeButton(text = "Close") { }
                }
            }

            fabbut.revertAnimation()
        }
    }

    private fun invalidDeviceDialog() {
        MaterialDialog(this).show {
            icon(R.drawable.ic_warning)
            title(text = "Invalid Device")
            message(text = "Your Device is not supported!")
            negativeButton(text = "Quit") { finish(); moveTaskToBack(true) }
            onDismiss { finish(); moveTaskToBack(true) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_scrolling, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mJob.cancel()
        fabbut.dispose()
    }
}
