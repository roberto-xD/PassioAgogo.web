package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.ApiStatus
import network.NetworkRepository

class HomeViewModel(
    private val networkRepository: NetworkRepository
) {
    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState
    private val _homeViewState : MutableStateFlow<HomeScreenState> = MutableStateFlow(HomeScreenState.Loading)
    val homeViewState: StateFlow<HomeScreenState> = _homeViewState

    fun getProduts(){
        CoroutineScope(Dispatchers.Unconfined).launch {
            networkRepository.getProductList().collect{ response ->
                when(response.status){
                    ApiStatus.LOADING -> {
                        _homeState.update{
                            it.copy(isLoading = true)
                        }
                    }
                    ApiStatus.ERROR -> {
                        _homeState.update{
                            it.copy(isLoading = false, errorMessage = response.message.orEmpty())
                        }
                    }
                    ApiStatus.SUCCESS -> {
                        _homeState.update {
                            it.copy(isLoading = false, errorMessage = "", responseData = response.data.orEmpty())
                        }
                    }
                }
            }
        }
    }
}