package net.znordic.scanandlaunch

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity :  Scanner.DatawedgeListener, AppCompatActivity() {

    var scanner : Scanner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        scanner = Scanner(this)

        val i : Intent = intent
        if(i.action == scanner!!.APP_PACKAGE_NAME+".SCAN") {
            val barcode : String = i!!.getStringExtra(scanner!!.DATA_STRING_TAG) as String
            val surl = "https://www.xn--ob-eka.se/search?CMS_SearchString="+barcode                 //modify URL to append barcode data as parameter
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(surl))
            startActivity(i)
        } else {

            //first start to modify the Launcher profile and associate Chrome to the profile and enable barcode input
            //requires Datawedge 7.1
            scanner!!.registerReceiver()
            scanner!!.createScannerProfile()
        }

    }

    override fun onDatawedgeEvent(intent: Intent?) {
        Log.d(Scanner.TAG, "My onDatawedgeEvent")

        //val tv = findViewById<TextView>(R.id.textView)
        //tv.setText(scanner!!.getBarcode(intent))

    }
}