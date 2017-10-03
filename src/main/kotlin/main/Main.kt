package main

import networking.ApiCall
import java.util.concurrent.Executors

object Main {
    val executorService = Executors.newFixedThreadPool(1)
    val apiCall = ApiCall.Factory.create()

    @JvmStatic
    fun main(args: Array<String>) {
        executorService.execute(getRunnable(1226926872))
        executorService.shutdown()
    }

    fun getRunnable(id: Int): Runnable {
        return Runnable { apiCall.getApplicationInfo(id).subscribe { it.results.forEach { print(it.description) } } }
    }
}