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
    private val outPath = Paths.get("Output.txt")
    private val outCosinePath = Paths.get("CosineOutput.txt")

    private lateinit var applicationList: List<String>
    private lateinit var applicationInfoList: List<ApplicationInfoResponse.Result>
    private lateinit var wordSet: MutableSet<String>
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

    private fun createWordSet() {
        wordSet = mutableSetOf()
        applicationInfoList.forEach {
            it.description.split(" ").forEach { wordSet.add(it) }
        }
    }

    private fun createMatrix() {
        textMap = hashMapOf()
        applicationInfoList.forEach {
            val wordArray = DoubleArray(wordSet.size)
            val descriptionWords = it.description.split(" ")
            wordSet.forEachIndexed { index, s ->
                val count = descriptionWords.count { it == s }
                wordArray[index] = count.toDouble()
            }
            textMap.put(it.trackName, wordArray)
        }
        println(wordSet)
        println()
        val array = textMap.values.toTypedArray()
        matrix = MatrixUtils.createRealMatrix(array)
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
        return CSVWriter(FileWriter(file))
    }
}
