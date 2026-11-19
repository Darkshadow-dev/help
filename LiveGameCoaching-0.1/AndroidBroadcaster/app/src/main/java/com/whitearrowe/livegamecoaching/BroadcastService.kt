package com.whitearrowe.livegamecoaching

import android.app.*
import android.content.*
import android.media.projection.MediaProjection
import android.os.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

class BroadcastService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE="resultCode"; const val EXTRA_RESULT_DATA="resultData"
        const val ACTION_CHAT="com.whitearrowe.livegamecoaching.CHAT"; const val ACTION_INCOMING_CHAT="com.whitearrowe.livegamecoaching.INCOMING_CHAT"
        var onRoomCode:((String)->Unit)?=null; var onStatus:((String)->Unit)?=null
    }
    private val client=OkHttpClient(); private var socket:WebSocket?=null; private var roomCode:String?=null
    private lateinit var factory:PeerConnectionFactory; private lateinit var eglBase:EglBase; private lateinit var capturer:ScreenCapturerAndroid
    private lateinit var videoSource:VideoSource; private lateinit var videoTrack:VideoTrack; private lateinit var projection:MediaProjection
    private val peers=ConcurrentHashMap<String,PeerConnection>(); private val pendingIce=ConcurrentHashMap<String,MutableList<IceCandidate>>()
    private val chatReceiver=object:BroadcastReceiver(){ override fun onReceive(c:Context?,i:Intent?){ val text=i?.getStringExtra("text")?.trim().orEmpty(); if(text.isNotEmpty()) send(JSONObject().put("type","chat").put("text",text.take(300))) } }

    override fun onCreate(){ super.onCreate(); createChannel(); registerReceiver(chatReceiver,IntentFilter(ACTION_CHAT), Context.RECEIVER_NOT_EXPORTED) }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        startForeground(7,NotificationCompat.Builder(this,"broadcast").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Live Game Coaching").setContentText("Screen is being shared").setOngoing(true).build())
        val code=intent?.getIntExtra(EXTRA_RESULT_CODE,0)?:0; val data=intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if(data!=null) begin(data,code)
        return START_NOT_STICKY
    }
    private fun begin(permissionData:Intent,resultCode:Int){
        try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions())
            eglBase=EglBase.create()
            factory=PeerConnectionFactory.builder().setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext,true,true)).setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext)).createPeerConnectionFactory()
            val helper=SurfaceTextureHelper.create("ScreenCaptureThread",eglBase.eglBaseContext)
            videoSource=factory.createVideoSource(true); capturer=ScreenCapturerAndroid(permissionData,object:MediaProjection.Callback(){ override fun onStop(){ stopSelf() } })
            capturer.initialize(helper,applicationContext,videoSource.capturerObserver); capturer.startCapture(BuildConfigValues.WIDTH,BuildConfigValues.HEIGHT,BuildConfigValues.FPS)
            videoTrack=factory.createVideoTrack("LGC_SCREEN",videoSource); connectSocket()
        } catch(e:Exception){ onStatus?.invoke("Capture error: ${e.message}"); stopSelf() }
    }
    private fun connectSocket(){
        val req=Request.Builder().url(BuildConfigValues.SIGNALING_URL).build()
        socket=client.newWebSocket(req,object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response){ ws.send(JSONObject().put("type","create-room").toString()) }
            override fun onMessage(ws:WebSocket,text:String){ try { val m=JSONObject(text); when(m.optString("type")){
                "room-created"->{ roomCode=m.getString("code"); onRoomCode?.invoke(roomCode!!); onStatus?.invoke("Room created · Waiting for coach") }
                "coach-joined"->makeOffer(m.getString("peerId"))
                "signal"->handleSignal(m.optString("from"),m.getJSONObject("data"))
                "chat"->{ val i=Intent(ACTION_INCOMING_CHAT).putExtra("from",m.optString("from")).putExtra("text",m.optString("text")); sendBroadcast(i) }
                "coach-left"->closePeer(m.optString("peerId"))
                "error"->onStatus?.invoke("Server: ${m.optString("message")}")
                "room-ended"->onStatus?.invoke("Room ended")
            } }catch(e:Exception){ onStatus?.invoke("Signaling error: ${e.message}") } }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){ onStatus?.invoke("WebSocket failed: ${t.message}") }
        })
    }
    private fun newPeer(id:String):PeerConnection? {
        val ice=PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        val pc=factory.createPeerConnection(PeerConnection.RTCConfiguration(listOf(ice)),object:PeerConnection.Observer{
            override fun onIceCandidate(c:IceCandidate){ send(JSONObject().put("type","signal").put("to",id).put("data",JSONObject().put("kind","candidate").put("candidate",JSONObject().put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).put("candidate",c.sdp)))) }
            override fun onConnectionChange(s:PeerConnection.PeerConnectionState){ onStatus?.invoke("Coach $id · ${s.name}") }
            override fun onSignalingChange(s:PeerConnection.SignalingState){}; override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){}; override fun onIceConnectionReceivingChange(b:Boolean){}; override fun onIceGatheringChange(s:PeerConnection.IceGatheringState){}; override fun onIceCandidatesRemoved(c:Array<out IceCandidate>){}; override fun onAddStream(s:MediaStream){}; override fun onRemoveStream(s:MediaStream){}; override fun onDataChannel(d:DataChannel){}; override fun onRenegotiationNeeded(){}; override fun onTrack(t:RtpTransceiver){}
        }) ?: return null
        pc.addTrack(videoTrack,listOf("LGC_STREAM")); peers[id]=pc; return pc
    }
    private fun makeOffer(id:String){ val pc=newPeer(id)?:return; pc.createOffer(object:SdpObserver{ override fun onCreateSuccess(d:SessionDescription){ pc.setLocalDescription(object:SdpObserver{ override fun onSetSuccess(){ send(JSONObject().put("type","signal").put("to",id).put("data",JSONObject().put("kind","offer").put("description",JSONObject().put("type",d.type.canonicalForm()).put("sdp",d.description)))) }; override fun onSetFailure(e:String){}; override fun onCreateSuccess(d:SessionDescription){}; override fun onCreateFailure(e:String){} },d) }; override fun onCreateFailure(e:String){ onStatus?.invoke("Offer failed: $e") }; override fun onSetSuccess(){}; override fun onSetFailure(e:String){} },MediaConstraints()) }
    private fun handleSignal(id:String,d:JSONObject){ val pc=peers[id]?:return; when(d.optString("kind")){ "answer"->{ val s=d.getJSONObject("description"); pc.setRemoteDescription(NoopSdp(),SessionDescription(SessionDescription.Type.ANSWER,s.getString("sdp"))); pendingIce.remove(id)?.forEach{pc.addIceCandidate(it)} }; "candidate"->{ val c=d.getJSONObject("candidate"); val ice=IceCandidate(c.optString("sdpMid"),c.optInt("sdpMLineIndex"),c.getString("candidate")); if(pc.remoteDescription!=null) pc.addIceCandidate(ice) else pendingIce.getOrPut(id){ mutableListOf() }.add(ice) } } }
    private class NoopSdp:SdpObserver{ override fun onCreateSuccess(d:SessionDescription){}; override fun onSetSuccess(){}; override fun onCreateFailure(e:String){}; override fun onSetFailure(e:String){} }
    private fun closePeer(id:String){ peers.remove(id)?.close(); pendingIce.remove(id) }
    private fun send(o:JSONObject){ socket?.send(o.toString()) }
    private fun createChannel(){ val n=getSystemService(NOTIFICATION_SERVICE) as NotificationManager; n.createNotificationChannel(NotificationChannel("broadcast","Screen broadcast",NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy(){ try{ send(JSONObject().put("type","close-room")); capturer.stopCapture(); capturer.dispose(); peers.values.forEach{it.close()}; peers.clear(); factory.dispose(); eglBase.release(); socket?.close(1000,"stopped"); unregisterReceiver(chatReceiver) }catch(_:Exception){}; super.onDestroy() }
    override fun onBind(intent:Intent?)=null
}
