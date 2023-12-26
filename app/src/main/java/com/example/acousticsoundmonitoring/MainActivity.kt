package com.example.acousticsoundmonitoring

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContextWrapper
import android.content.pm.PackageManager
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
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Date


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false
    private var RECORD_AUDIO = 0

    private lateinit var handler: Handler
    private val updateDelay = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStartRecording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(this, permissions,RECORD_AUDIO)
                Toast.makeText(this, "ASKING", Toast.LENGTH_SHORT).show()
            } else {
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
        val recordingFile = File("$audioDirPath/$curTimeStr.m4a")

        try {
            mediaRecorder = MediaRecorder(applicationContext)

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
}