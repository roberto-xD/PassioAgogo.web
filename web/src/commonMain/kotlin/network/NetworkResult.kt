package network

enum class ApiStatus { SUCCESS, ERROR, LOADING }

sealed class NetworkResult<out T>(
    val status: ApiStatus,
    val data: T?,
    val message: String?,
) {
    data class Success<out T>(val value: T?) :
        NetworkResult<T>(ApiStatus.SUCCESS, value, null)

    data class Error<out T>(val value: T?, val exception: String) :
        NetworkResult<T>(ApiStatus.ERROR, value, exception)

    data class Loading<out T>(val isLoading: Boolean) :
        NetworkResult<T>(ApiStatus.LOADING, null, null)
}
