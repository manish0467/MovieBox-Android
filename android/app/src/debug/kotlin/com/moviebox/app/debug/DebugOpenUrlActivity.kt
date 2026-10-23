package com.moviebox.app.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.moviebox.app.R
import com.moviebox.app.player.PlayerActivity

/** Debug-only entry point for testing local or remote playback URLs. */
class DebugOpenUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(ARG_URL).isNullOrBlank()) {
            showUrlForm()
        } else {
            openPlayer(
                url = intent.getStringExtra(ARG_URL).orEmpty(),
                subtitle = intent.getStringExtra(ARG_SUBTITLE),
                title = intent.getStringExtra(ARG_TITLE),
                headers = intent.getStringArrayListExtra(ARG_HEADERS).orEmpty(),
            )
        }
    }

    private fun showUrlForm() {
        setContentView(R.layout.activity_debug_open_url)
        val urlField = findViewById<EditText>(R.id.debug_url)
        val subtitleField = findViewById<EditText>(R.id.debug_subtitle)
        val titleField = findViewById<EditText>(R.id.debug_title)
        val headersField = findViewById<EditText>(R.id.debug_headers)

        findViewById<Button>(R.id.debug_open).setOnClickListener {
            val url = urlField.text.toString().trim()
            if (url.isBlank()) {
                urlField.error = getString(R.string.debug_url_required)
                return@setOnClickListener
            }
            openPlayer(
                url = url,
                subtitle = subtitleField.text.toString().trim().takeIf(String::isNotEmpty),
                title = titleField.text.toString().trim().takeIf(String::isNotEmpty),
                headers = headersField.text.toString()
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList(),
            )
        }
    }

    private fun openPlayer(url: String, subtitle: String?, title: String?, headers: List<String>) {
        if (url.isBlank()) {
            Toast.makeText(this, R.string.debug_url_required, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val playerIntent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, url)
        subtitle?.let { playerIntent.putExtra(PlayerActivity.EXTRA_SUBTITLE_URL, it) }
        title?.let { playerIntent.putExtra(PlayerActivity.EXTRA_TITLE, it) }
        playerIntent.putStringArrayListExtra(PlayerActivity.EXTRA_HEADERS, ArrayList(headers))
        startActivity(playerIntent)
        finish()
    }

    private companion object {
        const val ARG_URL = "url"
        const val ARG_SUBTITLE = "subtitle"
        const val ARG_TITLE = "title"
        const val ARG_HEADERS = "headers"
    }
}
