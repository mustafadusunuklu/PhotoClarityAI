package com.photoclarity.ai.safety

import com.photoclarity.ai.core.session.RemovalCoordinator
import com.photoclarity.ai.core.session.ScanCoordinator
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access to the actual application graph for process-death acceptance tests.
 * An androidTest-only entry point cannot be implemented by the production Hilt component.
 * This interface is absent from release and exposes no exported Android component.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface Phase3TestEntryPoint {
    fun sessions(): ScanSessionRepository
    fun database(): PhotoClarityDatabase
    fun removals(): RemovalCoordinator
    fun scans(): ScanCoordinator
    fun settings(): SettingsRepository
}
