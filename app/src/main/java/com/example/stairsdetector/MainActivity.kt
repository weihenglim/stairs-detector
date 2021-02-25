package com.example.stairsdetector

import android.content.Context
import android.hardware.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.stairsdetector.databinding.ActivityMainBinding
import java.util.Queue
import java.util.LinkedList

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var mAccel: Sensor? = null
    private var mSigMotion: Sensor? = null
    private var mGravity: Sensor? = null
    private var noise = FloatArray(3)
    private var accelFiltered = FloatArray(3)
    private var gravity = FloatArray(3)
    private val alpha = 0.1f
    private var sigMotionCount = 0
    private val vertThreshold: Double = 4.0
    private val vertMax: Double = 6.0
    private var vertAccelQueue: Queue<Double> = LinkedList<Double>()
    private val queueSize = 20
    private val errorMargin = 8
    private var motionThreshold: Double? = null
    private var restThreshold: Double = 1.0
    private var inVertMotion: Boolean = false
    private var prevSigCount = 0
    private var stairCount = 0
    private val stairDetectDelay: Long = 1000
    private var startTime: Int = 0
    private val minMotionDuration = 3000
    private var calibrate = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mSigMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        mGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val triggerEventListener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                sigMotionCount += 1
                binding.sigValue.text = sigMotionCount.toString()
                mSigMotion?.also { sensor ->
                    sensorManager.requestTriggerSensor(this, sensor)
                }
            }
        }
        mSigMotion?.also { sensor ->
            sensorManager.requestTriggerSensor(triggerEventListener, sensor)
        }

        motionThreshold = errorMargin * vertThreshold / queueSize
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            for (i in noise.indices) {
                noise[i] = event.values[i] * alpha + noise[i] * (1.0f - alpha)
                accelFiltered[i] = event.values[i] - noise[i]
            }
        }
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {

            for (i in noise.indices) {
                gravity[i] = event.values[i]
            }
        }

        binding.xValue.text = accelFiltered[0].toString()
        binding.yValue.text = accelFiltered[1].toString()
        binding.zValue.text = accelFiltered[2].toString()

        val vertAccel = (accelFiltered[0] * gravity[0] / 9.8) +
                (accelFiltered[1] * gravity[1] / 9.8) +
                (accelFiltered[2] *gravity[2] /9.8)

        binding.vertValue.text = vertAccel.toString().take(10)

        if (vertAccelQueue.size >= queueSize) {
            vertAccelQueue.remove()
        }

        vertAccelQueue.add(kotlin.math.min(kotlin.math.abs(vertAccel), vertThreshold))

        if (calibrate && vertAccelQueue.average() <= restThreshold) {
            calibrate = false
        }

        if (kotlin.math.abs(vertAccel) >= vertMax) {
            inVertMotion = false
            calibrate = true
        } else if (!calibrate){
            if (vertAccelQueue.average() >= motionThreshold!! && !inVertMotion) {
                inVertMotion = true
                prevSigCount = sigMotionCount
                startTime = System.currentTimeMillis().toInt()
            } else if (vertAccelQueue.average() <= restThreshold && inVertMotion) {
                inVertMotion = false
                if (System.currentTimeMillis().toInt() - startTime >= minMotionDuration) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (sigMotionCount > prevSigCount) {
                            stairCount += 1
                            binding.stairValue.text = stairCount.toString()
                        }
                    }, stairDetectDelay)
                }
            }
        }

        binding.vertAvgValue.text = vertAccelQueue.average().toString().take(10)
        binding.inVertMotionValue.text = inVertMotion.toString()
    }

    override fun onResume() {
        super.onResume()
        mAccel?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
        mGravity?.also { grav ->
            sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}