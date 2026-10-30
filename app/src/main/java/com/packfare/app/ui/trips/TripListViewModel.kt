package com.packfare.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.TripRepository
import com.packfare.app.data.TripSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(private val repository: TripRepository) : ViewModel() {

    /** null while loading, so the empty state doesn't flash on launch. */
    val trips: StateFlow<List<TripSummary>?> = repository.tripSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createEvent(name: String, company: String, place: String, myName: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createEvent(name, company, place, myName)) }
    }

    fun createTrip(name: String, destination: String, budgetPaise: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createTrip(name, destination, budgetPaise)
            onCreated(id)
        }
    }
}
