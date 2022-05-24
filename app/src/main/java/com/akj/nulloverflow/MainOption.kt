package com.akj.nulloverflow

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.lang.UCharacter.JoiningGroup.TAH
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.akj.nulloverflow.databinding.ActivityMainOptionBinding
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.create
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "MainOptionTAG"

class MainOption : AppCompatActivity() {

    private val binding by lazy { ActivityMainOptionBinding.inflate(layoutInflater) }
    private var userEmail: String? = null
    private var userName: String? = null
    private val dataFormat = SimpleDateFormat("yyyy-MM-dd/hh:mm:ss", Locale.KOREA)
    //private lateinit var bluetooth: Bluetooth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //Log.i(TAG, "bluetooth_scanning에서 받은 문자열: ${intent.getStringExtra("userEmail")}")
        //Log.i(TAG, "bluetooth_scanning에서 받은 문자열: ${intent.getStringExtra("userName")}")

        AWSMobileClient.getInstance().initialize(applicationContext, object: com.amazonaws.mobile.client.Callback<UserStateDetails> {
            override fun onResult(result: UserStateDetails?) {
                if(result?.userState == UserState.SIGNED_IN) {
                    userEmail = AWSMobileClient.getInstance().userAttributes["email"].toString()
                    userName = AWSMobileClient.getInstance().userAttributes["name"].toString()
                    Log.i(TAG, "userEmail is / $userEmail")
                    Log.i(TAG, "userName is / $userName")

                    runOnUiThread {
                        binding.userId.text = userEmail
                        binding.userName.text = userName
                    }
                }
            }

            override fun onError(e: Exception?) {
                Log.i(TAG, "AWSMobileClient Error / $e")
            }
        })

        //userEmail = intent.getStringExtra("userEmail").toString()
        //userName = intent.getStringExtra("userName").toString()

        //이메일 텍스트 길이가 길 수 있으므로 흐르기 효과를 주기위해
        binding.userId.isSelected = true

        /*
        //2022-05-25
        bluetooth = intent.getSerializableExtra("bluetooth") as Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), bluetooth_scanning.BLUETOOTH_SCAN_PERMISSION)
        }
        Log.i(TAG, "전달받은 bluetooth Serializable객체 / $bluetooth")
        Log.i(TAG, "전달받은 객체의 값(device.name) / ${bluetooth.bluetoothDevice.name}")
        Log.i(TAG, "전달받은 객체의 값(device.address) / ${bluetooth.bluetoothDevice.address}")
        Log.i(TAG, "전달받은 객체의 값(BluetoothGatt) / ${bluetooth.bleGattInfo.toString()}")
        //
        */

        //binding.userName.text = userName
        binding.seatTxt.text = intent.getStringExtra("bluetooth_name")
        val deviceAddress = intent.getStringExtra("bluetooth_address")
        Log.i(TAG, "deviceAddress: $deviceAddress")

        //spinner에서 사용할 아이템 목록
        //해당 정보는 AWS에 반영이 되어야 함(사용목적의 변경을 위해서 필요)
        var purpose_data = listOf("Study", "Discussion")
        var adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, purpose_data)
        binding.purposeSpinner.adapter = adapter

        binding.purposeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.purposeTxt.text = purpose_data[position]
                val updateRequest = RetrofitClient.getClient("https://gp34e91r3a.execute-api.ap-northeast-2.amazonaws.com")?.create(IRetrofit::class.java)
                val result = updateRequest?.updateInfo(deviceAddress.toString(), userEmail, "unknown", purpose_data[position], true, dataFormat.format(System.currentTimeMillis()))
                    ?.enqueue(object: Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        Log.i(TAG, "응답 성공: ${response.raw()}")
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.i(TAG, "응답 실패, Errored by: $t")
                    }
                })
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }
        }
        //여기까지가 spinner관련 코드

        //문의하기 버튼 listener
        binding.requestBtn.setOnClickListener {
            startActivity(Intent(this@MainOption, Send_email::class.java))
        }

        /*
        //자리비움 버튼 클릭시에 Alert Dialog 띄워줌
        binding.emptyBtn.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("자리 비움")
                .setMessage("자리를 비울 수 있는 시간은 10분입니다.")
                .setPositiveButton("확인", DialogInterface.OnClickListener { dialogInterface, id ->
                    //이부분에 서버에 10분 딜레이주는 코드 추가해야함
                    //딜레이가 아닌 연결 끊고 서버에는 사용중임을 보내주면 됨, 그리고 특정 시간 후에 연결 시도해서 연결 안되면 서버에 연결 해제되는 걸로, 중간에 연결 되면 그냥 냅두면 될듯?

                })
                .setNegativeButton("취소", DialogInterface.OnClickListener { dialogInterface, id ->

                })
            builder.show()
        }
        //
         */

        //액션바 상에 뒤로가기 버튼 관련된 코드, 밑에 있는 onOptionsItemSelected함수를 콜백함
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    //parameter = The menu item that was selected. This value cannot be null.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }
}