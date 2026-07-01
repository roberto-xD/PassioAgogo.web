package network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> toResultFlow(call: suspend () -> NetworkResult<T?>): Flow<NetworkResult<T>> {
    return flow {
        emit(NetworkResult.Loading(true))
        try {
            val response = call()
            emit(NetworkResult.Success(response.data))
        } catch (e: Exception) {
            emit(NetworkResult.Error(null, e.message ?: "Error desconocido"))
        }
    }
}
