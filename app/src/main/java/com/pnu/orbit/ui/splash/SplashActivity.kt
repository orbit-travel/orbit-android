package com.pnu.orbit.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.pnu.orbit.R
import com.pnu.orbit.ui.main.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Gentle "warp-in" of the logo so the cold start feels intentional, not abrupt.
        findViewById<android.view.View>(R.id.splashLogo)?.apply {
            alpha = 0f
            scaleX = 0.86f
            scaleY = 0.86f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(620L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        findViewById<android.view.View>(R.id.splashSubtitle)?.apply {
            alpha = 0f
            animate().alpha(1f).setStartDelay(280L).setDuration(420L).start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DELAY_MS)
    }

    companion object {
        private const val SPLASH_DELAY_MS = 900L
    }
}
