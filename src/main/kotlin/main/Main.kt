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
            val main = Main()
            main.getApplicationList()
            main.saveApplicationInfo()
        }
    }

    private val executorService = Executors.newFixedThreadPool(1)
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()
    private val inPath = Paths.get("Input.txt")
    private val outPath = Paths.get("Output.txt")
    private lateinit var applicationList: List<String>

    private fun getApplicationList() {
        applicationList = Files.readAllLines(inPath)
        println(applicationList.first())
    }

    private fun saveApplicationInfo() {
        executorService.execute(writeApplicationInfo(applicationList.first().toInt()))
        executorService.shutdown()
    }

    private fun writeApplicationInfo(id: Int): Runnable {
        return Runnable {
            apiCall.getApplicationInfo(id).subscribe {
                val appInfo = gson.toJson(it.results.first())
                println(appInfo)
                Files.write(outPath, appInfo.toString().toByteArray(), StandardOpenOption.APPEND);
            }
        }
    }
}