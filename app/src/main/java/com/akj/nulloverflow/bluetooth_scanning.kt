package com.akj.nulloverflow

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BlendModeColorFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akj.nulloverflow.databinding.ActivityBluetoothScanningBinding
import com.akj.nulloverflow.databinding.BluetoothListBinding

/*
//해당 어플리케이션의 context를 사용하기위해 선언
class MyApplication : Application() {
    init {
        instance = this
    }

    companion object {
        private lateinit var instance: MyApplication
        fun ApplicationContext(): Context {
            return instance.applicationContext
        }
    }
}
*/

class bluetooth_scanning : AppCompatActivity() {

    val binding by lazy { ActivityBluetoothScanningBinding.inflate(layoutInflater) }
    //startActiviyForResult가 deprecated되었기 때문에 필요한 변수
    private lateinit var resultActivity: ActivityResultLauncher<Intent>

    //블루투스 아답터 가져옴
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        //선언한 bluetootAdapter에 가져온 아답터 할당.
        bluetoothManager.adapter
    }

    //리사이클러뷰 아답터
    private lateinit var reAdapter: BleCustomAdapter
    companion object {
        //onRequestPermissionResult의 인자로 넘어갈 상수들
        val LOCATION_PERMISSION = 100
        val BLUETOOTH_SCAN_PERMISSION = 101
    }
    //Scan 하는 시간
    private val SCAN_PERIOD: Long = 10000
    //Scan한 블루투스 기기를 저장할 array list
    private var deviceList = ArrayList<BluetoothDevice>()
    //scan상태를 알려주는 변수 -> 별로 필요 없을듯?
    //private var scan_state:Boolean = false
    //to schedule messages and runnables to be executed at some point in the future, 특정시간 이후에 스캔을 멈추는 동작을 하기위해 설정
    private val handler = Handler(Looper.getMainLooper())
    //GATT서버에 연결하기위해 필요
    private var bleGatt: BluetoothGatt ?= null
    //블루투스 기기를 scan할 때 불러주는 startScan 및 stopScan 메서드에서 인자로 넘겨주어야할 클래스(콜백)
    private val bleScanCallBack = object : ScanCallback() {
        //BLE의 advertisement가 발견되었을 때 호출됨(필터 없이 호출되는 경우, 필터가 있어도 호출되는 경우가 존재)
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.let {
                if(!deviceList.contains(result.device)){
                    deviceList.add(result.device)
                    //리사이클러 뷰에 변화된거 띄워주는 코드 작성
                    reAdapter.notifyDataSetChanged()
                }
            }
        }
        //Batch scan result가 전달될 때 call back됨(lowpower옵션을 주거나 필터를 적용했을 때 호출됨, 한번에 하나의 값에만 반응을 하는 것이 아닌 전체를 묶어서 뿌려줌)
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)

            //결과로 받은 result가 null이 아닐때
            //results의 타입 = ScanResult for Bluetooth LE scan. BluetoothDevice타입과는 다르므로 results에서 해당하는 타입을 추출해야함
            results?.let {
                for(result in it) {
                    //기존 deviceList에 없을 경우 추가
                    if(!deviceList.contains(result.device))
                        deviceList.add(result.device)
                    //리사이클러 뷰에 변화된거 띄워주는 코드 작성
                    reAdapter.notifyDataSetChanged()
                }
            }
        }

        //scan 실패했을 때 호출되는 call back methods
        //스캔을 중지했다가 다시 스캔하면 오류가 남, errorCode는 2
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(this@bluetooth_scanning, "검색에 실패했습니다. Error : ${errorCode}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //위치권한 검사, 권한 없으면 권한 요청
        if(!checkPermission(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION)
        }

        //처음에는 글씨 안보이게
        binding.progressTxt.visibility = View.INVISIBLE

        //리사이클러뷰 아답터 생성
        reAdapter = BleCustomAdapter()
        reAdapter.setBleList(deviceList)

        //리사이클러뷰에 선언한 아답터 연결, 레이아웃 매니저로는 LinearLayout 사용
        binding.bleRecycler.adapter = reAdapter
        binding.bleRecycler.layoutManager = LinearLayoutManager(this)

        //아탑터에 interface로 선언한 ItemClickListener 등록시켜주고 onClick override
        reAdapter.setItemClickListener(object: ItemClickListener{
            override fun onClick(view: View, position: Int) {
                //연결하고자 하는 기기를 클릭하면 즉시 scan을 중지한 후
                //deviceScan이 아닌 스캔 버튼이 눌린 것 같은 효과를 줘야될 듯
                binding.scanBtn.isChecked = false
                deviceScan(false)

                //scan 멈춘 후 일정 시간 이후에 연결시도도
                //해당하는 위치의 기기를 가져옴
                val device = deviceList[position]

                //100밀리초 이후에 연결시도
                Thread.sleep(600)

                //BluetoothService로 값이 넘어갈 때 bleGatt가 null임임
                bleGatt = BluetoothService(this@bluetooth_scanning, bleGatt).gatt(device)
            }
        })

        //scanBtn에 setOnCheckedChangeListener달아서 scan할 수 있도록
        binding.scanBtn.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                //스캔중일때는 위에있는 스캔중... text가 보이도록
                binding.progressTxt.visibility = View.VISIBLE
                //스캔 시작을 하는 부분 블루투스 사용 가능한지 불가능한지 띄워줘야함
                //블루투스가 사용 불가능한 상태라면 사용 가능하게 바꿔줘야 함
                if(bluetoothAdapter?.isEnabled == false) {
                    resultActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if(it.resultCode == RESULT_OK) {
                            //요청 성공
                        }
                        else {
                            //요청 실패
                        }
                    }
                    //블루투스 설정하는 System Activity를 보여줌
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    resultActivity.launch(enableBtIntent)
                }
                //블루투스 사용 확인 부분 끝

                //스캔 시작
                deviceScan(true)

            } else {
                //스캔중이 아닐때는 위에 스캔중...이라는 text가 안보이도록
                binding.progressTxt.visibility = View.INVISIBLE
                //스캔을 멈추는 부분
                deviceScan(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        //블루투스를 지원하는지 확인해야함, 확인 후 지원하지 않는다면 앱이 종료되도록 설정 -> 이후 블루투스 지원여부 분기문을 작성 안해도 됨
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE기능을 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    //permission확인해주는 함수
    fun checkPermission(permission_list: Array<String>): Boolean {
        //bluetoothAdapter.bluetoothLeScanner.startScan() 매서드가 Marshmallow이상 버전에서만 사용이 가능함
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for(permission in permission_list) {
                //해당 권한이 없을 경우 false반환
                if(ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    //사용자 요청을 처리할때 콜백되는 함수 Override
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION -> {
                //요청정보 배열에서 권한이 없다면 종료 -> 위치권한 없는건 종료까진 필요없을라나
                if(grantResults.isNotEmpty()) {
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
                        Toast.makeText(this, "해당 권한을 승인해야합니다.", Toast.LENGTH_SHORT).show()
                        //finish()
                    }
                }
            }
            BLUETOOTH_SCAN_PERMISSION -> {
                if(grantResults.isNotEmpty()) {
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "해당 권한을 승인해야합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    //Permission 및 Context이용 때문에 Inner Class로 사용 -> inner class안에는 interface의 구현이 불가능 하지만 다른 방법을 찾았음
    inner class BleCustomAdapter: RecyclerView.Adapter<BleHolder>() {
        //어댑터 내부에서 사용할 디바이스 정보가 담긴 ArrayList
        private var bleList = ArrayList<BluetoothDevice>()
        //ItemClickListener를 위한 변수 지연초기화 사용
        private lateinit var itemClickListener: ItemClickListener

        //클릭 리스너를 등록해주는 메소드
        fun setItemClickListener(itemClickListener: ItemClickListener) {
            this.itemClickListener = itemClickListener
        }

        /*
        인터페이스는 inner class안에 구현이 불가능함 - inner class 안에는 다른 inner class만 구현이 가능
        interface OnItemClickListener {
            fun OnClick(v: View, posiotion: Int)
        }
         */

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleHolder {
            val binding = BluetoothListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

            return BleHolder(binding)
        }

        override fun onBindViewHolder(holder: BleHolder, position: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !checkPermission(arrayOf(Manifest.permission.BLUETOOTH_SCAN))) {
                ActivityCompat.requestPermissions(this@bluetooth_scanning, arrayOf(Manifest.permission.BLUETOOTH_SCAN), BLUETOOTH_SCAN_PERMISSION)
            }
            if(bleList.isNotEmpty()){
                holder.binding.bleNameTxt.text = bleList[position].name
                holder.binding.bleAddTxt.text = bleList[position].address
            }
            //특정 위치에 있는 device정보를 저장하기위해 사용
            //var device: BluetoothDevice = bleList[position]

            //clickListener는 문제없이 등록이 되었으나 연결이 바로 해제됨
            holder.itemView.setOnClickListener {
                itemClickListener.onClick(it, position)
            }

            /*
            holder.apply {
                bleClickConn(device)
            }
             */
        }

        override fun getItemCount(): Int {
            return bleList.size
        }

        //내부에서 사용하는 BleList변수가 private이기 때문에 값을 설정해주기위한 함수
        fun setBleList(deviceList: ArrayList<BluetoothDevice>) {
            this.bleList = deviceList
        }
    }

    inner class BleHolder(val binding: BluetoothListBinding): RecyclerView.ViewHolder(binding.root) {
        /*
        fun bleClickConn(device: BluetoothDevice) {
            //위 onBindViewHolder에서 만든 onclickListener가 전달되면 실행함
            binding.root.setOnClickListener {

                //연결하고자 하는 기기를 클릭하면 즉시 scan을 중지한 후
                deviceScan(false)
                //처음 bleGatt의 값은 null
                bleGatt = BluetoothService(this@bluetooth_scanning, bleGatt).gatt(device)
            }
        }
         */
    }

    interface ItemClickListener {
        fun onClick(view: View, position: Int)
    }

    //API레벨이 21이상인 LOLLIPOP버전 이상만 사용 가능 -> startLeScan이 depercated 됨
    //state의 상태에 따라서 핸들러를 이용, BLE기기를 scan하도록
    //compilesdk가 32 즉, marshmallow보다 높은 버전임, 이렇게되면 권한 사용할때마다 check해줘야됨, 모든 권한을 check할 수 있는 check함수 만들어야될듯 -> targetSdk와 관련있는 거라서 이게 아닌듯
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun deviceScan(state: Boolean) {
        //BLUETOOTH_SCAN에 관한 permission 없을 때 요청해줘여야됨, 있으면 바로 및 if(state)문으로 진입
        //해당부분 역시 MinSdk가 21인데 반해 targetSdk가 30이상이라 추가된 코드 따라서 분기분이 필요할 수도?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !checkPermission(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))) {
            //permission요청
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_SCAN_PERMISSION)
        }
        if(state) {
            handler.postDelayed({
                //scan_state = false
                binding.scanBtn.isChecked = false
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallBack)
            }, SCAN_PERIOD)
            //scan_state = true
            deviceList.clear()
            bluetoothAdapter?.bluetoothLeScanner?.startScan(bleScanCallBack)
        } else {
            //scan_state = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallBack)
        }
    }
}