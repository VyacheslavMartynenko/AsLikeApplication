@file:Suppress("DEPRECATION")

package main

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
    //TODO fill full database
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
//            main.prepareDataSet()
            main.processDataSet()
        }
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val apiCall = ApiCall.Factory.create()

    private val stopWordsPath = Paths.get("Stopwords.txt")
    private val inPath = Paths.get("InputBundleId.txt")
    private val inCosinePath = Paths.get("SeedApps.txt")
    private val appPath = "App.csv"
    private val appCosinePath = "AppCosine.csv"
    private val matrixPath = "Matrix.csv"
    private val uPath = "U.csv"
    private val cosinePath = "Cosine.csv"
    private val selectedLanguage = "en"

    private lateinit var stopWordSet: MutableSet<String>
    private lateinit var applicationList: List<String>
    private lateinit var applicationCosineList: MutableList<String>
    private lateinit var applicationInfoList: MutableList<ApplicationInfoResponse.Result>
    private lateinit var applicationCosineInfoList: MutableList<ApplicationInfoResponse.Result>
    private lateinit var wordSet: MutableSet<String>
    private lateinit var textMap: MutableMap<String, DoubleArray>
    private lateinit var matrix: RealMatrix

    private fun prepareDataSet() {
        createStopWordSet()
        //getApplicationList()
        //saveApplicationInfo()
        getApplicationCosineList()
        saveApplicationCosineInfo()
    }

    private fun processDataSet() {
        readApplicationInfoFromCsv()
        readApplicationCosineInfoFromCsv()
        createWordSet()
        createMatrix()
//        factorizeMatrix()
//        computeCosineMap()
        computeFullCosineMap()
    }

    private fun createStopWordSet() {
        stopWordSet = Files.readAllLines(stopWordsPath).toMutableSet()
    }

    private fun getApplicationList() {
        applicationList = Files.readAllLines(inPath)
    }

    private fun getApplicationCosineList() {
        applicationCosineList = Files.readAllLines(inCosinePath)
    }

    private fun saveApplicationInfo() {
        applicationList.forEach { executorService.execute(writeApplicationInfo(it, false)) }
        executorService.shutdown()
    }

    private fun saveApplicationCosineInfo() {
        applicationCosineList.forEach { executorService.execute(writeApplicationInfo(it, true)) }
        executorService.shutdown()
    }

    private fun writeApplicationInfo(id: String, isCosine: Boolean): Runnable {
        return Runnable {
            id.toIntOrNull()?.let {
                apiCall.getApplicationInfoById(it).subscribe({ write(it, isCosine) }, { println(it) })
            } ?: apiCall.getApplicationInfoByBundleId(id).subscribe({ write(it, isCosine) }, { println(it) })
        }
    }

    private fun write(applicationInfoResponse: ApplicationInfoResponse, isCosine: Boolean) {
        applicationInfoResponse.results.firstOrNull()?.let {
            val description = it.description.replace("\'", "").replace("[^A-Za-z ]".toRegex(), " ").trim().toLowerCase()
            val languageIdentifier = LanguageIdentifier(description)
            val lang = languageIdentifier.language
            if (lang == selectedLanguage) {
                val descriptionWordSet = description.split(" ").toMutableSet()
                val filteredDescription = descriptionWordSet.filter { it.isNotBlank() && stopWordSet.contains(it).not() }.joinToString(" ")
                it.description = filteredDescription
                writeApplicationInfoToCsv(it, isCosine)
            }
        }
    }

    private fun writeApplicationInfoToCsv(app: ApplicationInfoResponse.Result, isCosine: Boolean) {
        val path = if (isCosine) appCosinePath else appPath
        val csvWriter = getCsvWriter(path)
        csvWriter.writeNext(arrayOf(app.trackName, app.description, app.trackId.toString(), app.bundleId, app.userRating.toString()))
        csvWriter.flush()
        csvWriter.close()
    }

    private fun readApplicationInfoFromCsv() {
        val csvReader = getCsvReader(appPath)
        applicationInfoList = mutableListOf()
        csvReader?.readAll()?.forEach {
            val applicationInfo = ApplicationInfoResponse.Result(it[2].toInt(), it[0], it[3], it[1], it[4].toDouble())
            applicationInfoList.add(applicationInfo)
        }
        csvReader?.close()
    }

    private fun readApplicationCosineInfoFromCsv() {
        val csvReader = getCsvReader(appCosinePath)
        applicationCosineInfoList = mutableListOf()
        csvReader?.readAll()?.forEach {
            val applicationInfo = ApplicationInfoResponse.Result(it[2].toInt(), it[0], it[3], it[1], it[4].toDouble())
            applicationCosineInfoList.add(applicationInfo)
        }
        csvReader?.close()
    }

    private fun createWordSet() {
        println("Start creation of word set...")
        wordSet = mutableSetOf()
        applicationInfoList.forEach {
            it.description.split(" ").forEach { wordSet.add(it) }
        }
//        println(wordSet)
        println("Complete creation of word set.")
        println()
    }

    private fun createMatrix() {
        println("Start creation of matrix...")
        textMap = mutableMapOf()
        applicationInfoList.forEach {
            val descriptionWords = it.description.split(" ")
            val wordArray = wordSet.map { word ->
                descriptionWords.count { it == word }.toDouble()
            }.toDoubleArray()
            textMap.put(it.trackName, wordArray)
        }
        matrix = MatrixUtils.createRealMatrix(textMap.values.toTypedArray())
//        println(matrix.data.forEach { println(it.toList()) })
        println("Complete creation of matrix.")
        println()
        writeMatrixToCsv(matrix)
    }

    private fun writeMatrixToCsv(data: RealMatrix) {
        println("Start writing matrix...")
        val csvWriter = getCsvWriter(matrixPath)
        data.data.forEach { csvWriter.writeNext(it.map { it.toString() }.toTypedArray()) }
        csvWriter.flush()
        csvWriter.close()
        println("Complete writing matrix.")
        println()
    }

    private fun factorizeMatrix() {
        println("Start factorization...")
        val svd = SingularValueDecomposition(matrix)
        println("Complete factorization.")
        writeFactorizeMatrixToCsv(svd.u.data)
//        println(svd.u.data.forEach { println(it.toList()) })
        println()
    }

    private fun writeFactorizeMatrixToCsv(data: Array<out DoubleArray>) {
        println("Start writing factorized matrix...")
        val csvWriter = getCsvWriter(uPath)
        data.map { it.map { "%.4f".format(it) }.toTypedArray() }.toTypedArray().forEach { csvWriter.writeNext(it) }
        csvWriter.flush()
        csvWriter.close()
        println("Complete writing factorized matrix.")
        println()
    }

    private fun computeCosineMap() {
        println("Start computing cosine map...")
        applicationCosineInfoList.forEach {
            val app = textMap[it.trackName] as DoubleArray
            computeCosineApp(app, it.trackName)
        }
//        println(sortedCosineMap.values.toList().subList(0, 2))
        println()
    }

    private fun computeFullCosineMap() {
        println("Start computing cosine map...")
        val cosineMap = textMap.mapValues { 0.0 }.toMutableMap()
        applicationCosineInfoList.forEach {
            val app = textMap[it.trackName] as DoubleArray
            textMap.forEach { t, u -> cosineMap[t] = cosineMap[t]?.plus(computeCosineSimilarity(app, u)) ?: 0.0 }
        }
        val mediumCosineMap = cosineMap.mapValues { it.value / applicationCosineInfoList.size }
//        println(sortedCosineMap.values.toList().subList(0, 2))
        val sortedCosineMap = mediumCosineMap.toSortedMap(Comparator { o1, o2 -> if (cosineMap[o1]!! >= cosineMap[o2]!!) -1 else 1 })
        println("Complete computing cosine map.")
        writeCosineFullAppToCsv(sortedCosineMap)
    }

    private fun computeCosineApp(app: DoubleArray, name: String) {
        val cosineMap = textMap.mapValues { computeCosineSimilarity(app, it.value) }
        val sortedCosineMap = cosineMap.toSortedMap(Comparator { o1, o2 -> if (cosineMap[o1]!! >= cosineMap[o2]!!) -1 else 1 })
        println("Complete computing cosine map.")
        writeCosineAppToCsv(sortedCosineMap, name)
    }

    private fun writeCosineAppToCsv(data: SortedMap<String, Double>, name: String) {
        println("Start writing cosine map...")
        val csvWriter = getCsvWriter("Map\\$name.csv")
        data.entries.map { arrayOf<String>(it.key, "%.4f".format(it.value)) }.forEach { csvWriter.writeNext(it) }
        csvWriter.flush()
        csvWriter.close()
        println("Complete writing cosine map...")
    }

    private fun writeCosineFullAppToCsv(data: SortedMap<String, Double>) {
        println("Start writing cosine map...")
        val csvWriter = getCsvWriter(cosinePath)
        data.entries.map { arrayOf<String>(it.key, "%.4f".format(it.value)) }.forEach { csvWriter.writeNext(it) }
        csvWriter.flush()
        csvWriter.close()
        println("Complete writing cosine map...")
    }

    private fun computeCosineSimilarity(leftVector: DoubleArray, rightVector: DoubleArray): Double {
        val dotProduct = leftVector.indices.sumByDouble { (leftVector[it] * rightVector[it]) }
        val d1 = leftVector.sumByDouble { Math.pow(it, 2.0) }
        val d2 = rightVector.sumByDouble { Math.pow(it, 2.0) }
        return if (d1 <= 0.0 || d2 <= 0.0) 0.0 else (dotProduct / (Math.sqrt(d1) * Math.sqrt(d2)))
    }

    private fun getCsvWriter(pathName: String): CSVWriter {
        val file = File(pathName)
        if (!file.isFile) {
            file.createNewFile()
        }
        return CSVWriter(FileWriter(file, true))
    }

    private fun getCsvReader(pathName: String): CSVReader? {
        val file = File(pathName)
        return if (file.isFile) {
            CSVReader(FileReader(file))
        } else {
            null
        }
    }
}
