package com.halil.ozel.catchthefruits

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.halil.ozel.catchthefruits.databinding.ActivityMainBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var score = 0
    private val imageArray = mutableListOf<ImageView>()
    private val handler = Handler(Looper.getMainLooper())
    private var gameTimer: CountDownTimer? = null
    private var mInterstitialAd: InterstitialAd? = null

    private val imageSwitcher = object : Runnable {
        override fun run() {
            imageArray.forEach { 
                it.visibility = View.INVISIBLE 
                it.scaleX = 0.5f
                it.scaleY = 0.5f
            }
            val randomIndex = Random.nextInt(imageArray.size)
            val currentImage = imageArray[randomIndex]
            currentImage.visibility = View.VISIBLE
            currentImage.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            handler.postDelayed(this, IMAGE_SWITCH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.catchFruits = this
        
        MobileAds.initialize(this) {}
        
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
        
        loadInterstitialAd()
        
        imageArray.addAll(
            listOf(
                binding.ivApple, binding.ivBanana, binding.ivCherry,
                binding.ivGrapes, binding.ivKiwi, binding.ivOrange,
                binding.ivPear, binding.ivStrawberry, binding.ivWatermelon
            )
        )
        startGame()
    }

    fun increaseScore(view: View) {
        score += 1
        updateScoreText()
        view.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(150).withEndAction {
            view.visibility = View.INVISIBLE
            view.alpha = 1f
        }.start()
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

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, getString(R.string.admob_interstitial_id), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("MainActivity", adError.toString())
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("MainActivity", "Ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })
    }

    private fun showGameOverDialog() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            // Load the next interstitial ad for the next game over
            loadInterstitialAd()
        } else {
            Log.d("MainActivity", "The interstitial ad wasn't ready yet.")
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_game_over, null)
        val tvScoreMessage = dialogView.findViewById<TextView>(R.id.tvScoreMessage)
        val btnPlayAgain = dialogView.findViewById<Button>(R.id.btnPlayAgain)
        val btnExit = dialogView.findViewById<Button>(R.id.btnExit)

        tvScoreMessage.text = "Your score: $score"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            startGame()
        }

        btnExit.setOnClickListener {
            dialog.dismiss()
            stopGame()
        }

        dialog.show()
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