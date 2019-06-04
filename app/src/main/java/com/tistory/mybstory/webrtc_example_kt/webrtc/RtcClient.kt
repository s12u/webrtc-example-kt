package com.tistory.mybstory.webrtc_example_kt.webrtc

import android.content.Context
import com.tistory.mybstory.webrtc_example_kt.base.PeerConnectionHandler
import com.tistory.mybstory.webrtc_example_kt.base.RemoteVideoHandler
import com.tistory.mybstory.webrtc_example_kt.util.RtcUtil
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer

class RtcClient private constructor(context: Context) : RemoteVideoHandler {

    private val eglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper
    private var videoCapturer: VideoCapturer?

    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var remoteVideoTrack: VideoTrack? = null

    private var localSurfaceViewRenderer: SurfaceViewRenderer? = null
    private var remoteSurfaceViewRenderer: SurfaceViewRenderer? = null

    private val mediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    companion object {
        private var instance: RtcClient? = null

        fun getInstance(context: Context): RtcClient {
            if (instance == null) {
                instance = RtcClient(context)
            }
            return instance!!
        }
    }

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val mediaConstraints = MediaConstraints()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        surfaceTextureHelper = SurfaceTextureHelper.create("CapturingThread", eglBase.eglBaseContext)
        videoCapturer = RtcUtil.createVideoCapturer(context)

        videoCapturer?.let {
            localVideoSource = peerConnectionFactory.createVideoSource(false)
            localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource)
            it.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
            enableVideo(true, it)
        }

        localAudioSource = peerConnectionFactory.createAudioSource(mediaConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource)

    }

    fun initPeerConnection(
        iceServers: List<IceServer>,
        peerConnectionHandler: PeerConnectionHandler
    ) {

        val rtcConfiguration = PeerConnection.RTCConfiguration(iceServers)
        rtcConfiguration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE

        val peerConnectionObserver = RtcPeerConnectionObserver(peerConnectionHandler, this)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfiguration, peerConnectionObserver)

        val localMediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")

        localMediaStream.apply {
            localAudioTrack?.let {
                addTrack(it)
            }
            localVideoTrack?.let {
                addTrack(it)
            }
        }
        peerConnection?.addStream(localMediaStream)
    }

    // TODO : need to run on the other thread

    fun createOffer(sdpObserver: SimpleSdpObserver) = peerConnection?.createOffer(sdpObserver, mediaConstraints)

    fun createAnswer(sdpObserver: SimpleSdpObserver) = peerConnection?.createAnswer(sdpObserver, mediaConstraints)

    fun setLocalDescription(sdpObserver: SimpleSdpObserver, localDescription: SessionDescription) =
        peerConnection?.setLocalDescription(sdpObserver, localDescription)

    fun setRemoteDescription(sdpObserver: SimpleSdpObserver, remoteDescription: SessionDescription) =
        peerConnection?.setRemoteDescription(sdpObserver, remoteDescription)

    fun addIceCandidate(iceCandidate: IceCandidate) = peerConnection?.addIceCandidate(iceCandidate)

    fun removeIceCandidates(iceCandidates: Array<IceCandidate>) {
        peerConnection?.removeIceCandidates(iceCandidates)
    }

    fun attachLocalView(localSurfaceViewRenderer: SurfaceViewRenderer) {
        localSurfaceViewRenderer.init(eglBase.eglBaseContext, null)
        this.localSurfaceViewRenderer = localSurfaceViewRenderer
        localVideoTrack?.addSink(this@RtcClient.localSurfaceViewRenderer)
    }

    fun attachRemoteView(remoteSurfaceViewRenderer: SurfaceViewRenderer) {
        remoteSurfaceViewRenderer.init(eglBase.eglBaseContext, null)
        this.remoteSurfaceViewRenderer = remoteSurfaceViewRenderer
        remoteVideoTrack?.addSink(this@RtcClient.remoteSurfaceViewRenderer)
    }

    //TODO: Remote sink 처리 (현재 안보임 )
    override fun onAddRemoteStream(remoteVideoTrack: VideoTrack) {
        this.remoteVideoTrack = remoteVideoTrack
        remoteSurfaceViewRenderer?.let{
            remoteVideoTrack.addSink(it)
        }
    }

    override fun removeVideoStream() {
        this.remoteVideoTrack = null
    }

    private fun enableVideo(isEnabled: Boolean, videoCapturer: VideoCapturer) {
        if (isEnabled) {
            videoCapturer.startCapture(1280, 720, 30)
        } else {
            videoCapturer.stopCapture()
        }
    }

}