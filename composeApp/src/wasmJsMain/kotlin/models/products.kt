package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    var list: List<Products>
)

@Serializable
data class Products (
    @SerialName("id")
    var id: String,
)