package networking

import io.reactivex.Flowable
import networking.model.ApplicationInfoResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit


interface ApiCall {

    @GET("lookup")
    fun getApplicationInfoById(@Query("id") id: Int): Flowable<ApplicationInfoResponse>

    @GET("lookup")
    fun getApplicationInfoByBundleId(@Query("bundleId") bundleId: String): Flowable<ApplicationInfoResponse>

    object Factory {
        private val BASE_URL = "https://itunes.apple.com/"
        private val NETWORK_CALL_TIMEOUT = 60

        fun create(): ApiCall {
            val builder = OkHttpClient.Builder()
            builder.readTimeout(NETWORK_CALL_TIMEOUT.toLong(), TimeUnit.SECONDS)
            builder.writeTimeout(NETWORK_CALL_TIMEOUT.toLong(), TimeUnit.SECONDS)
            val httpClient = builder.build()
            val retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build()
            return retrofit.create(ApiCall::class.java)
        }
    }
}