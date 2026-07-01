package network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> toResultFlow(call: suspend () -> NetWorkResult<T?>): Flow<NetWorkResult<T>> {
    return flow {
        emit(NetWorkResult.Loading(true))
        try {
            val response = call()
            emit(NetWorkResult.Success(response.data))
        } catch (e: Exception) {
            emit(NetWorkResult.Error(null, e.message ?: "Error desconocido"))
        }
    }
}
