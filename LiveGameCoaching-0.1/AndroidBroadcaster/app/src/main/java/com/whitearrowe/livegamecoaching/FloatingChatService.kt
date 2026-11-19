package com.whitearrowe.livegamecoaching

import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*

class FloatingChatService:Service(){
    private lateinit var wm:WindowManager; private var panel:View?=null
    private val incoming=object:BroadcastReceiver(){ override fun onReceive(c:Context?,i:Intent?){ val line="${i?.getStringExtra("from")?:"Coach"}: ${i?.getStringExtra("text")?:""}"; panel?.findViewById<TextView>(R.id.overlayMessages)?.let{it.text=(it.text.toString()+"\n"+line).takeLast(1200)} } }
    override fun onCreate(){ super.onCreate(); wm=getSystemService(WINDOW_SERVICE) as WindowManager; registerReceiver(incoming,IntentFilter(BroadcastService.ACTION_INCOMING_CHAT),Context.RECEIVER_NOT_EXPORTED); showPanel() }
    private fun showPanel(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; setPadding(12,10,12,10); setBackgroundColor(0xEE182438.toInt())}
        val head=TextView(this).apply{text="LGC  ·  drag to move"; setTextColor(-1); textSize=13f; setPadding(4,4,4,8)}; box.addView(head)
        val messages=TextView(this).apply{id=R.id.overlayMessages; text="Coach chat ready"; setTextColor(-1); textSize=12f; maxLines=5}; box.addView(messages)
        val input=EditText(this).apply{hint="Message coach…"; singleLine=true; imeOptions=EditorInfo.IME_ACTION_SEND}; box.addView(input)
        input.setOnEditorActionListener{v,action,_ -> if(action==EditorInfo.IME_ACTION_SEND || action==0){ val t=input.text.toString().trim(); if(t.isNotEmpty()) sendBroadcast(Intent(BroadcastService.ACTION_CHAT).setPackage(packageName).putExtra("text",t)); input.text.clear(); true }else false}
        val close=Button(this).apply{text="Hide chat"; setOnClickListener{stopSelf()}}; box.addView(close)
        val type=if(android.os.Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p=WindowManager.LayoutParams(280,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END; x=20;y=120}
        head.setOnTouchListener(object:View.OnTouchListener{var sx=0f;var sy=0f;var ox=0;var oy=0;override fun onTouch(v:View,e:MotionEvent):Boolean{when(e.action){MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=p.x;oy=p.y;return true};MotionEvent.ACTION_MOVE->{p.x=ox+(sx-e.rawX).toInt();p.y=oy+(e.rawY-sy).toInt();wm.updateViewLayout(box,p);return true}};return false}})
        wm.addView(box,p); panel=box
    }
    override fun onDestroy(){try{unregisterReceiver(incoming);panel?.let{wm.removeView(it)}}catch(_:Exception){};super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
