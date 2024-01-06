package com.example.acousticsoundmonitoring

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.acousticsoundmonitoring.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.Classifications
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Date
import kotlin.concurrent.timerTask
import kotlin.math.log10


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false
    private var RECORD_AUDIO = 0

    private lateinit var handler: Handler
    private val updateDelay = 250L
    private var model = "yamnet.tflite"
    private var probTreshHold: Float = 0.3f
    private var audioPath: String = ""
    var audioClassifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null
    private var audioRecord: AudioRecord? = null

    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        audioClassifier = AudioClassifier.createFromFile(this, model)

        binding.buttonStartRecording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(this, permissions,RECORD_AUDIO)
                Toast.makeText(this, "ASKING", Toast.LENGTH_SHORT).show()
            } else {
                tensorAudio = audioClassifier?.createInputTensorAudio()
                //audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
                startRecording()
            }
        }

        binding.buttonStopRecording.setOnClickListener{
            stopRecording()
        }

        binding.buttonPauseRecording.setOnClickListener {
            pauseRecording()
        }

    }

    private val updateAmplitude = object : Runnable {
        override fun run() {
            tensorAudio?.load(audioRecord)
            val output = audioClassifier?.classify(tensorAudio)
            val filteredModelOutput = output?.get(0)?.categories?.filter {
                it.score > 0.3f
            }
            val outputStr = filteredModelOutput?.sortedBy { -it.score }
                ?.joinToString(separator = "\n") { "${it.label} -> ${it.score} " }

            if(outputStr != null){
                Log.d("HasilKlasifikasi", outputStr)
            }
            if (mediaRecorder != null) {
                try {
                    val currentAmplitude = mediaRecorder!!.maxAmplitude
                    val db = currentAmplitude * (150.0/16000.0)
                    updateUI(db, outputStr.toString())
                    handler.postDelayed(this, updateDelay)

                } catch (e: IllegalStateException) {
                    Log.d("AAAA", e.message.toString())
                }

            }
        }
    }

    private fun startRecording() {
        val audioDir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AudioMemos")
        audioDir.mkdirs()
        val audioDirPath = audioDir.absolutePath
        val currentTime: Date = Calendar.getInstance().time // current time
        val curTimeStr: String = currentTime.toString().replace(" ", "_")
        audioPath = "$audioDirPath/accousticsensor.m4a"
        val recordingFile = File("$audioDirPath/accousticsensor.m4a")
        val format: TensorAudio.TensorAudioFormat? = audioClassifier?.requiredTensorAudioFormat
        audioRecord = audioClassifier?.createAudioRecord()
        audioRecord?.startRecording()
        tensorAudio = audioClassifier?.createInputTensorAudio()

        try {
            mediaRecorder = MediaRecorder()

            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder?.setOutputFile(recordingFile)

            try {
                mediaRecorder!!.prepare()
            } catch (e: IOException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                Log.d("AAAA", e.message.toString())
            }

            mediaRecorder!!.start()
            state = true
            handler = Handler()
            handler.postDelayed(updateAmplitude, updateDelay)

            Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun pauseRecording() {
        if(state) {
            if(!recordingStopped){
                Toast.makeText(this,"Paused!", Toast.LENGTH_SHORT).show()
                mediaRecorder?.pause()
                recordingStopped = true
                binding.buttonPauseRecording.text = "Resume"
            }else{
                resumeRecording()
            }
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun resumeRecording() {
        Toast.makeText(this,"Resume!", Toast.LENGTH_SHORT).show()
        mediaRecorder?.resume()
        binding.buttonPauseRecording.text = "Pause"
        recordingStopped = false
    }

    private fun stopRecording(){
        if(state){
            audioRecord?.stop()
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
            binding.classificationResult.setText("(Kondisi Tidak Merekam)")
            Toast.makeText(this, "Recording Finished!", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(currentAmplitude: Double, classificationResult: String) {
        Log.d("AAAA", currentAmplitude.toString())
        if(classificationResult != ""){
            binding.classificationResult.setText(classificationResult)
        }
        binding.speedView.speedTo(currentAmplitude.toFloat())
        binding.speedView.unit = "db"
    }
}