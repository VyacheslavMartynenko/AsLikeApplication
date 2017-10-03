package networking.model

import com.google.gson.annotations.SerializedName

data class ApplicationInfoResponse(@SerializedName("resultCount") val resultCount: String,
                                   @SerializedName("results") val results: List<Result>) {
    public data class Result(@SerializedName("trackId") val trackId: Int,
                      @SerializedName("trackName") val trackName: String,
                      @SerializedName("bundleId") val bundleId: String,
                      @SerializedName("description") val description: String,
                      @SerializedName("averageUserRatingForCurrentVersion") val userRating: Double)
}