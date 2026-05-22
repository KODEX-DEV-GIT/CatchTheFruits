package com.halil.ozel.catchthefruits

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import android.widget.FrameLayout
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
    private lateinit var sharedPreferences: SharedPreferences
    
    // Ad pacing variables
    private var gamesPlayed = 0
    private val INTERSTITIAL_FREQUENCY = 3 // Show interstitial every 3 games
    
    // Combo system variables
    private var lastTapTime = 0L
    private var currentCombo = 0
    private val COMBO_TIME_WINDOW_MS = 800L // Must tap within 800ms to keep combo
    
    // Difficulty progression
    private var currentSwitchInterval = IMAGE_SWITCH_INTERVAL_MS
    
    // Sound variables
    private lateinit var soundPool: SoundPool
    private var soundPop = 0
    private var soundGameOver = 0
    private var soundHighScore = 0
    private var isSoundEnabled = true

    private val imageSwitcher = object : Runnable {
        override fun run() {
            // Hide all fruits and bomb
            imageArray.forEach { 
                it.visibility = View.INVISIBLE 
                it.scaleX = 0.5f
                it.scaleY = 0.5f
                it.rotation = 0f
            }
            binding.ivBomb.visibility = View.INVISIBLE
            binding.ivBomb.scaleX = 0.5f
            binding.ivBomb.scaleY = 0.5f
            binding.ivBomb.rotation = 0f
            
            // 15% chance to spawn a bomb instead of a fruit
            val isBomb = Random.nextInt(100) < 15
            
            val currentImage = if (isBomb) {
                // Position bomb randomly in the grid
                val randomFruit = imageArray[Random.nextInt(imageArray.size)]
                val layoutParams = binding.ivBomb.layoutParams as FrameLayout.LayoutParams
                layoutParams.leftMargin = randomFruit.left
                layoutParams.topMargin = randomFruit.top
                binding.ivBomb.layoutParams = layoutParams
                binding.ivBomb
            } else {
                imageArray[Random.nextInt(imageArray.size)]
            }
            
            currentImage.visibility = View.VISIBLE
            
            // Set up click listener
            currentImage.setOnClickListener {
                if (isBomb) {
                    hitBomb(it)
                } else {
                    increaseScore(it)
                }
                it.setOnClickListener(null) // Remove listener so it can't be double tapped
            }
            
            // Cartoon pop-in animation
            val randomRotation = Random.nextInt(-15, 15).toFloat()
            currentImage.rotation = randomRotation
            currentImage.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration((currentSwitchInterval * 0.3).toLong())
                .withEndAction {
                    currentImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration((currentSwitchInterval * 0.2).toLong())
                        .withEndAction {
                            // If it's still visible at the end of its cycle, it was missed
                            if (currentImage.visibility == View.VISIBLE) {
                                currentImage.setOnClickListener(null)
                                // Only shake if a fruit was missed (missing a bomb is good!)
                                if (!isBomb) {
                                    // Shake the board slightly to indicate a miss
                                    binding.flGameBoard.animate()
                                        .translationXBy(10f)
                                        .setDuration(50)
                                        .withEndAction {
                                            binding.flGameBoard.animate()
                                                .translationXBy(-20f)
                                                .setDuration(50)
                                                .withEndAction {
                                                    binding.flGameBoard.animate()
                                                        .translationXBy(10f)
                                                        .setDuration(50)
                                                        .start()
                                                }.start()
                                        }.start()
                                }
                            }
                        }
                        .start()
                }.start()
                
            handler.postDelayed(this, currentSwitchInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.catchFruits = this
        
        sharedPreferences = getSharedPreferences("CatchTheFruitsPrefs", Context.MODE_PRIVATE)
        isSoundEnabled = sharedPreferences.getBoolean("SOUND_ENABLED", true)
        updateBestScoreText()
        
        setupSoundPool()
        
        MobileAds.initialize(this) { initializationStatus ->
            Log.d("MainActivity", "AdMob Initialized: $initializationStatus")
            
            // Load banner ad after initialization
            val adRequest = AdRequest.Builder().build()
            binding.adView.loadAd(adRequest)
            
            // Load other ads
            loadInterstitialAd()
            loadRewardedAd()
        }
        
        imageArray.addAll(
            listOf(
                binding.ivApple, binding.ivBanana, binding.ivCherry,
                binding.ivGrapes, binding.ivKiwi, binding.ivOrange,
                binding.ivPear, binding.ivStrawberry, binding.ivWatermelon
            )
        )
        
        setupStartScreen()
    }

    private fun setupStartScreen() {
        binding.llStartScreen.visibility = View.VISIBLE
        
        // Update sound button text based on saved preference
        binding.btnToggleSound.text = if (isSoundEnabled) "SOUND: ON" else "SOUND: OFF"
        
        binding.btnStartGame.setOnClickListener {
            binding.llStartScreen.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.llStartScreen.visibility = View.GONE
                    startGame()
                }.start()
        }
        
        binding.btnToggleSound.setOnClickListener {
            isSoundEnabled = !isSoundEnabled
            sharedPreferences.edit().putBoolean("SOUND_ENABLED", isSoundEnabled).apply()
            binding.btnToggleSound.text = if (isSoundEnabled) "SOUND: ON" else "SOUND: OFF"
        }
    }

    private fun setupSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
            
        soundPop = soundPool.load(this, R.raw.pop, 1)
        soundGameOver = soundPool.load(this, R.raw.game_over, 1)
        soundHighScore = soundPool.load(this, R.raw.high_score, 1)
    }

    private fun playSound(soundId: Int) {
        if (isSoundEnabled && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
        }
    }

    private fun hitBomb(view: View) {
        // Penalty for hitting bomb: lose combo and lose 2 points
        currentCombo = 0
        score = maxOf(0, score - 2)
        
        playSound(soundGameOver) // Use game over sound as a negative feedback
        
        updateScoreText()
        
        // Show floating penalty text
        showFloatingText(view, "-2", true)
        
        // Shake the screen violently
        binding.root.animate()
            .translationXBy(20f)
            .setDuration(50)
            .withEndAction {
                binding.root.animate()
                    .translationXBy(-40f)
                    .setDuration(50)
                    .withEndAction {
                        binding.root.animate()
                            .translationXBy(20f)
                            .setDuration(50)
                            .start()
                    }.start()
            }.start()

        // Explode animation
        view.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                view.visibility = View.INVISIBLE
                view.alpha = 1f
            }.start()
    }

    fun increaseScore(view: View) {
        val currentTime = System.currentTimeMillis()
        
        // Combo Logic
        if (currentTime - lastTapTime <= COMBO_TIME_WINDOW_MS) {
            currentCombo++
        } else {
            currentCombo = 1
        }
        lastTapTime = currentTime
        
        // Calculate points (base 1 + combo bonus)
        val pointsEarned = if (currentCombo >= 3) 2 else 1
        score += pointsEarned
        
        playSound(soundPop)
        
        updateScoreText()
        
        // Show floating text
        showFloatingText(view, "+$pointsEarned")
        
        // Pulse the score text (bigger pulse for combo)
        val scaleTarget = if (currentCombo >= 3) 1.4f else 1.2f
        binding.tvScore.animate()
            .scaleX(scaleTarget).scaleY(scaleTarget)
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

    private fun showFloatingText(view: View, text: String, isPenalty: Boolean = false) {
        val floatingText = TextView(this).apply {
            this.text = text
            textSize = 24f
            val colorRes = if (isPenalty) android.R.color.holo_red_dark else R.color.colorAccent
            setTextColor(resources.getColor(colorRes, theme))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 4f, resources.getColor(R.color.colorPrimaryDark, theme))
        }

        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Position the text roughly where the fruit was tapped
        layoutParams.leftMargin = view.left + (view.width / 4)
        layoutParams.topMargin = view.top + (view.height / 4)
        
        binding.flFloatingText.addView(floatingText, layoutParams)

        // Animate floating up and fading out
        floatingText.animate()
            .translationYBy(-100f)
            .alpha(0f)
            .setDuration(600)
            .withEndAction {
                binding.flFloatingText.removeView(floatingText)
            }.start()
    }

    private fun startGame(extraTimeMs: Long = GAME_DURATION_MS) {
        if (extraTimeMs == GAME_DURATION_MS) {
            score = 0
            currentCombo = 0
            currentSwitchInterval = IMAGE_SWITCH_INTERVAL_MS
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
                val secondsLeft = (tick / TICK_INTERVAL_MS).toInt()
                updateTimeText(secondsLeft)
                
                // Progressive difficulty: speed up as time runs out
                // At 10s: 500ms, At 5s: 400ms, At 2s: 300ms
                if (extraTimeMs == GAME_DURATION_MS) {
                    if (secondsLeft <= 3) {
                        currentSwitchInterval = 300L
                    } else if (secondsLeft <= 6) {
                        currentSwitchInterval = 400L
                    }
                }
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
        // Check and save best score
        val currentBest = sharedPreferences.getInt("BEST_SCORE", 0)
        var isNewHighScore = false
        
        if (score > currentBest) {
            isNewHighScore = true
            sharedPreferences.edit().putInt("BEST_SCORE", score).apply()
            updateBestScoreText()
        }

        // Safe Interstitial Pacing: Only show every N games
        // Force show on first game for testing, then every N games
        if (gamesPlayed == 1 || gamesPlayed % INTERSTITIAL_FREQUENCY == 0) {
            if (mInterstitialAd != null) {
                Log.d("MainActivity", "Showing Interstitial Ad")
                mInterstitialAd?.show(this)
                // Load the next interstitial ad for the next time it's needed
                loadInterstitialAd()
            } else {
                Log.d("MainActivity", "The interstitial ad wasn't ready yet. Loading a new one.")
                loadInterstitialAd()
            }
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_game_over, null)
        val tvScoreMessage = dialogView.findViewById<TextView>(R.id.tvScoreMessage)
        val tvBestScoreMessage = dialogView.findViewById<TextView>(R.id.tvBestScoreMessage)
        val tvNewHighScore = dialogView.findViewById<TextView>(R.id.tvNewHighScore)
        val btnPlayAgain = dialogView.findViewById<Button>(R.id.btnPlayAgain)
        val btnExit = dialogView.findViewById<Button>(R.id.btnExit)
        val btnWatchAd = dialogView.findViewById<Button>(R.id.btnWatchAd)

        tvScoreMessage.text = "Your score: $score"
        
        val displayBest = if (isNewHighScore) score else currentBest
        tvBestScoreMessage.text = "Best: $displayBest"
        
        if (isNewHighScore && score > 0) {
            playSound(soundHighScore)
            tvNewHighScore.visibility = View.VISIBLE
            // Simple pulse animation for the high score text
            tvNewHighScore.animate()
                .scaleX(1.1f).scaleY(1.1f)
                .setDuration(500)
                .withEndAction {
                    tvNewHighScore.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
                }.start()
        } else {
            playSound(soundGameOver)
            tvNewHighScore.visibility = View.GONE
        }

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
        currentCombo = 0
        updateScoreText()
        updateTimeText(0)
        imageArray.forEach { it.visibility = View.INVISIBLE }
    }

    private fun updateScoreText() {
        val comboText = if (currentCombo >= 3) " 🔥x$currentCombo" else ""
        binding.score = "⭐ $score$comboText"
    }

    private fun updateBestScoreText() {
        val best = sharedPreferences.getInt("BEST_SCORE", 0)
        binding.bestScore = "🏆 $best"
    }

    private fun updateTimeText(seconds: Int) {
        binding.time = "⏱️ $seconds"
    }

    override fun onDestroy() {
        super.onDestroy()
        gameTimer?.cancel()
        handler.removeCallbacks(imageSwitcher)
        soundPool.release()
    }

    companion object {
        private const val GAME_DURATION_MS = 10_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val IMAGE_SWITCH_INTERVAL_MS = 500L
    }
}