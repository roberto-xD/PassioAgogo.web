package viewmodel

sealed class HomeScreenState{
    data object Loading: HomeScreenState()
    data class Error(val errorMessage: String):HomeScreenState()
    data class Success(val responseData: String?): HomeScreenState()
}
data class HomeState(
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    val responseData: String? = null,
){
    fun toUiState():HomeScreenState{
        return if (isLoading){
            HomeScreenState.Loading
        }else if(errorMessage.isNotEmpty()){
            HomeScreenState.Error(errorMessage)
        }else{
            HomeScreenState.Success(responseData)
        }
    }
}
