package com.example.data.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class OpenAiTool(
    val type: String
)

data class OpenAiResponseRequest(
    val model: String,
    val input: String,
    val instructions: String? = null,
    val tools: List<OpenAiTool>? = null
)

data class OpenAiContentPart(
    val type: String? = null,
    val text: String? = null
)

data class OpenAiOutputItem(
    val type: String? = null,
    val content: List<OpenAiContentPart>? = null
)

data class OpenAiResponsePayload(
    val id: String? = null,
    @Json(name = "output_text") val outputText: String? = null,
    val output: List<OpenAiOutputItem>? = null
)

interface OpenAiResponsesApi {
    @POST("v1/responses")
    suspend fun createResponse(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiResponseRequest
    ): Response<OpenAiResponsePayload>
}

class OpenAiRepository {
    private val apiKey: String = BuildConfig.OPENAI_API_KEY

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "MY_OPENAI_API_KEY"

    private val api: OpenAiResponsesApi by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiResponsesApi::class.java)
    }

    suspend fun queryKnowledge(
        prompt: String,
        assistantName: String,
        userTitle: String,
        model: String,
        enableWebSearch: Boolean
    ): Result<AiReply> {
        if (!isConfigured) {
            return Result.failure(
                IllegalStateException(
                    "OPENAI_API_KEY is not configured. Add your OpenAI key in the AI Studio Secrets panel to enable the OpenAI Responses API knowledge engine."
                )
            )
        }
        return try {
            val tools = if (enableWebSearch) listOf(OpenAiTool(type = "web_search_preview")) else null
            val request = OpenAiResponseRequest(
                model = model.ifBlank { "gpt-4.1-mini" },
                input = prompt,
                instructions = "You are $assistantName, a refined, capable personal AI assistant addressing the user as $userTitle. Provide accurate, structured, concise answers and cite sources when web search is used.",
                tools = tools
            )
            val response = api.createResponse("Bearer $apiKey", request)
            if (response.isSuccessful) {
                val body = response.body()
                val text = body?.outputText?.takeIf { it.isNotBlank() }
                    ?: body?.output
                        ?.flatMap { it.content.orEmpty() }
                        ?.mapNotNull { it.text }
                        ?.joinToString("\n")
                        ?.takeIf { it.isNotBlank() }
                    ?: "OpenAI returned an empty response."
                Result.success(
                    AiReply(
                        text = text,
                        providerTag = "OpenAI Responses (${request.model})",
                        isPlaceholder = false
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()?.take(240)
                Result.failure(
                    IllegalStateException("OpenAI API error ${response.code()}: ${errorBody ?: response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
