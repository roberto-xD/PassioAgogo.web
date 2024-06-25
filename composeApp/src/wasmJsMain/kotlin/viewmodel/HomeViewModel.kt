package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.PGCatalog
import network.ApiStatus
import network.NetworkRepository

class HomeViewModel(
    private val networkRepository: NetworkRepository
) {
    private val _productState = MutableStateFlow(PGCatalog())
    val productState: StateFlow<PGCatalog> = _productState
//    private val _homeViewState : MutableStateFlow<HomeScreenState> = MutableStateFlow(HomeScreenState.Loading)
//    val homeViewState: StateFlow<HomeScreenState> = _homeViewState

    fun getProduts(
        store: String ?= null
    ){
        CoroutineScope(Dispatchers.Unconfined).launch {
            networkRepository.getProductList(store).collect{ response ->
                when(response.status){
                    ApiStatus.LOADING -> {
                        /*_productState.update{
                            it.copy(isLoading = true)
                        }*/
                    }
                    ApiStatus.ERROR -> {
                        /*_productState.update{
                            it.copy(isLoading = false, errorMessage = response.message.orEmpty())
                        }*/
                    }
                    ApiStatus.SUCCESS -> {
                        response.data?.items?.let { dataList ->
                            _productState.update {
                                it.copy(dataList)
                            }
                        }
                    }
                }
            }
        }
    }
}