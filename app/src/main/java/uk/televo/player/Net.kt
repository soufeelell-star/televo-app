package uk.televo.player

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Tiny threading helper so we don't pull in extra libraries. */
object Net {
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun run(background: () -> Unit) { io.execute(background) }
    fun ui(action: () -> Unit) { main.post(action) }
}
