package dev.mauve.fit.wear

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for tile buttons: sends one command to the phone and closes again, so
 * "Set done" from the tile does not open the full app. "open" just launches [WatchActivity].
 */
class TileActionActivity : ComponentActivity() {
    private var link: PhoneLink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cmd = intent?.getStringExtra(EXTRA_CMD)
        if (cmd !in COMMANDS) {
            startActivity(Intent(this, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        val l = PhoneLink(this).also { link = it }
        lifecycleScope.launch {
            val ok = l.send(cmd!!)
            if (ok) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                Toast.makeText(this@TileActionActivity, tileText("Handy nicht erreichbar", "Phone not reachable"), Toast.LENGTH_SHORT).show()
            }
            // The phone answers with a new state item; the listener service then redraws the tile.
            TileCache.requestTileUpdate(this@TileActionActivity)
            finish()
        }
    }

    override fun onDestroy() {
        link?.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        /** Only what the phone understands from a single tap; anything else opens the app. */
        val COMMANDS = setOf("start", "done", "skip", "plus15")
    }
}
