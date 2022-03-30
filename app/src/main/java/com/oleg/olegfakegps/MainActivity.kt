package com.oleg.olegfakegps

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oleg.olegfakegps.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mockLocationReceiver: ApplyMockBroadcastReceiver? = null

    var webAppInterface: WebAppInterface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        context = applicationContext
        webView = findViewById(R.id.wvMaps)
        webAppInterface = WebAppInterface(this, this)
        alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
        sharedPref = getSharedPreferences(sharedPrefKey, MODE_PRIVATE)
        editor = sharedPref?.edit()

        editTextLat = findViewById(R.id.edtLatitude)
        editTextLng = findViewById(R.id.edtLongitude)

        registerMockLocationReceiver()
        setOnClickListener()
        setWebView()

        try {
            val pInfo = this.packageManager.getPackageInfo(packageName, 0)
            currentVersion = pInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        checkSharedPrefs()
        howManyTimes = sharedPref?.getString("howManyTimes", "1")!!
            .toInt()
        timeInterval = sharedPref?.getString("timeInterval", "10")!!
            .toInt()
        try {
            lat = sharedPref?.getString("lat", "")!!.toDouble()
            lng = sharedPref?.getString("lng", "")!!.toDouble()
            editTextLat?.setText(lat.toString())
            editTextLng?.setText(lng.toString())
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        editTextLat?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (!editTextLat?.text.toString().isEmpty() && editTextLat?.text
                        .toString() != "-"
                ) {
                    if (srcChange != SourceChange.CHANGE_FROM_MAP) {
                        try {
                            lat = editTextLat?.text.toString()
                                .toDouble()
                            if (lng == null) return
                            setLatLng(
                                editTextLat?.text.toString(),
                                lng.toString(),
                                SourceChange.CHANGE_FROM_EDITTEXT
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
            }
        })
        editTextLng?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (editTextLng?.text.toString().isNotEmpty() && editTextLng?.text
                        .toString() != "-"
                ) {
                    if (srcChange != SourceChange.CHANGE_FROM_MAP) {
                        try {
                            lng = editTextLng?.text.toString()
                                .toDouble()
                            if (lat == null) return
                            setLatLng(
                                lat.toString(),
                                editTextLng?.text.toString(),
                                SourceChange.CHANGE_FROM_EDITTEXT
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        endTime = sharedPref?.getLong("endTime", 0)!!
        if (pendingIntent != null && endTime > System.currentTimeMillis()) {
            changeButtonToStop()
        } else {
            endTime = 0
            editor?.putLong("endTime", 0)
            editor?.commit()
        }
    }

    private fun setWebView() {
        webView?.let {
            it.settings.javaScriptEnabled = true
            it.webChromeClient = WebChromeClient()
            it.settings.javaScriptCanOpenWindowsAutomatically = true
            it.addJavascriptInterface(webAppInterface!!, "Android")
            it.loadUrl("file:///android_asset/map.html")
        }
    }

    private fun setOnClickListener() {
        with(binding) {
            btnApplyLocation.setOnClickListener { applyLocation() }
            btnHelp.setOnClickListener {
                val myIntent = Intent(baseContext, MoreActivity::class.java)
                startActivity(myIntent)
            }
        }
    }

    private fun registerMockLocationReceiver() {
        mockLocationReceiver = object : ApplyMockBroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val lat = sharedPref.getString("lat", "0")!!.toDouble()
                    val lng = sharedPref.getString("lng", "0")!!.toDouble()
                    exec(lat, lng)
                    if (!hasEnded()) {
                        setAlarm(timeInterval)
                    } else {
                        stopMockingLocation()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
        registerReceiver(mockLocationReceiver, IntentFilter())
    }

    private fun unregisterMockLocationReceiver() {
        unregisterReceiver(mockLocationReceiver)
        mockLocationReceiver = null
    }

    /**
     * Changes the button to Apply, and its behavior.
     */
    fun changeButtonToApply() {
        with(binding.btnApplyLocation) {
            text = context!!.resources.getString(R.string.ActivityMain_Apply)
            setOnClickListener { applyLocation() }
        }
    }

    /**
     * Changes the button to Stop, and its behavior.
     */
    fun changeButtonToStop() {
        with(binding.btnApplyLocation) {
            text = context!!.resources.getString(R.string.ActivityMain_Stop)
            setOnClickListener { stopMockingLocation() }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        toast(context!!.resources.getString(R.string.ApplyMockBroadRec_Closed))
        stopMockingLocation()
        unregisterMockLocationReceiver()
    }

    public override fun onPause() {
        super.onPause()
        if (isFinishing) {
            toast(context!!.resources.getString(R.string.ApplyMockBroadRec_Closed))
            stopMockingLocation()
        }
    }

    /**
     * Check and reinitialize shared preferences in case of problem.
     */
    fun checkSharedPrefs() {
        val version = sharedPref!!.getInt("version", 0)
        val lat = sharedPref!!.getString("lat", "N/A")
        val lng = sharedPref!!.getString("lng", "N/A")
        val howManyTimes = sharedPref!!.getString("howManyTimes", "N/A")
        val timeInterval = sharedPref!!.getString("timeInterval", "N/A")
        val endTime = sharedPref!!.getLong("endTime", 0)
        if (version != currentVersion) {
            editor!!.putInt("version", currentVersion)
            editor!!.commit()
        }
        try {
            lat!!.toDouble()
            lng!!.toDouble()
            howManyTimes!!.toDouble()
            timeInterval!!.toDouble()
        } catch (e: NumberFormatException) {
            editor!!.clear()
            editor!!.putString("lat", lat)
            editor!!.putString("lng", lng)
            editor!!.putInt("version", currentVersion)
            editor!!.putString("howManyTimes", "1")
            editor!!.putString("timeInterval", "10")
            editor!!.putLong("endTime", 0)
            editor!!.commit()
            e.printStackTrace()
        }
    }

    /**
     * Apply a mocked location, and start an alarm to keep doing it if howManyTimes is > 1
     * This method is called when "Apply" button is pressed.
     */
    protected fun applyLocation() {
        if (latIsEmpty() || lngIsEmpty()) {
            toast(context!!.resources.getString(R.string.MainActivity_NoLatLong))
            return
        }
        lat = editTextLat!!.text.toString().toDouble()
        lng = editTextLng!!.text.toString().toDouble()
        toast(context!!.resources.getString(R.string.MainActivity_MockApplied))
        endTime = System.currentTimeMillis() + (howManyTimes - 1) * timeInterval * 1000
        editor!!.putLong("endTime", endTime)
        editor!!.commit()
        changeButtonToStop()
        try {
            mockNetwork = MockLocationProvider(LocationManager.NETWORK_PROVIDER, context)
            mockGps = MockLocationProvider(LocationManager.GPS_PROVIDER, context)
        } catch (e: SecurityException) {
            e.printStackTrace()
            toast(context!!.resources.getString(R.string.ApplyMockBroadRec_MockNotApplied))
            stopMockingLocation()
            return
        }
        exec(lat!!, lng!!)
        if (!hasEnded()) {
            toast(context!!.resources.getString(R.string.MainActivity_MockLocRunning))
            setAlarm(timeInterval)
        } else {
            stopMockingLocation()
        }
    }


    /**
     * Check if mocking location should be stopped
     *
     * @return true if it has ended
     */
    fun hasEnded(): Boolean {
        return if (howManyTimes == KEEP_GOING) {
            false
        } else System.currentTimeMillis() > endTime
    }

    /**
     * Sets the next alarm accordingly to <seconds>
     *
     * @param seconds number of seconds
    </seconds> */
    fun setAlarm(seconds: Int) {
        serviceIntent = Intent(context, ApplyMockBroadcastReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            context,
            SCHEDULE_REQUEST_CODE,
            serviceIntent!!,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager!!.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + seconds * 1000,
                        pendingIntent
                    )
                } else {
                    alarmManager!!.setExact(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + timeInterval * 1000,
                        pendingIntent
                    )
                }
            } else {
                alarmManager!![AlarmManager.RTC, System.currentTimeMillis() + timeInterval * 1000] =
                    pendingIntent
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shows a toast
     */
    fun toast(str: String?) {
        Toast.makeText(context, str, Toast.LENGTH_LONG).show()
    }

    /**
     * Returns true editTextLat has no text
     */
    fun latIsEmpty(): Boolean {
        return editTextLat!!.text.toString().isEmpty()
    }

    /**
     * Returns true editTextLng has no text
     */
    fun lngIsEmpty(): Boolean {
        return editTextLng!!.text.toString().isEmpty()
    }

    /**
     * Stops mocking the location.
     */
    fun stopMockingLocation() {
        changeButtonToApply()
        editor!!.putLong("endTime", System.currentTimeMillis() - 1)
        editor!!.commit()
        if (pendingIntent != null) {
            alarmManager!!.cancel(pendingIntent)
            toast(context!!.resources.getString(R.string.MainActivity_MockStopped))
        }
        if (mockNetwork != null) mockNetwork!!.shutdown()
        if (mockGps != null) mockGps!!.shutdown()
    }


    /**
     * Set a mocked location.
     *
     * @param lat latitude
     * @param lng longitude
     */
    fun exec(lat: Double, lng: Double) {
        try {
            //MockLocationProvider mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockNetwork!!.pushLocation(lat, lng)
            //MockLocationProvider mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
            mockGps!!.pushLocation(lat, lng)
        } catch (e: Exception) {
            toast(context!!.resources.getString(R.string.MainActivity_MockNotApplied))
            changeButtonToApply()
            e.printStackTrace()
            return
        }
    }

    companion object {
        const val sharedPrefKey = "cl.coders.mockposition.sharedpreferences"
        const val KEEP_GOING = 0
        private const val SCHEDULE_REQUEST_CODE = 1

        @JvmField
        var serviceIntent: Intent? = null

        @JvmField
        var pendingIntent: PendingIntent? = null

        @JvmField
        var alarmManager: AlarmManager? = null

        var webView: WebView? = null
        var editTextLat: EditText? = null
        var editTextLng: EditText? = null
        var context: Context? = null

        @JvmField
        var sharedPref: SharedPreferences? = null

        @JvmField
        var editor: SharedPreferences.Editor? = null
        var lat: Double? = null
        var lng: Double? = null

        @JvmField
        var timeInterval = 0

        @JvmField
        var howManyTimes = 0
        var endTime: Long = 0
        var currentVersion = 0
        private var mockNetwork: MockLocationProvider? = null
        private var mockGps: MockLocationProvider? = null
        var srcChange = SourceChange.NONE

        /**
         * returns latitude
         *
         * @return latitude
         */
        @JvmStatic
        fun getLat(): String {
            return editTextLat!!.text.toString()
        }

        /**
         * returns latitude
         *
         * @return latitude
         */
        @JvmStatic
        fun getLng(): String {
            return editTextLng!!.text.toString()
        }

        /**
         * Sets latitude and longitude
         *
         * @param mLat      latitude
         * @param mLng      longitude
         * @param srcChange CHANGE_FROM_EDITTEXT or CHANGE_FROM_MAP, indicates from where comes the change
         */
        @JvmStatic
        fun setLatLng(mLat: String, mLng: String, srcChange: SourceChange) {
            lat = mLat.toDouble()
            lng = mLng.toDouble()
            if (srcChange == SourceChange.CHANGE_FROM_EDITTEXT) {
                webView!!.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");")
            } else if (srcChange == SourceChange.CHANGE_FROM_MAP) {
                Companion.srcChange = SourceChange.CHANGE_FROM_MAP
                editTextLat!!.setText(mLat)
                editTextLng!!.setText(mLng)
                Companion.srcChange = SourceChange.NONE
            }
            editor!!.putString("lat", mLat)
            editor!!.putString("lng", mLng)
            editor!!.commit()
        }
    }
}