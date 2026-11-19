package io.github.quezka.roundabout.data

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import io.github.quezka.roundabout.domain.DashRecorder
import io.github.quezka.roundabout.domain.DashcamPolicy
import io.github.quezka.roundabout.domain.Lens
import java.io.File

// camera1 <- tablet hal = legacy ; no preview window -> dummy texture
@Suppress("DEPRECATION")
class CameraRecorder(private val dir: File, private val lens: Lens) : DashRecorder {
    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private val texture = SurfaceTexture(10)

    val active: Boolean get() = recorder != null

    override fun open(): Boolean {
        if (camera != null) return true
        val want = if (lens == Lens.ROAD) Camera.CameraInfo.CAMERA_FACING_BACK else Camera.CameraInfo.CAMERA_FACING_FRONT
        val id = (0 until Camera.getNumberOfCameras()).firstOrNull {
            Camera.CameraInfo().also { info -> Camera.getCameraInfo(it, info) }.facing == want
        } ?: return false
        return try {
            val c = Camera.open(id)
            c.parameters = c.parameters.apply {
                if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in supportedFocusModes) focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                setRecordingHint(true)
            }
            c.setPreviewTexture(texture)
            c.startPreview()
            camera = c
            cameraId = id
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    private var cameraId = 0

    // one segment ; onEnd fires @ max duration
    override fun start(name: String, onEnd: () -> Unit): Boolean {
        val c = camera ?: return false
        val out = File(dir, name)
        return try {
            c.unlock()
            val profile = when {
                CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P) -> CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
                else -> CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH)
            }
            val r = MediaRecorder().apply {
                setCamera(c)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
                setVideoFrameRate(minOf(profile.videoFrameRate, 30))
                setVideoEncodingBitRate(minOf(profile.videoBitRate, DashcamPolicy.BITRATE))
                // landscape mount @ rotation 90 ; back sensor 90 -> 0 ; front sensor 270 -> 180
                setOrientationHint(if (lens == Lens.ROAD) 0 else 180)
                setMaxDuration(DashcamPolicy.SEGMENT_MS)
                setOutputFile(out.path)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onEnd()
                }
                prepare()
                start()
            }
            recorder = r
            true
        } catch (e: Exception) {
            stopSegment()
            false
        }
    }

    override fun stopSegment() {
        recorder?.let {
            try { it.stop() } catch (e: Exception) { }
            it.reset()
            it.release()
        }
        recorder = null
        try { camera?.reconnect() } catch (e: Exception) { }
    }

    override fun close() {
        stopSegment()
        camera?.let {
            try { it.stopPreview() } catch (e: Exception) { }
            it.release()
        }
        camera = null
    }
}
