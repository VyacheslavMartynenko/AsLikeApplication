package main

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.LanguageProfileReader
import com.optimaize.langdetect.text.CommonTextObjectFactories
import networking.ApiCall
import networking.model.ApplicationInfoResponse
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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
        }
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val apiCall = ApiCall.Factory.create()
    private val gson = Gson()

    private val inPath = Paths.get("InputBundleId.txt")
    private val outPath = Paths.get("Output.txt")

    private lateinit var applicationList: List<String>
    private lateinit var applicationInfoList: List<ApplicationInfoResponse.Result>

    private val languageProfiles = LanguageProfileReader().readAllBuiltIn()
    private var languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
            .withProfiles(languageProfiles)
            .build()
    private var textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText()


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
            val textObject = textObjectFactory.forText(appInfo)
            val lang = languageDetector.detect(textObject).takeIf { it.isPresent }?.get()?.language
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
        val textMap: HashMap<String, DoubleArray> = hashMapOf()
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
        val matrix = MatrixUtils.createRealMatrix(array)
        println(matrix.data.forEach { println(it.toList()) })
        println()
        factorizeMatrix(matrix)
    }

    private fun factorizeMatrix(matrix: RealMatrix) {
        val svd = SingularValueDecomposition(matrix)
        println(svd.s.data.forEach { println(it.toList()) })
        println()
    }
}
