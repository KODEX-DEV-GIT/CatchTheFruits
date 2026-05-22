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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.halil.ozel.catchthefruits.databinding.ActivityMainBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var score = 0
    private val imageArray = mutableListOf<ImageView>()
    private val handler = Handler(Looper.getMainLooper())
    private var gameTimer: CountDownTimer? = null
    private var mInterstitialAd: InterstitialAd? = null
    private var mRewardedAd: RewardedAd? = null
    
    // Ad pacing variables
    private var gamesPlayed = 0
    private val INTERSTITIAL_FREQUENCY = 3 // Show interstitial every 3 games

    private val imageSwitcher = object : Runnable {
        override fun run() {
            imageArray.forEach { 
                it.visibility = View.INVISIBLE 
                it.scaleX = 0.5f
                it.scaleY = 0.5f
                it.rotation = 0f
            }
            val randomIndex = Random.nextInt(imageArray.size)
            val currentImage = imageArray[randomIndex]
            currentImage.visibility = View.VISIBLE
            
            // Cartoon pop-in animation
            val randomRotation = Random.nextInt(-15, 15).toFloat()
            currentImage.rotation = randomRotation
            currentImage.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(150)
                .withEndAction {
                    currentImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }.start()
                
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
        loadRewardedAd()
        
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
        
        // Pulse the score text
        binding.tvScore.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                binding.tvScore.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()

        // Squash and fade out the fruit
        view.animate()
            .scaleX(1.3f)
            .scaleY(0.7f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        view.visibility = View.INVISIBLE
                        view.alpha = 1f
                    }.start()
            }.start()
    }

    private fun startGame(extraTimeMs: Long = GAME_DURATION_MS) {
        if (extraTimeMs == GAME_DURATION_MS) {
            score = 0
            updateScoreText()
        }
        
        updateTimeText((extraTimeMs / TICK_INTERVAL_MS).toInt())
        imageArray.forEach { it.visibility = View.INVISIBLE }

        handler.removeCallbacks(imageSwitcher)
        handler.post(imageSwitcher)

        gameTimer?.cancel()
        gameTimer = object : CountDownTimer(extraTimeMs, TICK_INTERVAL_MS) {
            override fun onFinish() {
                binding.time = getString(R.string.time_up)
                handler.removeCallbacks(imageSwitcher)
                imageArray.forEach { it.visibility = View.INVISIBLE }
                
                gamesPlayed++
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
                Log.d("MainActivity", "Interstitial ad failed to load: ${adError.message}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("MainActivity", "Interstitial ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, getString(R.string.admob_rewarded_id), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("MainActivity", "Rewarded ad failed to load: ${adError.message}")
                mRewardedAd = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("MainActivity", "Rewarded ad was loaded.")
                mRewardedAd = rewardedAd
            }
        })
    }

    private fun showGameOverDialog() {
        // Safe Interstitial Pacing: Only show every N games
        if (gamesPlayed % INTERSTITIAL_FREQUENCY == 0) {
            if (mInterstitialAd != null) {
                mInterstitialAd?.show(this)
                // Load the next interstitial ad for the next time it's needed
                loadInterstitialAd()
            } else {
                Log.d("MainActivity", "The interstitial ad wasn't ready yet.")
            }
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_game_over, null)
        val tvScoreMessage = dialogView.findViewById<TextView>(R.id.tvScoreMessage)
        val btnPlayAgain = dialogView.findViewById<Button>(R.id.btnPlayAgain)
        val btnExit = dialogView.findViewById<Button>(R.id.btnExit)
        val btnWatchAd = dialogView.findViewById<Button>(R.id.btnWatchAd)

        tvScoreMessage.text = "Your score: $score"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Only show the rewarded ad button if an ad is actually loaded
        if (mRewardedAd != null) {
            btnWatchAd.visibility = View.VISIBLE
            btnWatchAd.setOnClickListener {
                mRewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Load the next rewarded ad
                        loadRewardedAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("MainActivity", "Rewarded ad failed to show.")
                        mRewardedAd = null
                    }
                }
                
                mRewardedAd?.show(this, OnUserEarnedRewardListener { rewardItem ->
                    // Reward the user with 10 extra seconds
                    dialog.dismiss()
                    startGame(10_000L) // 10 seconds extra
                })
            }
        } else {
            btnWatchAd.visibility = View.GONE
        }

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
        binding.score = "⭐ $score"
    }

    private fun updateTimeText(seconds: Int) {
        binding.time = "⏱️ $seconds"
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