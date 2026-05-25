private fun block(title: String, channel: String, score: Int, reason: String) {
        main.post {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            // Press BACK 3 times to fully exit YouTube then go HOME
            performGlobalAction(GLOBAL_ACTION_BACK)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 200)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 400)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 700)
            main.postDelayed({ showOverlay(title, reason) }, 1000)
        }
        BlockEventLogger.log(this, BlockEvent(title, channel, score, reason))
        sendBroadcast(Intent("com.safestream.BLOCK").apply {
            putExtra("title",     title)
            putExtra("channel",   channel)
            putExtra("score",     score)
            putExtra("reason",    reason)
            putExtra("timestamp", System.currentTimeMillis())
        })
    }
