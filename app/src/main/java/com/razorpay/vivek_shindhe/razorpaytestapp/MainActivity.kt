package com.razorpay.vivek_shindhe.razorpaytestapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.razorpay.PaymentResultListener
import com.razorpay.Razorpay
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    data class ResponseObject(
        var responseCode: Int,
        var responseResult: String?,
        var headers: Map<String,List<String>>,
    )

    private lateinit var razorpay: Razorpay
    private lateinit var btnSubmit: Button
    private lateinit var etCustomOptions: EditText
    private lateinit var progressBar: ProgressBar
    private val upiAutoPayApps = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnSubmit = findViewById(R.id.btn_pay)
        progressBar = findViewById(R.id.progress_bar)
        btnSubmit.setOnClickListener {
            createOrder()
        }
        etCustomOptions = findViewById(R.id.et_custom_options)
        razorpay = Razorpay(this)
        razorpay.setWebView(WebView(this))
        Razorpay.getAppsWhichSupportAutoPayIntent(this) { applicationList ->
            applicationList?.let {
                it.forEach { appDetail ->
                    upiAutoPayApps.add(appDetail.appName.toString())
                }
                val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
                alertDialogBuilder.setTitle("UPI AutoPay Supported Apps")
                alertDialogBuilder.setMessage(upiAutoPayApps.toString())
                alertDialogBuilder.show()
            }
        }
    }

    private fun createOrder(){
        progressBar.visibility = View.VISIBLE
        val body = if (etCustomOptions.text.toString().isEmpty()){
            """
            {
               "amount":700,
               "currency":"INR",
               "customer_id":"cust_M5GZesLOzHBQZg",
               "method":"upi",
               "token":{
                  "max_amount":700,
                  "frequency":"as_presented"
               },
               "receipt":"Receipt No. 1",
               "notes":{
                  "notes_key_1":"Tea, Earl Grey, Hot",
                  "notes_key_2":"Tea, Earl Grey... decaf."
               }
            }
        """.trimIndent()
        }else{
            etCustomOptions.text.toString()
        }
        val headers = HashMap<String, String>()
        val creds = "rzp_live_1z50GPkiSefZcn:oM5smlfVdJ2ErvtvHvNrFPv3"
        headers["content-type"] = "application/json"
        headers["authorization"] = "Basic ${Base64.encodeToString(creds.toByteArray(), Base64.DEFAULT)}"
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            val responseObject = makeApiCall("POST","https://api.razorpay.com/v1/orders", headers, body)

            handler.post {
                responseObject.responseResult?.let {
                    val orderResponsePayload = JSONObject(it)
                    val data = JSONObject("{\"currency\": \"INR\"}")
                    data.put("amount", "700")
                    data.put("contact", "9999999999")
                    data.put("email", "testing2eawhn@gmail.com")
                    data.put(
                        "order_id",
                        orderResponsePayload.getString("id")
                    ) // mandatory for UPI AutoPay payments

                    data.put(
                        "customer_id",
                        "cust_M5GZesLOzHBQZg"
                    ) // mandatory for UPI AutoPay payments

                    data.put("recurring", "preferred")
                    data.put("description", "Credits towards consultation")
                    data.put("method", "upi")
                    data.put("_[flow]", "intent")
                    data.put(
                        "upi_app_package_name",
                        "com.google.android.apps.nbu.paisa.user"
                    ) // pass package name that is returned in getAppsWhichSupportUpi and/or getAppsWhichSupportUpiAutoPay
                    razorpay.changeApiKey("rzp_live_1z50GPkiSefZcn")
                    razorpay.submit(data, object : PaymentResultListener {
                        override fun onPaymentSuccess(p0: String?) {
                            if (p0 != null) {
                                showAlert("success", p0)
                            }
                        }

                        override fun onPaymentError(p0: Int, p1: String?) {
                            if (p1 != null) {
                                showAlert("success", p1)
                            }
                        }
                    })
                    return@post
                }
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        razorpay.onActivityResult(requestCode, resultCode, data)
    }

    private fun showAlert(title: String, body: String){
        val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(body)
        alertDialogBuilder.show()
        progressBar.visibility = View.GONE

    }

    private fun makeApiCall(method: String,urlString: String,headers: Map<String, String>, data: String?): ResponseObject{

        var `is`: InputStream? = null
        val responseObject = ResponseObject(-1,null, emptyMap())
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpsURLConnection
            for ((key, value) in headers.entries) {
                conn.setRequestProperty(key, value)
            }
            conn.requestMethod = method
            if (data != null) {
                conn.doOutput = true
                conn.outputStream.write(data.toByteArray(StandardCharsets.UTF_8))
            }

            // default values
            // conn.setDoInput(true);
            //
            conn.connectTimeout = 15000

            // if all content is not loaded in 20 secs
            // set 0 to disable read timeout
            conn.readTimeout = 20000
            conn.connect()

            // if response code is needed..
            val status = conn.responseCode
            responseObject.responseCode = status
            `is` = if (status >= 400) {
                conn.errorStream
            } else {
                conn.inputStream
            }
            responseObject.headers = conn.headerFields
            responseObject.responseResult = readIt(`is`)
        } catch (e: Exception) {
        } finally {
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (e: Exception) { }
            }
        }
        return responseObject
    }

    @Throws(java.lang.Exception::class)
    private fun readIt(stream: InputStream): String {
        // Don't update to standard characters as it's support from API level 19
        val `in` = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val body = StringBuilder()
        while (true) {
            val line = `in`.readLine() ?: break
            body.append(line)
        }
        `in`.close()
        return body.toString()
    }
}