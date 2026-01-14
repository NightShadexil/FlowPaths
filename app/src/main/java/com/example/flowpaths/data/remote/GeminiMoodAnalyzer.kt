package com.example.flowpaths.data.remote

import android.util.Log
import com.example.flowpaths.BuildConfig
import com.example.flowpaths.data.models.PercursoRecomendado
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class GeminiMoodAnalyzer {



    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.CLOUD_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f // Temperatura mais baixa = Mais fiel aos factos/localização
            responseMimeType = "application/json"
        }
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun getVibeRecommendation(
        moodText: String,
        moodIcon: String?,
        dadosMeteorologicos: String,
        userLocation: String // Ex: "41.23, -8.62" (Maia)
    ): Result<PercursoRecomendado> {
        return withContext(Dispatchers.IO) {

            retryWithBackoff(times = 2, initialDelay = 2000) {
                try {
                    val icone = moodIcon ?: "Neutro"

                    // ✅ PROMPT ATUALIZADO PARA SINCRONIA PONTO <-> DESAFIO
                    val prompt = """                        
                        Atua como um Guia de Mindfulness e Terapeuta Comportamental experiente, combinando psicologia comportamental com um toque de leveza.
                    
                        **CONTEXTO DO UTILIZADOR:**
                        - 📍 GPS: "$userLocation"
                        - 😐 Humor: "$moodText"
                        - 🌡️ Clima: "$dadosMeteorologicos"
                    
                        ---
                    
                        ### 1. TOM DE VOZ (IMPORTANTE)
                        - **Empático e Respeitoso:** Evita excesso de familiaridade (NÃO uses "amigo", "mano", "olha lá").
                        - **Caloroso:** Usa uma linguagem que acolhe, mas mantém uma distância profissional saudável.
                        - **Motivador:** Incentiva a ação, não a passividade.
                    
                        ---
                    
                        ### 2. PROTOCOLO DE CLIMA (3 CENÁRIOS DISTINTOS)
                        Analisa o clima e escolhe rigorosamente um destes modos:
                    
                        **CENÁRIO A: CHUVA / TEMPESTADE (Modo Refúgio)**
                        - **Ação:** O utilizador NÃO sai do lugar. Jornada interior.
                        - **Foco:** Introspeção, conforto, escuta ativa do som da chuva.
                        - **Coordenadas:** Gera desvios micro (0.0001) apenas para a app detetar movimento técnico.
                        
                        **CENÁRIO B: FRIO (< 12ºC) MAS SEM CHUVA (Modo Vigoroso)**
                        - **Ação:** Caminhada Rápida/Vigorosa.
                        - **Motivação:** O frio não é desculpa, é combustível. O objetivo é gerar calor corporal.
                        - **Foco:** Sentir o ar no rosto, ver o fumo da respiração, movimento rítmico.
                        
                        **CENÁRIO C: BOM TEMPO / AMENO (Modo Exploração)**
                        - **Ação:** Caminhada de Descoberta (Raio 1km).
                        - **Foco:** Curiosidade visual, detalhes arquitetónicos, natureza, "Awe walks".
                    
                        ---
                    
                        ### 3. A "FÓRMULA" DOS DESAFIOS (Ciência + Sorriso)
                        Gera entre 3 a 4 desafios que sigam esta distribuição:
                        
                        1.  **O Científico (Grounding):** Baseado em TCC/Mindfulness. Foco na respiração ou sensações físicas para acalmar o sistema nervoso.
                        2.  **O "Lúdico" (O Toque Patético):** Um desafio ligeiramente absurdo ou infantil para libertar dopamina e provocar um sorriso. 
                            *Exemplos:* "Faz uma 'Power Pose' (posição de Super-Herói) durante 10 segundos para aumentar a confiança", "Segura uma caneta com os dentes para forçar o sorriso (biofeedback)", "Caminha 10 metros como se fosses um gigante", "Faz uma careta para um sinal de trânsito", "Dá um nome a uma nuvem".
                        3.  **O Criativo:** Foco na estética, fotografia ou sons.
                    
                        ---
                    
                        ### 4. INSTRUÇÕES MULTIMÉDIA (RIGOROSO)
                        A instrução DEVE terminar obrigatoriamente com o comando de ação para a app:
                        - **FOTO:** Termina com: *"Tira uma fotografia a [detalhe/cor/textura]..."*
                        - **AUDIO:** Termina com: *"Grava um áudio sobre [tema]..."* ou *"Grava o som de..."*
                        - **TEXTO:** Termina com: *"Escreve [uma palavra/pensamento]..."*
                        - **REFLEXAO:** Apenas instrução física/mental.
                    
                        ---
                    
                       ### 5. CURADORIA MUSICAL (O Segredo da Vibe)

                        **REGRA DE OURO:** A IA não pode inventar IDs. Tens de escolher **UMA** das opções abaixo que melhor se adapte à *nuance* específica do humor do utilizador.
                        
                        **Copia EXATAMENTE o campo 'URI' para o JSON.**
                    
                        **-- PARA ENERGIA / MOVIMENTO / FRIO --**
                        - "Energy Boost" (Pop/Rock animado): 
                          URI: "spotify:playlist:37i9dQZF1DX3rxVfibe1L0"
                        - "Power Walk" (Ritmo constante): 
                          URI: "spotify:playlist:37i9dQZF1DXadOVCgGhS7j"
                        - "Beast Mode" (Intenso/Treino): 
                          URI: "spotify:playlist:37i9dQZF1DX76Wlfdnj7AP"
                        - "Motivation Mix" (Inspirador): 
                          URI: "spotify:playlist:37i9dQZF1DXdxcBWuJkbcy"
                        - "Happy Hits" (Para levantar o ânimo): 
                          URI: "spotify:playlist:37i9dQZF1DXdPec7aLTmlC"
                    
                        **-- PARA CALMA / ANSIEDADE / CHUVA --**
                        - "Peaceful Piano" (Clássico/Calmo): 
                          URI: "spotify:playlist:37i9dQZF1DX4sWSpwq3LiO"
                        - "Stress Relief" (Ambiental): 
                          URI: "spotify:playlist:37i9dQZF1DWXe9gFZP0gtP"
                        - "Calm Vibes" (Acústico suave): 
                          URI: "spotify:playlist:37i9dQZF1DX1s9knjP51Oa"
                        - "Lo-Fi Beats" (Batida suave/Foco): 
                          URI: "spotify:playlist:37i9dQZF1DWWQRwui0ExPn"
                        - "Rain Sounds" (Sons de chuva/Natureza): 
                          URI: "spotify:playlist:37i9dQZF1DX8ymr6UES7vc"
                    
                        **-- PARA TRISTEZA / REFLEXÃO / CONFORTO --**
                        - "Comfort Zone" (Músicas quentinhas): 
                          URI: "spotify:playlist:37i9dQZF1DX889U0CL85jj"
                        - "Life Sucks" (Para validar a tristeza): 
                          URI: "spotify:playlist:37i9dQZF1DX3YSRoSdA634"
                        - "Alone Again" (Melancolia suave): 
                          URI: "spotify:playlist:37i9dQZF1DWX83CujKHHOn"
                        - "Acoustic Warmth" (Violão acolhedor): 
                          URI: "spotify:playlist:37i9dQZF1DX2cBWl3pZC4M"
                    
                        **-- PARA BOM TEMPO / ALEGRIA / INDIE --**
                        - "Feel Good Indie" (Descontraído): 
                          URI: "spotify:playlist:37i9dQZF1DX2sUQwD7tbmL"
                        - "Sunny Day" (Solar/Positivo): 
                          URI: "spotify:playlist:37i9dQZF1DX1BzILRveYHb"
                        - "Good Vibes" (Pop/R&B Chill): 
                          URI: "spotify:playlist:37i9dQZF1DWYBO1MoTDhZI"
                    
                        ---
                    
                        ### 6. REGRAS TÉCNICAS
                        - **REGRA INQUEBRÁVEL:** Nº de `pontos_paragem` == Nº de `desafios`.
                    
                        **ESTRUTURA JSON OBRIGATÓRIA:**
                        {
                          "titulo_dinamico": "Título Imersivo",
                          "recomendacao": "Conselho prático curto.",
                          "termo_pesquisa_spotify": "Termo descritivo da vibe",
                          "playlist_spotify_nome": "Nome exato da lista escolhida",
                          "playlist_spotify_url": "COLA_AQUI_O_URI_ESCOLHIDO_ACIMA",
                          "sentimento_dominante": "Estado emocional alvo",
                          "duracao_estimada": 900, 
                          "dados_meteorologicos": "$dadosMeteorologicos",
                          "pontos_paragem": [
                            { "nome": "Local 1", "latitude": 0.0, "longitude": 0.0 },
                            { "nome": "Local 2", "latitude": 0.0, "longitude": 0.0 },
                            { "nome": "Local 3", "latitude": 0.0, "longitude": 0.0 }
                          ],
                          "desafios": [
                            { 
                                "titulo": "Título (ex: O Passo do Gigante)", 
                                "instrucao": "Instrução lúdica... Tira uma fotografia aos teus pés.", 
                                "tipo": "FOTO", 
                                "duracao_segundos": 120, 
                                "foco_psicologico": "Desbloqueio", 
                                "status_conclusao": "PENDENTE"
                            },
                            { 
                                "titulo": "Título Calmo", 
                                "instrucao": "Instrução de respiração... Grava o som ambiente.", 
                                "tipo": "AUDIO", 
                                "duracao_segundos": 300, 
                                "foco_psicologico": "Conexão", 
                                "status_conclusao": "PENDENTE"
                            }
                          ]
                        }
                    """.trimIndent()

                    Log.d("GeminiAnalyzer", "📝 PROMPT ENVIADO:\n$prompt")

                    val response = generativeModel.generateContent(prompt)

                    if (response.text.isNullOrBlank()) {
                        throw IOException("Resposta vazia da IA")
                    }

                    // Log para debug (confirma no Logcat se as coordenadas vêm certas)
                    Log.d("GeminiAnalyzer", "JSON Gerado: ${response.text}")

                    val result = json.decodeFromString<PercursoRecomendado>(response.text!!)
                    Result.success(result)

                } catch (e: Exception) {
                    Log.e("GeminiAnalyzer", "Erro: ${e.message}")
                    throw e
                }
            }
        }
    }

    private suspend fun <T> retryWithBackoff(
        times: Int,
        initialDelay: Long,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w("GeminiAnalyzer", "Tentativa ${attempt + 1} falhou. A tentar de novo...")
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return try {
            block()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}