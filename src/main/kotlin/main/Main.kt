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
//            val str = "stf"
//            val eqw = "12"
//            str.toIntOrNull()?.let { println(it) } ?: println(str)
//            eqw.toIntOrNull()?.let { println(it) } ?: println(eqw)
        }
    }

    private val executorService = Executors.newFixedThreadPool(1)
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()
    private val inPathId = Paths.get("InputId.txt")
    private val inPathBundleId = Paths.get("InputBundleId.txt")
    private val outPath = Paths.get("Output.txt")
    private lateinit var applicationListId: List<String>
    private lateinit var applicationListBundleId: List<String>

    private fun getApplicationList() {
        applicationListId = Files.readAllLines(inPathId)
        applicationListBundleId = Files.readAllLines(inPathBundleId)
        println(applicationListId.first())
        println(applicationListBundleId.first())
    }

    private fun saveApplicationInfo() {
        executorService.execute(writeApplicationInfoId(applicationListId.first().toInt()))
        executorService.execute(writeApplicationInfoBundleId(applicationListBundleId.first()))
        executorService.shutdown()
    }

    private fun writeApplicationInfoId(id: Int): Runnable {
        return Runnable {
            apiCall.getApplicationInfoById(id).subscribe {
                val appInfo = gson.toJson(it.results.first())
                println(appInfo)
                Files.write(outPath, appInfo.toString().toByteArray(), StandardOpenOption.APPEND)
            }
        }
    }

    private fun writeApplicationInfoBundleId(bundleId: String): Runnable {
        return Runnable {
            apiCall.getApplicationInfoByBundleId(bundleId).subscribe {
                val appInfo = gson.toJson(it.results.first())
                println(appInfo)
                Files.write(outPath, appInfo.toString().toByteArray(), StandardOpenOption.APPEND)
            }
        }
    }
}