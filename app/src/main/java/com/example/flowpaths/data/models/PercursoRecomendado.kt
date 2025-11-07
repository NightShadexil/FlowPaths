import com.example.flowpaths.data.models.PontoMapa
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PercursoRecomendado(
    val recomendacao: String,
    @SerialName("tipo_percurso")
    val tipoPercurso: String,
    @SerialName("pontos_chave")
    val pontosChave: List<PontoMapa>
)