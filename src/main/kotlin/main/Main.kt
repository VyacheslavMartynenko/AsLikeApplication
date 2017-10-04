package main

import com.google.gson.Gson
import networking.ApiCall
import networking.model.ApplicationInfoResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors

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

    private val inPath = Paths.get("InputAll.txt")
    private val outPath = Paths.get("Output.txt")

    private lateinit var applicationList: List<String>

    private fun getApplicationList() {
        applicationList = Files.readAllLines(inPath)
    }

    private fun saveApplicationInfo() {
        for (i in 1..10) {
            println(applicationList[i])
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
        applicationInfoResponse.results.first().let {
            val appInfo = gson.toJson(it)
            println(appInfo)
            Files.write(outPath, appInfo.toString().toByteArray(), StandardOpenOption.APPEND)
        }
    }
}