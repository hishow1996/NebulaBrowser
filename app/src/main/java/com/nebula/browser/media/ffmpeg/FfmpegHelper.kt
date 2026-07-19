package com.nebula.browser.media.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object FfmpegHelper {

    suspend fun mergeVideoAudio(videoPath: String, audioPath: String, outputPath: String): Boolean =
        runFfmpeg("-i \"$videoPath\" -i \"$audioPath\" -c copy \"$outputPath\"")

    suspend fun transcodeDownscale(input: String, output: String, maxWidth: Int, targetBitrate: String): Boolean =
        runFfmpeg("-i \"$input\" -vf scale=$maxWidth:-2 -b:v $targetBitrate -c:a copy \"$output\"")

    private suspend fun runFfmpeg(cmd: String): Boolean = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeAsync(cmd) { s ->
            val code = ReturnCode(sessionReturnCode(s)) // 注意：FFmpegKit4 API
            // 简化：直接看代码返回
            cont.resume(ReturnCode.isSuccess(s.returnCode))
        }
        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    private fun sessionReturnCode(session: com.arthenica.ffmpegkit.FFmpegSession): Int = session.returnCode.value
}
