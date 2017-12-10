package main

import com.google.gson.Gson
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import networking.ApiCall
import networking.model.ApplicationInfoResponse
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.apache.tika.language.LanguageIdentifier
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors


class Main {
    //TODO rename to csv
    //TODO remove block words
    //TODO move files constants
    //TODO fix double precision
    //TODO fill full database
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
//            main.getApplicationList()
//            main.saveApplicationInfo()
            main.readApplicationInfoFromCsw()
            main.createWordSet()
            main.createMatrix()
            main.factorizeMatrix()
            main.computeCosineMap()
        }
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()

    private val inPath = Paths.get("InputBundleId.txt")

    private lateinit var applicationList: List<String>
    private lateinit var applicationInfoList: MutableList<ApplicationInfoResponse.Result>
    private lateinit var wordSet: MutableSet<String>
    private lateinit var textMap: MutableMap<String, DoubleArray>
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
            val languageIdentifier = LanguageIdentifier(description)
            val lang = languageIdentifier.language
            if (lang == "en") {
                writeApplicationInfoToCsw(it)
            }
        }
    }

    private fun writeApplicationInfoToCsw(app: ApplicationInfoResponse.Result) {
        val cswWriter = getCswWriter("App.csv")
        cswWriter.writeNext(arrayOf(app.trackName, app.description, app.trackId.toString(), app.bundleId, app.userRating.toString()))
        cswWriter.flush()
        cswWriter.close()
    }

    private fun readApplicationInfoFromCsw() {
        val cswReader = getCswReader("App.csv")
        applicationInfoList = mutableListOf()
        cswReader?.readAll()?.forEach {
            val applicationInfo = ApplicationInfoResponse.Result(it[2].toInt(), it[0], it[3], it[1], it[4].toDouble())
            applicationInfoList.add(applicationInfo)
        }
        cswReader?.close()
    }

    private fun createWordSet() {
        wordSet = mutableSetOf()
        applicationInfoList.forEach {
            it.description.split(" ").forEach { wordSet.add(it) }
        }
        println(wordSet)
        println()
    }

    private fun createMatrix() {
        textMap = mutableMapOf()
        applicationInfoList.forEach {
            val descriptionWords = it.description.split(" ")
            val wordArray = wordSet.map { word ->
                descriptionWords.count { it == word }.toDouble()
            }.toDoubleArray()
            textMap.put(it.trackName, wordArray)
        }
        matrix = MatrixUtils.createRealMatrix(textMap.values.toTypedArray())
        println(matrix.data.forEach { println(it.toList()) })
        println()
    }

    private fun factorizeMatrix() {
        val svd = SingularValueDecomposition(matrix)
        writeFactorizeMatrixToCsv(svd.u.data)
        println(svd.u.data.forEach { println(it.toList()) })
        println()
    }

    private fun writeFactorizeMatrixToCsv(data: Array<out DoubleArray>) {
        val csvWriter = getCswWriter("U.csv")
        data.map { it.map { it.toString() }.toTypedArray() }.toTypedArray().forEach { csvWriter.writeNext(it) }
        csvWriter.flush()
        csvWriter.close()
    }

    private fun computeCosineMap() {
        val test = textMap[applicationInfoList[0].trackName] as DoubleArray
        val cosineMap = textMap.mapValues { computeCosineSimilarity(test, it.value) }
        val sortedCosineMap = cosineMap.toSortedMap(Comparator { o1, o2 -> if (cosineMap[o1]!! >= cosineMap[o2]!!) -1 else 1 })
        writeCosineMapToCsv(sortedCosineMap)
        println(sortedCosineMap.values.toList().subList(0, 2))
        println()
    }

    private fun writeCosineMapToCsv(data: SortedMap<String, Double>) {
        val csvWriter = getCswWriter("Cosine.csv")
        data.entries.map { arrayOf<String>(it.key, it.value.toString()) }.forEach { csvWriter.writeNext(it) }
        csvWriter.flush()
        csvWriter.close()
    }

    private fun computeCosineSimilarity(leftVector: DoubleArray, rightVector: DoubleArray): Double {
        val dotProduct = leftVector.indices.sumByDouble { (leftVector[it] * rightVector[it]) }
        val d1 = leftVector.sumByDouble { Math.pow(it, 2.0) }
        val d2 = rightVector.sumByDouble { Math.pow(it, 2.0) }
        return if (d1 <= 0.0 || d2 <= 0.0) 0.0 else (dotProduct / (Math.sqrt(d1) * Math.sqrt(d2)))
    }

    private fun getCswWriter(pathName: String): CSVWriter {
        val file = File(pathName)
        if (!file.isFile) {
            file.createNewFile()
        }
        return CSVWriter(FileWriter(file, true))
    }

    private fun getCswReader(pathName: String): CSVReader? {
        val file = File(pathName)
        return if (file.isFile) {
            CSVReader(FileReader(file))
        } else {
            null
        }
    }
}
