package juniojsv.mtk.easy.su

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import juniojsv.mtk.easy.su.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.await
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var preferences: SharedPreferences
    private lateinit var github: GithubRepository
    private lateinit var binding: ActivityMainBinding
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)
        github = Retrofit.Builder().baseUrl(getString(R.string.github_api_entry))
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(GithubRepository::class.java)

        binding.mLog.makeScrollableInsideScrollView()

        if (Build.VERSION.SECURITY_PATCH.replace("-", "")
                .toInt() >= 20200301 && !preferences.getBoolean(PREF_SECURITY_PATCH_IGNORED, false)
        ) {
            AlertDialog.Builder(this).run {
                setTitle(R.string.warning_word)
                setMessage(R.string.security_patch_warning)
                setPositiveButton(getText(R.string.ignore)) { _, _ ->
                    preferences.edit(true) {
                        putBoolean(PREF_SECURITY_PATCH_IGNORED, true)
                    }
                }
                setNegativeButton(R.string.close) { _, _ ->
                    finishAndRemoveTask()
                }
                create().apply { setCanceledOnTouchOutside(false) }
            }.show()
        }

        launch {
            val update = getLatestUpdateAvailable()

            if (update != null) {
                withContext(Dispatchers.Main) {
                    getString(R.string.new_version_available).snack(
                        binding.root, true, getString(R.string.download)
                    ) {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = update.url.toUri()
                        })
                    }
                }
            }
        }

        if (!preferences.getBoolean(PREF_STARTUP_WARNING, false))
            AlertDialog.Builder(this).run {
                setTitle(getString(R.string.warning_word))
                setMessage(getString(R.string.startup_warning))
                setPositiveButton(getString(R.string.accept)) { _, _ ->
                    preferences.edit(true) {
                        putBoolean(PREF_STARTUP_WARNING, true)
                    }
                }
                create().apply { setCanceledOnTouchOutside(false) }
            }.show()

        binding.mRunAs64.apply {
            isChecked = preferences.getBoolean(PREF_RUN_AS_64_BITS, false)
            setOnCheckedChangeListener { _, isChecked ->
                preferences.edit(true) {
                    putBoolean(PREF_RUN_AS_64_BITS, isChecked)
                }
            }
        }

        binding.mVersion.text =
            String.format(
                "%s %s",
                getString(R.string.version),
                BuildConfig.VERSION_NAME
            )

        binding.mButtonDonate.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = getString(R.string.donate_url).toUri()
            })
        }

        binding.mButtonGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = getString(R.string.github_url).toUri()
            })
        }

        binding.mButtonXda.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = getString(R.string.xda_url).toUri()
            })
        }

        binding.mButtonTryRoot.setOnClickListener { button ->
            getString(R.string.please_wait).toast(this, true)
            button.isEnabled = false
            ExploitHandler(this) { result ->
                binding.mLog.text = result.log
                binding.mButtonCopy.isEnabled = true
                button.isEnabled = true
                if (result.isSuccessful)
                    getString(R.string.success).toast(this, true)
                else
                    getString(R.string.fail).toast(this, false)
            }.execute()
        }
    }
    @SuppressLint("ServiceCast")
    fun copy(view: View) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.log), binding.mLog.text))
    }
}

private suspend fun getLatestUpdateAvailable(): GithubRelease? {
    return try {
        lateinit var github: GithubRepository
        val release = github.getLatestRelease().await()
        val latest = release.tag.filter { it.isDigit() }.toInt()
        val current = BuildConfig.VERSION_NAME.filter { it.isDigit() }.toInt()

        if (current < latest) release else null
    } catch (e: Exception) {
        Log.e(LOG_VERITY_UPDATE, "${e.message}")
        null
    }
}
