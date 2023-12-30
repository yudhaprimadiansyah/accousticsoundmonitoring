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
                audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
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
            if (mediaRecorder != null) {
                try {
                    val currentAmplitude = mediaRecorder!!.maxAmplitude
                    updateUI(currentAmplitude)
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
//        audioRecord = audioClassifier?.createAudioRecord()
        audioRecord?.startRecording()
        tensorAudio = audioClassifier?.createInputTensorAudio()

        GlobalScope.launch {
            val audioBuffer = ShortArray(bufferSize / 2) // 16-bit audio, 2 bytes per sample

            while (true) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size)
                if (bytesRead != null) {
                    if (bytesRead > 0) {
            //                    processAudioData(audioBuffer)
                        Log.d("AAAA", audioBuffer.toString())
                    }
                }
            }
        }

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

        timerTask {
            run(){
                val output: List<Classifications>? = audioClassifier?.classify(tensorAudio)
                val finalOutput: MutableList<Category> = ArrayList()
                if(output != null){
                    for (classifications in output) {
                        for (category in classifications.categories) {
                            if (category.score > 0.3f) {
                                finalOutput.add(category)
                            }
                        }
                    }
                }
            }
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
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
            Toast.makeText(this, "Recording Finished!", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(currentAmplitude: Int) {
        Log.d("AAAA", currentAmplitude.toString())
        binding.speedView.speedTo(currentAmplitude.toFloat())
        binding.speedView.unit = "dB"
    }

    //    private fun processAudioData(audioBuffer: ShortArray) {
//        // Di sini Anda dapat memproses data audio sesuai kebutuhan Anda
//        // Contoh: Kirim data audio ke TensorFlow Interpreter
//
//        // Bentuk input tensor sesuai dengan model
//        val inputTensor = interpreter.getInputTensor(0)
//        inputTensor.rewind()
//
//        for (i in audioBuffer.indices) {
//            // Normalisasi data audio dan kirim ke model
//            val normalizedValue = audioBuffer[i].toFloat() / Short.MAX_VALUE
//            inputTensor.putFloat(normalizedValue)
//        }
//
//        // Eksekusi interpreter
//        interpreter.run()
//
//        // Dapatkan output dari model jika diperlukan
//        val outputTensor = interpreter.getOutputTensor(0)
//        val outputValues = FloatArray(outputTensor.shape()[1])
//        outputTensor.getFloatArray(outputValues)
//    }
}