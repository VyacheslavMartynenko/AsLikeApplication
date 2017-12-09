package networking.model

import com.google.gson.annotations.SerializedName

data class ApplicationCosine(@SerializedName("trackName") val trackName: String,
                             @SerializedName("similarity") val similarity: Double)