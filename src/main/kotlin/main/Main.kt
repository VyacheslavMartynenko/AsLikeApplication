package main

import networking.ApiCall
import java.util.concurrent.Executors

object Main {
    private val executorService = Executors.newFixedThreadPool(1)
    private val apiCall = ApiCall.Factory.create()

    @JvmStatic
    fun main(args: Array<String>) {
        executorService.execute(getApplicationInfo(1226926872))
        executorService.shutdown()
    }

    private fun getApplicationInfo(id: Int): Runnable {
        return Runnable {
            apiCall.getApplicationInfo(id).subscribe {
                it.results.forEach {
                    print(it.description + " " + it.userRating)
                }
            }
        }
    }
}