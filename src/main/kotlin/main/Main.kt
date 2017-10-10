package main

import com.google.gson.Gson
import networking.ApiCall
import networking.model.ApplicationInfoResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import com.detectlanguage.DetectLanguage


class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
            main.getApplicationList()
            main.saveApplicationInfo()
        }
    }

    private val executorService = Executors.newFixedThreadPool(1)
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()

    private val inPath = Paths.get("InputBundleId.txt")
    private val outPath = Paths.get("Output.txt")

    private lateinit var applicationList: List<String>

    private fun getApplicationList() {
        DetectLanguage.apiKey = "dd91b09c32474791552a1b27e0ba0085"
        applicationList = Files.readAllLines(inPath)
    }

    private fun saveApplicationInfo() {
        for (i in 3000..3100) {
            executorService.execute(writeApplicationInfo(applicationList[i]))
        }
        executorService.shutdown()
    }

    private fun writeApplicationInfo(id: String): Runnable {
        return Runnable {
            id.toIntOrNull()?.let {
                apiCall.getApplicationInfoById(it).subscribe { write(it) }
            } ?: apiCall.getApplicationInfoByBundleId(id).subscribe { write(it) }
        }
    }

    private fun write(applicationInfoResponse: ApplicationInfoResponse) {
        applicationInfoResponse.results.firstOrNull()?.let {
            val appInfo = gson.toJson(it).replace("\\n", "")
            DetectLanguage.detect(it.description)
                    .filter { it.language == "en" && it.isReliable }
                    .forEach { Files.write(outPath, appInfo.toByteArray(), StandardOpenOption.APPEND) }
        }
    }
}