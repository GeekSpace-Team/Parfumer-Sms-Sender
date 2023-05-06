package com.gonodono.smssender

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gonodono.smssender.sms.SmsPermissions
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val defaultHost = "http://216.250.9.29:5003"
    private val eventName = "verification-phone"
    private var mSocket: Socket? = null
    // This will just end up blank if the permissions aren't granted.
    override fun onCreate(savedInstanceState: Bundle?) {
        val request =
            registerForActivityResult(RequestMultiplePermissions()) { grants ->
                if (grants.all { it.value }) setUpUi()
            }

        super.onCreate(savedInstanceState)

        val permissions = SmsPermissions
        if (permissions.any { checkSelfPermission(it) != PERMISSION_GRANTED }) {
            request.launch(permissions)
        } else {
            createSocket()
            connectWebSocket()
            setUpUi()
        }
    }

    private fun createSocket() {
        try {
            val options = IO.Options.builder() // IO factory options
                .setForceNew(false)
                .setMultiplex(true) // low-level engine options
                .setTransports(arrayOf(WebSocket.NAME))
                .setUpgrade(true)
                .setRememberUpgrade(false)
                .setQuery(null)
                .setExtraHeaders(null) // Manager options
                .setReconnection(true)
                .setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(1000)
                .setReconnectionDelayMax(5000)
                .setRandomizationFactor(0.5)
                .setTimeout(20000) // Socket options
                .setAuth(null)
                .build()
            //            opts.forceNew = true;
            mSocket = IO.socket(URI.create(defaultHost), options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disconnect() {
        mSocket!!.disconnect()
        mSocket!!.off(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket!!.off(Socket.EVENT_CONNECT, onConnect)
        mSocket!!.off(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket!!.off(eventName, onNewMessage)
    }

    private fun connectWebSocket() {
        mSocket!!.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket!!.on(Socket.EVENT_CONNECT, onConnect)
        mSocket!!.on(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket!!.on(eventName, onNewMessage)
        mSocket!!.connect()
    }

    var onConnect = Emitter.Listener {
        Log.d("TAG", "Socket Connected!")
        setStatus("Connected")
    }

    private val onConnectError =
        Emitter.Listener { args ->
            setStatus("Connection error!")
        }
    private val onDisconnect = Emitter.Listener {
        setStatus("Disconnected")
    }

    private fun setStatus(status: String){
        Log.e("Status",status)
    }

    private val onNewMessage =
        Emitter.Listener { args ->
            try {
                val viewModel: MainViewModel by viewModels()
                val obj = args[0] as JSONObject
                val gson = Gson()
                //                String res= (String) args[0];
                //                String[] s=res.split(",");
                //                sendSms(obj.getString("number"),obj.getString("text"));
                viewModel.sendSms(obj.getString("number"), obj.getString("sms"))
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

    private fun setUpUi() {
        val viewModel: MainViewModel by viewModels()
        var uiState: UiState by mutableStateOf(UiState.Loading)

        lifecycleScope.launch {
            viewModel.uiState
                .flowWithLifecycle(lifecycle)
                .onEach { uiState = it }
                .collect()
        }

        setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                when (val state = uiState) {
                    is UiState.Active -> {
                        Text(state.messages, Modifier.weight(1F))

                        val info = when {
                            state.isSending -> "Sending…"
                            else -> state.lastError
                        }

                        if (info != null) {
                            Text(info, Modifier.padding(10.dp), Color.Red)
                        }

                        TextButton({ viewModel.queueDemoMessagesAndSend() }) {
                            Text("Queue messages & send")
                        }

                        TextButton({ viewModel.resetFailedAndRetry() }) {
                            Text("Reset failed & retry")
                        }
                    }
                    else -> Text("Loading…")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}