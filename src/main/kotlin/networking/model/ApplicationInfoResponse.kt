package networking.model

import com.google.gson.annotations.SerializedName

data class ApplicationInfoResponse(@SerializedName("resultCount") val resultCount: String,
                                   @SerializedName("results") val results: List<Result>) {
    data class Result(@SerializedName("description") val description: String)
}