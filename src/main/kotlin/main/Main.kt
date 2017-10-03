package main

import com.google.gson.Gson
import networking.ApiCall
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Main().start()
        }
    }

    private val executorService = Executors.newFixedThreadPool(1)
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()
    private val path = Paths.get("output.txt")

    private fun start() {
        executorService.execute(getApplicationInfo(1226926872))
        executorService.shutdown()
    }

    private fun getApplicationInfo(id: Int): Runnable {
        return Runnable {
            apiCall.getApplicationInfo(id).subscribe {
                val appInfo = gson.toJson(it.results.first())
                Files.write(path, appInfo.toString().toByteArray(), StandardOpenOption.APPEND);
            }
        }
    }
}