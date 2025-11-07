package com.example.flowpaths.data.remote

import android.util.Log
import com.example.flowpaths.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
// 🛑 REMOVEMOS O IMPORT DE SCHEMA
// import com.google.ai.client.generativeai.type.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// 💡 MODELOS DE DADOS UNIFICADOS (Permanecem, pois são necessários para a deserialização)
@Serializable
data class PontoMapa(
    val latitude: Double,
    val longitude: Double,
    val nome: String? = null
)

@Serializable
data class PercursoRecomendado(
    val recomendacao: String,
    @SerialName("tipo_percurso")
    val tipoPercurso: String,
    @SerialName("pontos_chave")
    val pontosChave: List<PontoMapa>
)
// --------------------------------------------------------------------------------------


class GeminiMoodAnalyzer {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f

            // 🛑 REMOVEMOS A DEFINIÇÃO responseSchema para resolver o erro de compilação
            // No entanto, ainda pedimos JSON na instrução.
            responseMimeType = "application/json"
        }
    )

    /**
     * Analisa o humor do utilizador e obtém uma recomendação de percurso (no formato JSON).
     */
    suspend fun getVibeRecommendation(
        moodText: String,
        moodIcon: String
    ): Result<PercursoRecomendado> {
        return withContext(Dispatchers.IO) {
            try {

                // ✅ PROMPT APERFEIÇOADO para forçar a saída JSON
                val prompt = """
                    Age como um assistente de bem-estar para a app "FlowPaths".
                    O utilizador sente-se: "$moodText"
                    O ícone de humor selecionado foi: "$moodIcon"

                    A tua resposta DEVE ser estritamente um objeto JSON que obedece ao seguinte formato (não inclua formatação Markdown como ```json):
                    {
                      "recomendacao": "Texto empático e breve, máximo 2 frases.",
                      "tipo_percurso": "Curto, Longo, Relaxante ou Desafiante",
                      "pontos_chave": [
                        {"latitude": 38.707751, "longitude": -9.136691, "nome": "Ponto A"},
                        {"latitude": 38.710151, "longitude": -9.138891, "nome": "Ponto B"},
                        {"latitude": 38.712551, "longitude": -9.141091, "nome": "Ponto C"}
                      ]
                    }

                    Gera uma recomendação e três pontos de mapa (coordenadas fictícias e plausíveis em Portugal), seguindo este formato EXATO.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)

                // 💡 NOVO LOG: Logar a resposta bruta (ANTES do parse)
                Log.d("GEMINI_JSON_OUTPUT", "Resposta Bruta: ${response.text}")

                if (response.text.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Resposta vazia da IA."))
                }

                // 💡 PARSE DO JSON
                val jsonResponse = Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }.decodeFromString<PercursoRecomendado>(response.text!!)

                // 3. Devolver o resultado
                Result.success(jsonResponse)

            } catch (e: Exception) {
                Log.e("GeminiMoodAnalyzer", "Falha na API Gemini/JSON: ${e.message}")
                Result.failure(Exception("Falha na API Gemini: Erro ao gerar JSON. Tente novamente."))
            }
        }
    }
}