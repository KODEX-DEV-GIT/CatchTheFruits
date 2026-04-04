package com.halil.ozel.catchthefruits

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.halil.ozel.catchthefruits.databinding.ActivityMainBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var score = 0
    private val imageArray = mutableListOf<ImageView>()
    private val handler = Handler(Looper.getMainLooper())
    private var gameTimer: CountDownTimer? = null

    private val imageSwitcher = object : Runnable {
        override fun run() {
            imageArray.forEach { it.visibility = View.INVISIBLE }
            val randomIndex = Random.nextInt(imageArray.size)
            imageArray[randomIndex].visibility = View.VISIBLE
            handler.postDelayed(this, IMAGE_SWITCH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.catchFruits = this
        imageArray.addAll(
            listOf(
                binding.ivApple, binding.ivBanana, binding.ivCherry,
                binding.ivGrapes, binding.ivKiwi, binding.ivOrange,
                binding.ivPear, binding.ivStrawberry, binding.ivWatermelon
            )
        )
        startGame()
    }

    fun increaseScore() {
        score += 1
        updateScoreText()
    }

    private fun startGame() {
        score = 0
        updateScoreText()
        updateTimeText((GAME_DURATION_MS / TICK_INTERVAL_MS).toInt())
        imageArray.forEach { it.visibility = View.INVISIBLE }

        handler.removeCallbacks(imageSwitcher)
        handler.post(imageSwitcher)

        gameTimer?.cancel()
        gameTimer = object : CountDownTimer(GAME_DURATION_MS, TICK_INTERVAL_MS) {
            override fun onFinish() {
                binding.time = getString(R.string.time_up)
                handler.removeCallbacks(imageSwitcher)
                imageArray.forEach { it.visibility = View.INVISIBLE }
                showGameOverDialog()
            }

            override fun onTick(tick: Long) {
                updateTimeText((tick / TICK_INTERVAL_MS).toInt())
            }
        }.start()
    }

    private fun showGameOverDialog() {
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(getString(R.string.game_name))
            .setMessage(getString(R.string.game_over_message, score))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> startGame() }
            .setNegativeButton(getString(R.string.no)) { _, _ -> stopGame() }
            .show()
    }

    private fun stopGame() {
        gameTimer?.cancel()
        handler.removeCallbacks(imageSwitcher)
        score = 0
        updateScoreText()
        updateTimeText(0)
        imageArray.forEach { it.visibility = View.INVISIBLE }
    }

    private fun updateScoreText() {
        binding.score = getString(R.string.score_value, score)
    }

    private fun updateTimeText(seconds: Int) {
        binding.time = getString(R.string.time_value, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameTimer?.cancel()
        handler.removeCallbacks(imageSwitcher)
    }

    companion object {
        private const val GAME_DURATION_MS = 10_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val IMAGE_SWITCH_INTERVAL_MS = 500L
    }
}