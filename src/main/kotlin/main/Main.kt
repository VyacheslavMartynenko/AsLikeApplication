package main

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.opencsv.CSVWriter
import networking.ApiCall
import networking.model.ApplicationCosine
import networking.model.ApplicationInfoResponse
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.apache.tika.language.LanguageIdentifier
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors


class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
            //main.getApplicationList()
            //main.saveApplicationInfo()
            main.readApplicationInfo()
            main.createMatrix()
            main.factorizeMatrix()
            main.computeCosineMap()
        }
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()

    private val inPath = Paths.get("InputBundleId.txt")
    private val outPath = Paths.get("Output.txt")
    private val outCosinePath = Paths.get("CosineOutput.txt")
    private val outFactorizePath = Paths.get("FactorizeOutput.txt")

    private lateinit var applicationList: List<String>
    private lateinit var applicationInfoList: List<ApplicationInfoResponse.Result>
    private lateinit var textMap: HashMap<String, DoubleArray>
    private lateinit var matrix: RealMatrix

    private fun getApplicationList() {
        applicationList = Files.readAllLines(inPath)
    }

    private fun saveApplicationInfo() {
        applicationList.forEach { executorService.execute(writeApplicationInfo(it)) }
        executorService.shutdown()
    }

    private fun writeApplicationInfo(id: String): Runnable {
        return Runnable {
            id.toIntOrNull()?.let {
                apiCall.getApplicationInfoById(it).subscribe({ write(it) }, { println(it) })
            } ?: apiCall.getApplicationInfoByBundleId(id).subscribe({ write(it) }, { println(it) })
        }
    }

    private fun write(applicationInfoResponse: ApplicationInfoResponse) {
        applicationInfoResponse.results.firstOrNull()?.let {
            val description = it.description.replace("\'", "").replace("[^A-Za-z ]".toRegex(), " ").trim().toLowerCase()
            it.description = description
            val appInfo = gson.toJson(it).replace("\\n", " ").replace(" {2,}".toRegex(), " ")
            val languageIdentifier = LanguageIdentifier(appInfo)
            val lang = languageIdentifier.language
            if (lang == "en") {
                Files.write(outPath, appInfo.plus(", ").toByteArray(), StandardOpenOption.APPEND)
            }
        }
    }

    private fun readApplicationInfo() {
        val description = Files.readAllLines(outPath).joinToString()
        applicationInfoList = gson.fromJson(description, object : TypeToken<List<ApplicationInfoResponse.Result>>() {}.type)
    }

    private fun createMatrix() {
        val applications = mutableSetOf<String>()
        val words = mutableSetOf<String>()
        applicationInfoList.forEach {
            applications.add(it.trackName)
            it.description.split(" ").forEach { words.add(it) }
        }
        fillMatrix(words)
    }

    private fun fillMatrix(words: Set<String>) {
        textMap = hashMapOf()
        applicationInfoList.forEach {
            val wordArray = DoubleArray(words.size)
            val descriptionWords = it.description.split(" ")
            words.forEachIndexed { index, s ->
                val count = descriptionWords.count { it == s }
                wordArray[index] = count.toDouble()
            }
            textMap.put(it.trackName, wordArray)
        }
        println(words)
        println()
        val array = textMap.values.toTypedArray()
        matrix = MatrixUtils.createRealMatrix(array)
        println(matrix.data.forEach { println(it.toList()) })
        println()
    }

    private fun factorizeMatrix() {
        val svd = SingularValueDecomposition(matrix)
        svd.u.data.forEach { Files.write(outFactorizePath, it.toList().toString().plus(", ").plus(System.lineSeparator()).toByteArray(), StandardOpenOption.APPEND) }
        println(svd.u.data.forEach { println(it.toList()) })
        println()
        writeToCsv(svd.u.data)
    }

    private fun writeToCsv(data: Array<out DoubleArray>) {
        val csvWriter = CSVWriter(FileWriter(getFile("U.csv")))
        data.forEach { csvWriter.writeNext(it.map { it.toString() }.toTypedArray()) }
        csvWriter.flush()
        csvWriter.close()
    }

    private fun computeCosineMap() {
        val test = textMap[applicationInfoList[0].trackName] as DoubleArray
        val cosineMap: HashMap<String, Double> = hashMapOf()
        textMap.forEach { t, u -> cosineMap.put(t, computeCosineSimilarity(test, u)) }
        val sortedMap = cosineMap.toSortedMap(Comparator { o1, o2 -> if (cosineMap[o1]!! >= cosineMap[o2]!!) -1 else 1 })
        cosineMap.forEach { t, u -> Files.write(outCosinePath, gson.toJson(ApplicationCosine(t, u)).plus(", ").toByteArray(), StandardOpenOption.APPEND) }
        println(sortedMap.values.toList().subList(0, 2))
        println()
    }

    private fun computeCosineSimilarity(leftVector: DoubleArray, rightVector: DoubleArray): Double {
        val dotProduct = leftVector.indices.sumByDouble { (leftVector[it] * rightVector[it]) }
        val d1 = leftVector.sumByDouble { Math.pow(it, 2.0) }
        val d2 = rightVector.sumByDouble { Math.pow(it, 2.0) }
        return if (d1 <= 0.0 || d2 <= 0.0) 0.0 else (dotProduct / (Math.sqrt(d1) * Math.sqrt(d2)))
    }

    private fun getFile(pathName: String): File {
        val file = File(pathName)
        if (!file.isFile) {
            file.createNewFile()
        }
        return file
    }
}
