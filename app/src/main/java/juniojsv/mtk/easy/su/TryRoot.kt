package juniojsv.mtk.easy.su

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference

/* d88b db    db d8b   db d888888b  .d88b.     d88b .d8888. db    db
   `8P' 88    88 888o  88   `88'   .8P  Y8.    `8P' 88'  YP 88    88
    88  88    88 88V8o 88    88    88    88     88  `8bo.   Y8    8P
    88  88    88 88 V8o88    88    88    88     88    `Y8b. `8b  d8'
db. 88  88b  d88 88  V888   .88.   `8b  d8' db. 88  db   8D  `8bd8'
Y8888P  ~Y8888P' VP   V8P Y888888P  `Y88P'  Y8888P  `8888Y'    YP  */

class TryRoot(
    context: WeakReference<Context>,
    private val onFinished: ((success: Boolean, log: String) -> Unit)? = null
) : AsyncTask<Void, Void, String>() {

    @SuppressLint("StaticFieldLeak")
    private val context = context.get()?.applicationContext
    private val filesDir = this.context?.filesDir
    private val shell = Runtime.getRuntime()

    override fun onPreExecute() {
        super.onPreExecute()
        context?.let {
            Toast
                .makeText(it, "Please wait", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun doInBackground(vararg args: Void?): String {
        context?.assets?.list("")?.forEach { fileName ->
            when (fileName) {
                "magiskinit", "mtk-su", "magisk-boot.sh" -> {
                    context.assets.open(fileName).also { input ->
                        FileOutputStream(
                            File(
                                filesDir,
                                fileName
                            )
                        ).also { output ->
                            var buffSize: Int
                            val buff = ByteArray(512)
                            while (true) {
                                buffSize = input.read(buff)
                                if (buffSize > 0) {
                                    output.write(buff, 0, buffSize)
                                } else break
                            }
                            input.close()
                            output.close()
                        }
                        shell.exec("chmod 755 $filesDir/$fileName").waitFor()
                    }
                }
            }
        }?.let {
            shell.exec("sh $filesDir/magisk-boot.sh $filesDir").apply {
                val stdout = BufferedReader(InputStreamReader(inputStream))
                val stderr = BufferedReader(InputStreamReader(errorStream))
                val log = StringBuilder()
                var buff: String?
                while (true) {
                    buff = stdout.readLine()?.plus("\n")
                    if (buff != null) log.append(buff)
                    else break
                }
                while (true) {
                    buff = stderr.readLine()?.plus("\n")
                    if (buff != null) log.append(buff)
                    else break
                }
                waitFor().also { exit ->
                    return "$log" + "Exit value $exit"
                }
            }
        }
        return "Can't execute"
    }

    override fun onPostExecute(log: String) {
        super.onPostExecute(log)
        context?.let {
            verifyRoot().also { result ->
                if (result) {
                    Toast
                        .makeText(
                            context, "Success! root access granted.", Toast.LENGTH_SHORT
                        )
                        .show()
                } else {
                    Toast
                        .makeText(
                            context,
                            "Failed to gain root access, please try again.",
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
                onFinished?.invoke(result, log) ?: Log.d("Try Root", "Callback not implemented")
            }
        }
    }

    private fun verifyRoot(): Boolean {
        arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        ).find { dir -> File(dir).exists() }
            ?.let { return true }
            ?: return false
    }

}