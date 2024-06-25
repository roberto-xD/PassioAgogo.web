package viewmodel

sealed class PGRemoteState<out T>{
    data object Empty: PGRemoteState<Nothing>()
    data object Loading: PGRemoteState<Nothing>()
    data class Error(val errorMessage: String):PGRemoteState<Nothing>()
    data class Success<T>(val responseData: T?): PGRemoteState<T>()
}
/*
data class HomeState(
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    val responseData: PGCatalog? = null,
){
    fun toUiState():PGRemoteState<PGCatalog>{
        return if (isLoading){
            PGRemoteState.Loading
        }else if(errorMessage.isNotEmpty()){
            PGRemoteState.Error(errorMessage)
        }else{
            PGRemoteState.Success(responseData)
        }
    }
}
*/
