package com.photoclarity.ai.ui.session

import androidx.lifecycle.ViewModel
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(repository: ScanSessionRepository) : ViewModel() {
    val state = repository.state
}
