package com.wanghoufan.zaijiuyidian.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.wanghoufan.zaijiuyidian.R

/**
 * 离线语音提示：全部使用本地录制/生成的 WAV，经 SoundPool 低延迟播放。
 * 启动时预加载；若尚未加载完成，调用方回退到短提示音。
 */
object VoiceCues {

    private var pool: SoundPool? = null
    private val resToSample = HashMap<Int, Int>()
    private val readySamples = HashSet<Int>()
    private var warmed = false

    private val intervalRes: Map<Int, Int> = mapOf(
        3 to R.raw.cue_interval_3,
        6 to R.raw.cue_interval_6,
        9 to R.raw.cue_interval_9,
        12 to R.raw.cue_interval_12,
        15 to R.raw.cue_interval_15,
        18 to R.raw.cue_interval_18,
        21 to R.raw.cue_interval_21,
        24 to R.raw.cue_interval_24,
        27 to R.raw.cue_interval_27,
        30 to R.raw.cue_interval_30,
        33 to R.raw.cue_interval_33,
        36 to R.raw.cue_interval_36,
        39 to R.raw.cue_interval_39,
        42 to R.raw.cue_interval_42,
        45 to R.raw.cue_interval_45,
        48 to R.raw.cue_interval_48,
        51 to R.raw.cue_interval_51,
        54 to R.raw.cue_interval_54,
        57 to R.raw.cue_interval_57,
        60 to R.raw.cue_interval_60,
        63 to R.raw.cue_interval_63,
        66 to R.raw.cue_interval_66,
        69 to R.raw.cue_interval_69,
        72 to R.raw.cue_interval_72,
        75 to R.raw.cue_interval_75,
        78 to R.raw.cue_interval_78,
        81 to R.raw.cue_interval_81,
        84 to R.raw.cue_interval_84,
        87 to R.raw.cue_interval_87,
        90 to R.raw.cue_interval_90,
        93 to R.raw.cue_interval_93,
        96 to R.raw.cue_interval_96,
        99 to R.raw.cue_interval_99,
    )

    private val allRes: List<Int> = buildList {
        add(R.raw.cue_start)
        add(R.raw.cue_target)
        add(R.raw.cue_interval_100)
        addAll(intervalRes.values)
    }

    fun warmUp() {
        if (warmed) return
        warmed = true
        pool()
    }

    private fun pool(): SoundPool? {
        pool?.let { return it }
        return runCatching {
            val ctx: Context = com.wanghoufan.zaijiuyidian.ZaiJiuApp.instance
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val sp = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) readySamples.add(sampleId)
            }
            allRes.forEach { res ->
                runCatching {
                    ctx.resources.openRawResourceFd(res)?.use { afd ->
                        resToSample[res] = sp.load(afd, 1)
                    }
                }
            }
            pool = sp
            sp
        }.getOrNull()
    }

    private fun play(res: Int): Boolean {
        val sp = pool() ?: return false
        val sample = resToSample[res] ?: return false
        if (sample !in readySamples) return false
        return runCatching { sp.play(sample, 1f, 1f, 1, 0, 1f) > 0 }.getOrDefault(false)
    }

    fun playStart(): Boolean = play(R.raw.cue_start)

    fun playTarget(): Boolean = play(R.raw.cue_target)

    fun playInterval(minute: Int): Boolean =
        play(intervalRes[minute] ?: R.raw.cue_interval_100)
}
