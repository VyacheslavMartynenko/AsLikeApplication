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
    private val inPathId = Paths.get("InputId.txt")
    private val outPath = Paths.get("Output.txt")
    private lateinit var applicationListId: List<String>

    private fun getApplicationList() {
        applicationListId = Files.readAllLines(inPathId)
        println(applicationListId.first())
    }

    private fun saveApplicationInfo() {
        executorService.execute(writeApplicationInfo(applicationListId.first().toInt()))
        executorService.shutdown()
    }

    private fun writeApplicationInfo(id: Int): Runnable {
        return Runnable {
            apiCall.getApplicationInfoById(id).subscribe {
                val appInfo = gson.toJson(it.results.first())
                println(appInfo)
                Files.write(outPath, appInfo.toString().toByteArray(), StandardOpenOption.APPEND);
            }
        }
    }
}