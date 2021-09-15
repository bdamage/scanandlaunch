package net.znordic.scanandlaunch

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import java.util.*

interface ScannerEvents {
    fun onDatawedgeEvent(intent: Intent) {
        Log.d(Scanner.TAG, "onDatawedgeEvent - not implemented")
    }
}

class Scanner(activity: Activity) :  ScannerEvents, BroadcastReceiver() {
    var APP_PACKAGE_NAME = activity.applicationContext.packageName
    val DATA_STRING_TAG = "com.symbol.datawedge.data_string"

    protected var barcodeScanned = false
    private var barcodeScannedStarted = false
    private var scanTime: Long = 0
    var scannerList: ArrayList<Bundle>? = null

    // http://techdocs.zebra.com/datawedge/6-5/guide/api/registerfornotification/
    var mDatawedgeEvent: DatawedgeListener? = null
    var mContext: Context = activity.applicationContext

    fun registerReceiver(broadcastReceiver: BroadcastReceiver?) {
        val filter = IntentFilter()
        filter.addAction(ACTION_ENUMERATEDSCANNERLIST)
        filter.addAction(APP_PACKAGE_NAME)
        filter.addAction(NOTIFICATION_ACTION) // SCANNER_STATUS
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        mContext.registerReceiver(broadcastReceiver, filter)
    }

    fun unregisterReceiver(broadcastReceiver: BroadcastReceiver?) {
       mContext.unregisterReceiver(broadcastReceiver)
   }

    fun unregisterReceiver() {
        Log.d(TAG, "UnregisterReceiver")
        // Register for notifications - SCANNER_STATUS
        val b = Bundle()
        b.putString("com.symbol.datawedge.api.APPLICATION_NAME", APP_PACKAGE_NAME)
        b.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", "SCANNER_STATUS")
        val i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.UNREGISTER_REGISTER_FOR_NOTIFICATION", b) //(1)
        mContext.sendBroadcast(i)
        mContext.unregisterReceiver(this)
    }

    fun registerReceiver() {
        Log.d(TAG, "RegisterReceiver")
        val filter = IntentFilter()
        filter.addAction(ACTION_ENUMERATEDSCANNERLIST)
        filter.addAction(APP_PACKAGE_NAME)
        filter.addAction(NOTIFICATION_ACTION) // SCANNER_STATUS
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        mContext.registerReceiver(this, filter)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Action: $action")
        if (action == "com.symbol.datawedge.api.RESULT_ACTION") {
            if (intent.hasExtra("com.symbol.datawedge.api.RESULT_ENUMERATE_SCANNERS")) {
                scannerList =
                    intent.getSerializableExtra("com.symbol.datawedge.api.RESULT_ENUMERATE_SCANNERS") as ArrayList<Bundle>?
                if (scannerList != null && scannerList!!.size > 0) {
                    for (bunb in scannerList!!) {
                        val entry = arrayOfNulls<String>(4)
                        entry[0] = bunb.getString("SCANNER_NAME")
                        entry[1] = bunb.getBoolean("SCANNER_CONNECTION_STATE").toString() + ""
                        entry[2] = bunb.getInt("SCANNER_INDEX").toString() + ""
                        entry[3] = bunb.getString("SCANNER_IDENTIFIER")
                        Log.d(
                            TAG,
                            "Scanner:" + entry[0] + " Connection:" + entry[1] + " Index:" + entry[2] + " ID:" + entry[3]
                        )
                    }

                    mDatawedgeEvent!!.onDatawedgeEvent(intent)
                }
            }
            if (intent.hasExtra("com.symbol.datawedge.api.RESULT_GET_VERSION_INFO")) {
                var text: String? = null
                var SimulScanVersion : String = "Not supported"
                var ScannerFirmware : Array<String> = arrayOf("")
                val res =
                    intent.getBundleExtra("com.symbol.datawedge.api.RESULT_GET_VERSION_INFO")
                val DWVersion = res!!.getString("DATAWEDGE")
                val BarcodeVersion = res.getString("BARCODE_SCANNING")
                val DecoderVersion = res.getString("DECODER_LIBRARY")
                val bundleKeySet = res.keySet() // string key set
                for (key in bundleKeySet) { // traverse and print pairs
                    Log.i(TAG, key + "  : " + res[key])
                }
                if (res.containsKey("SCANNER_FIRMWARE")) {
                    ScannerFirmware = res.getStringArray("SCANNER_FIRMWARE") as Array<String>
                }
                if (res.containsKey("SIMULSCAN")) {
                    SimulScanVersion = res.getString("SIMULSCAN") as String
                }
                text = "DataWedge:$DWVersion\nDecoderLib:$DecoderVersion\nFirmware:"
                if (ScannerFirmware != null) {
                    for (s in ScannerFirmware) {
                        text += """
                            
                            $s
                            """.trimIndent()
                    }
                }
                text += "\nBarcodescan:$BarcodeVersion\nSimulscan:$SimulScanVersion"
                Log.d(TAG, text)
            }
        }
        //  Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        if (action == APP_PACKAGE_NAME) {
            mDatawedgeEvent!!.onDatawedgeEvent(intent);
        } else if (action == NOTIFICATION_ACTION) {
            if (intent.hasExtra(NOTIFICATION)) {
                val b = intent.getBundleExtra(NOTIFICATION)
                val NOTIFICATION_TYPE = b!!.getString("NOTIFICATION_TYPE")
                if (NOTIFICATION_TYPE != null) {
                    when (NOTIFICATION_TYPE) {
                        NOTIFICATION_TYPE_SCANNER_STATUS -> {
                            Log.d(
                                TAG,
                                "SCANNER_STATUS: status: " + b.getString("STATUS") + ", profileName: " + b.getString(
                                    "PROFILE_NAME"
                                )
                            )
                            val scanner_status = b.getString("STATUS")
                            if (scanner_status.equals("WAITING", ignoreCase = true)) {
                                // check if barcode scan was started and timed out
                                if (!barcodeScanned && barcodeScannedStarted && System.currentTimeMillis() - scanTime >= BEAM_TIMEOUT) {
                                    //Toast.makeText(getApplicationContext(), "SCAN TIMEOUT", Toast.LENGTH_SHORT).show();
                                }
                            }
                            if (scanner_status.equals("SCANNING", ignoreCase = true)) {
                                barcodeScanned = false
                                barcodeScannedStarted = true
                                scanTime = System.currentTimeMillis()
                            }
                        }
                        NOTIFICATION_TYPE_PROFILE_SWITCH -> Log.d(
                            TAG,
                            "PROFILE_SWITCH: profileName: " + b.getString("PROFILE_NAME") + ", profileEnabled: " + b.getBoolean(
                                "PROFILE_ENABLED"
                            )
                        )
                        NOTIFICATION_TYPE_CONFIGURATION_UPDATE -> {
                        }
                    }
                }
            }
        }
    }

    fun enumerateScanners() {
        val i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.ENUMERATE_SCANNERS", "")
        mContext.sendBroadcast(i)
    }

    val versions: Unit
        get() {
            val i = Intent()
            i.action = "com.symbol.datawedge.api.ACTION"
            i.putExtra("com.symbol.datawedge.api.GET_VERSION_INFO", "")
            mContext.sendBroadcast(i)
        }

    //can put a list "ADF,BDF"
    val config: Unit
        get() {
            val bMain = Bundle()
            bMain.putString("PROFILE_NAME", PROFILENAME)
            val bConfig = Bundle()
            val pluginName = ArrayList<Bundle>()
            val pluginInternal = Bundle()
            pluginInternal.putString("PLUGIN_NAME", "BARCODE") //can put a list "ADF,BDF"
            pluginInternal.putString("OUTPUT_PLUGIN_NAME", "BARCODE")
            pluginName.add(pluginInternal)
            bConfig.putParcelableArrayList("PROCESS_PLUGIN_NAME", pluginName)
            bMain.putBundle("PLUGIN_CONFIG", bConfig)
            val i = Intent()
            i.action = "com.symbol.datawedge.api.ACTION"
            i.putExtra("com.symbol.datawedge.api.GET_CONFIG", bMain)
            mContext.sendBroadcast(i)
        }


    /******************************************
     Multiple plugins setup with one signle intent
     requires Datawedge 7.1 or newer
     *******************************************/
    fun createScannerProfile() {
        val bMain = Bundle()
        //bMain.putString("PROFILE_NAME", PROFILENAME)
        bMain.putString("PROFILE_NAME", PROFILENAME)
        bMain.putString("PROFILE_ENABLED", "true")
        bMain.putString("CONFIG_MODE", "UPDATE")


        /*******************************************
            First set up keystroke output to false
         ******************************************/
        val bConfigKey = Bundle()
        val bParamsKey = Bundle()
        bConfigKey.putString("PLUGIN_NAME", "KEYSTROKE")
        bParamsKey.putString("keystroke_output_enabled", "false")
        bConfigKey.putBundle("PARAM_LIST", bParamsKey)

        /*******************************************
        Set up Datawedge to send an broadcast Intent
         ******************************************/
        val bConfigIntent = Bundle()
        val bParamsIntent = Bundle()
        bConfigIntent.putString("PLUGIN_NAME", "INTENT")
        bParamsIntent.putString("intent_output_enabled", "true")
        bParamsIntent.putString("intent_action", APP_PACKAGE_NAME+".SCAN")
        //bParamsIntent.putString("intent_category", "android.intent.category.DEFAULT")
                //bParamsIntent.putString("intent_delivery", "1") //Broadcast the intent
        bConfigIntent.putBundle("PARAM_LIST", bParamsIntent)

        /*******************************************
         Set up Barcode Reader parameters
         *******************************************/
        val bConfigBarcode = Bundle()
        val bParamsBarcode = Bundle()
        bConfigBarcode.putString("PLUGIN_NAME", "BARCODE")
        bParamsBarcode.putString("scanner_selection", "auto") //!important if decoders are being set etc
        bParamsBarcode.putString("scanner_input_enabled", "true")
        //bParamsBarcode.putString("decoder_microqr", "true");
        bConfigBarcode.putBundle("PARAM_LIST", bParamsBarcode)



        /*****************************************************************
         Associate (activate) the Datawedge profile to a app package name
         *****************************************************************/
        val bundleApp = Bundle()

        bundleApp.putString("PACKAGE_NAME", APP_PACKAGE_NAME)
        bundleApp.putStringArray("ACTIVITY_LIST", arrayOf("*"))

        val bundleApp2 = Bundle()
        bundleApp2.putString("PACKAGE_NAME", "com.android.chrome")
        bundleApp2.putStringArray("ACTIVITY_LIST", arrayOf("*"))

        val bundleApp3 = Bundle()
        bundleApp3.putString("PACKAGE_NAME", "com.android.launcher3")
        bundleApp3.putStringArray("ACTIVITY_LIST", arrayOf("*"))


// NEXT APP_LIST BUNDLE(S) INTO THE MAIN BUNDLE
        bMain.putParcelableArray(
            "APP_LIST", arrayOf(
                bundleApp,
                bundleApp2,
                bundleApp3
            )
        )


        val bundlePluginConfig: ArrayList<Bundle> = ArrayList()
        bundlePluginConfig.add(bConfigIntent)
        bundlePluginConfig.add(bConfigBarcode)
        bundlePluginConfig.add(bConfigKey)
        bMain.putParcelableArrayList("PLUGIN_CONFIG", bundlePluginConfig)


        var i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.SET_CONFIG", bMain)
        mContext.sendBroadcast(i)

        // Register for notifications - SCANNER_STATUS
        val b = Bundle()
        b.putString("com.symbol.datawedge.api.APPLICATION_NAME", APP_PACKAGE_NAME)
        b.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", "SCANNER_STATUS")
        i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION", b) //(1)
        mContext.sendBroadcast(i)
    }



    fun softTrigger() {
        val i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.SOFT_SCAN_TRIGGER", "START_SCANNING")

        // send the intent to DataWedge
        mContext.sendBroadcast(i)
    }

    fun enableDatawedge(active: Boolean) {
        val i = Intent()
        i.action = "com.symbol.datawedge.api.ACTION"
        i.putExtra("com.symbol.datawedge.api.ENABLE_DATAWEDGE", active)
    }

    fun getBarcode(i : Intent?) : String {
        return i!!.getStringExtra(DATA_STRING_TAG) as String
//        var type: String = i.getStringExtra(LABEL_TYPE)
  //      var source: String = i.getStringExtra(SOURCE_TAG)
    }

    // Container Activity must implement this interface
    interface DatawedgeListener {
        fun onDatawedgeEvent(intent: Intent?)
    }

    companion object {
        const val TAG = "ScannerMgr"
        const val PROFILENAME = "Launcher"
        private const val BEAM_TIMEOUT: Long = 2000
        const val ACTION_ENUMERATEDSCANNERLIST = "com.symbol.datawedge.api.ACTION_ENUMERATEDSCANNERLIST"
        const val KEY_ENUMERATEDSCANNERLIST = "DWAPI_KEY_ENUMERATEDSCANNERLIST"
        const val NOTIFICATION = "com.symbol.datawedge.api.NOTIFICATION"
        const val NOTIFICATION_ACTION = "com.symbol.datawedge.api.NOTIFICATION_ACTION"
        const val NOTIFICATION_TYPE_SCANNER_STATUS = "SCANNER_STATUS"
        const val NOTIFICATION_TYPE_PROFILE_SWITCH = "PROFILE_SWITCH"
        const val NOTIFICATION_TYPE_CONFIGURATION_UPDATE = "CONFIGURATION_UPDATE"

        const val LABEL_TYPE = "com.symbol.datawedge.label_type"
        const val SOURCE_TAG = "com.symbol.datawedge.source"
        const val DECODE_DATA_TAG = "com.symbol.datawedge.decode_data"
    }

    init {
        mContext = activity.applicationContext
        try {
            mDatawedgeEvent = activity as DatawedgeListener
        } catch (e: ClassCastException) {
            throw ClassCastException(
                this.toString()
                        + " must implement DatawedgeListener"
            )
        }

        APP_PACKAGE_NAME = mContext.packageName
        registerReceiver()
    }
}