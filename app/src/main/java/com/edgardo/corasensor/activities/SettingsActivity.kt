package com.edgardo.corasensor.activities

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import com.edgardo.corasensor.R
import com.edgardo.corasensor.networkUtility.BluetoothConnectionService
import kotlinx.android.synthetic.main.activity_settings.*
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class SettingsActivity : AppCompatActivity(), AdapterView.OnItemClickListener {

    val _tag = "myTag"
    // List of bluetooth devices
    var btDevices = ArrayList<BluetoothDevice>()
    // Selected devices
    lateinit var selectedBtDevices: BluetoothDevice
    // Communication UUID
    private val uuidConnection = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    // List adapter
    lateinit var devicesBTListAdapter: DevicesBTListAdapter
    // Bluetooth adapter
    var btAdapter: BluetoothAdapter? = null
    // Bluetooth connection
    lateinit var btConnection: BluetoothConnectionService

    companion object {
        const val BLUETOOTH_REQUEST_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btDevices = ArrayList()

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        validateBT()

        //Broadcasts when bond state changes (ie:pairing)
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4, filter)

        button_discover.setOnClickListener { click(it) }
    }

    /**
     * Function to validate if the phone have BT and check if is turn on
     */
    private fun validateBT() {
        if (btAdapter == null) {
            AlertDialog.Builder(this).setMessage(
                    applicationContext.getString(R.string.device_bt_capability)
            ).setCancelable(false)
        }
        // if bluetooth is off
        if (!btAdapter!!.isEnabled) {
            val alert = AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(applicationContext.getString(R.string.msg_enable_bluetooth))
                    .setCancelable(true)
                    .setPositiveButton("Ok") { dialog, which ->
                        // Enable BT
                        val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivity(enableBT)
                        // Notify changes on BT status
                        val btIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                        registerReceiver(changeOnAction, btIntent)
                    }
                    .setNegativeButton("Cancel") { dialog, which ->
                        // Back to Home
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    }

            alert.show()

        }

    }


    private fun click(v: View) {

        when (v.id) {
            R.id.button_discover -> {
                startDiscover(v)
            }
        }

    }


    /**
     * Create a BroadcastReceiver for ACTION_FOUND
     * Verify if BT status has changes
     */
    private val changeOnAction = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                when (state) {
                    BluetoothAdapter.STATE_OFF -> Log.d(_tag, "onReceive: STATE OFF")
                    BluetoothAdapter.STATE_TURNING_OFF -> Log.d(_tag, "changeOnAction: STATE TURNING OFF")
                    BluetoothAdapter.STATE_ON -> Log.d(_tag, "changeOnAction: STATE ON")
                    BluetoothAdapter.STATE_TURNING_ON -> Log.d(_tag, "changeOnAction: STATE TURNING ON")
                }
            }
        }
    }


    /**
     * Start looking fot devices
     */

    private fun startDiscover(v: View) {
        Log.d(_tag, "Discovering: Looking for unpaired devices.")
        Toast
                .makeText(this, applicationContext.getString(R.string.msg_bt_searching),
                        Toast.LENGTH_SHORT)
                .show()

        // Check for permissions on manifest
        checkBTPermissions()

        if (btAdapter!!.isDiscovering) {
            btAdapter!!.cancelDiscovery()
            Log.d(_tag, "Discovering: Canceling discovery.")


            btAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(updateListAdapter, discoverDevicesIntent)
        }
        if (!btAdapter!!.isDiscovering) {


            btAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(updateListAdapter, discoverDevicesIntent)
        }
    }


    /**
     *  Receiver for a list of not paired devices
     * -Executed by startDiscover() method.
     *  Update the Adapter list
     */
    private val updateListAdapter = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(_tag, "onReceive: ACTION FOUND.")

            if (action == BluetoothDevice.ACTION_FOUND) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                btDevices.add(device)
                Log.d(_tag, "onReceive: " + device.name + ": " + device.address)
                devicesBTListAdapter = DevicesBTListAdapter(context, R.layout.row_devices_bt, btDevices)
                list_new_devices.adapter = devicesBTListAdapter
            }
        }
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        //first cancel discovery because its very memory intensive.
        btAdapter!!.cancelDiscovery()

        Log.d(_tag, "onItemClick: You Clicked on a device.")
        val deviceName = btDevices[i].name
//        val deviceAddress = btDevices[i].address

//        Log.d(_tag, "onItemClick: deviceName = $deviceName")
//        Log.d(_tag, "onItemClick: deviceAddress = $deviceAddress")

        //create the bond.

        Log.d(_tag, "Trying to pair with $deviceName")
        Toast
                .makeText(this, applicationContext.getString(R.string.msg_bt_pairing) + deviceName,
                        Toast.LENGTH_SHORT)
                .show()
        btDevices[i].createBond()

        selectedBtDevices = btDevices[i]
        btConnection = BluetoothConnectionService(this)

        if (selectedBtDevices.address != null) {
            try {
                startConnection()
            } catch (e: Exception) {
                Toast
                        .makeText(this, "Error paring", Toast.LENGTH_SHORT)
                        .show()
            }
        }

    }


    /**
     * Request permission to access bluetooth
     */
    private fun checkBTPermissions() {

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                        != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH), BLUETOOTH_REQUEST_PERMISSION)

        }

    }

    /**
     * Start the connection with the device
     * Has to be paired first
     */
    //create method for starting connection
    private fun startConnection() {
        startBTConnection(selectedBtDevices, uuidConnection)
    }

    /**
     * starting listening service method
     */
    private fun startBTConnection(device: BluetoothDevice, uuid: UUID) {
        Log.d(_tag, "startBTConnection: Initializing RFCOM Bluetooth Connection.")

        btConnection.startClient(device, uuid)
    }


    /**
     * Broadcast Receiver for changes made to bluetooth states such as:
     * 1) Discoverability mode on/off or expire.
     */
    private val mBroadcastReceiver2 = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {

                val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)

                when (mode) {
                    //Device is in Discoverable Mode
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> Log.d(
                            _tag,
                            "mBroadcastReceiver2: Discoverability Enabled."
                    )
                    //Device not in discoverable mode
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> Log.d(
                            _tag,
                            "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections."
                    )
                    BluetoothAdapter.SCAN_MODE_NONE -> Log.d(
                            _tag,
                            "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections."
                    )
                    BluetoothAdapter.STATE_CONNECTING -> Log.d(_tag, "mBroadcastReceiver2: Connecting....")
                    BluetoothAdapter.STATE_CONNECTED -> Log.d(_tag, "mBroadcastReceiver2: Connected.")
                }

            }
        }
    }


    /**
     * Broadcast Receiver that detects bond state changes (Pairing status changes)
     */
    private val mBroadcastReceiver4 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                //3 cases:
                //case1: bonded already
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(_tag, "BroadcastReceiver: BOND_BONDED.")
                    //inside BroadcastReceiver4
                    selectedBtDevices = device
                }
                //case2: creating a bone
                if (device.bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(_tag, "BroadcastReceiver: BOND_BONDING.")
                }
                //case3: breaking a bond
                if (device.bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(_tag, "BroadcastReceiver: BOND_NONE.")
                }
            }
        }
    }


    override fun onDestroy() {
        Log.d(_tag, "onDestroy: called.")
        super.onDestroy()
        unregisterReceiver(changeOnAction)
        unregisterReceiver(mBroadcastReceiver2)
        unregisterReceiver(updateListAdapter)
        unregisterReceiver(mBroadcastReceiver4)
        //mBluetoothAdapter.cancelDiscovery();
    }


//
//    fun enableDisableBT() {
//        if (btAdapter == null) {
//            Log.d(_tag, "enableDisableBT: Does not have BT capabilities.")
//        }
//        if (!btAdapter!!.isEnabled) {
//            Log.d(_tag, "enableDisableBT: enabling BT.")
//            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivity(enableBTIntent)
//
//            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//            registerReceiver(changeOnAction, BTIntent)
//        }
//        if (btAdapter!!.isEnabled) {
//            Log.d(_tag, "enableDisableBT: disabling BT.")
//            btAdapter!!.disable()
//
//            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//            registerReceiver(changeOnAction, BTIntent)
//        }
//
//    }


//    fun btnEnableDisable_Discoverable(view: View) {
//        Log.d(_tag, "btnEnableDisable_Discoverable: Making device discoverable for 300 seconds.")
//
//        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
//        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
//        startActivity(discoverableIntent)
//
//        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
//        registerReceiver(mBroadcastReceiver2, intentFilter)
//
//    }


}
