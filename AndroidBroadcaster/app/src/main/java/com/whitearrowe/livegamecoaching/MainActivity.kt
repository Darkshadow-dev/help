package com.whitearrowe.livegamecoaching

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private val captureRequest = 4101
    private lateinit var status: TextView
    private lateinit var room: TextView
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,40,28,24); gravity=Gravity.CENTER_HORIZONTAL; setBackgroundColor(0xFF101827.toInt()) }
        fun label(text:String, size:Float=16f) = TextView(this).apply { this.text=text; textSize=size; setTextColor(0xFFFFFFFF.toInt()); gravity=Gravity.CENTER; setPadding(8,14,8,14) }
        layout.addView(label("LIVE GAME COACHING",24f)); layout.addView(label("Broadcaster · Prototype 0.1"))
        status=label("Ready to connect"); layout.addView(status)
        room=label("Room code will appear here",22f); layout.addView(room)
        val start=Button(this).apply { text="Start broadcast"; setOnClickListener { startCaptureFlow() } }; layout.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val overlay=Button(this).apply { text="Enable floating chat"; setOnClickListener { enableOverlay() } }; layout.addView(overlay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val stop=Button(this).apply { text="Stop broadcast"; setOnClickListener { stopService(Intent(this@MainActivity,BroadcastService::class.java)); status.text="Broadcast stopped"; room.text="" } }; layout.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(label("Target: 1280 × 720 · 60 FPS\nKeep this app running for the first test. Share the room code with your coach."))
        setContentView(layout)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),55)
        BroadcastService.onRoomCode = { code -> runOnUiThread { room.text="ROOM  $code"; status.text="Broadcast starting · Share this code" } }
        BroadcastService.onStatus = { value -> runOnUiThread { status.text=value } }
    }

    private fun startCaptureFlow() {
        if (BuildConfigValues.SIGNALING_URL.contains("YOUR-RENDER-SERVICE")) { status.text="Set SIGNALING_URL in BuildConfigValues.kt first"; return }
        startActivityForResult(projectionManager.createScreenCaptureIntent(),captureRequest)
    }
    @Deprecated("MediaProjection permission result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==captureRequest && resultCode==Activity.RESULT_OK && data!=null) {
            val intent=Intent(this,BroadcastService::class.java).apply { putExtra(BroadcastService.EXTRA_RESULT_CODE,resultCode); putExtra(BroadcastService.EXTRA_RESULT_DATA,data) }
            ContextCompat.startForegroundService(this,intent); status.text="Starting capture and connecting…"
        } else if (requestCode==captureRequest) status.text="Screen capture permission was not granted"
    }
    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        startService(Intent(this,FloatingChatService::class.java))
    }
}
