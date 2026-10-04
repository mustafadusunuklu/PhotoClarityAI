package com.photoclarity.ai.domain.model

/**
 * Real-time progress reported during a scan.
 */
sealed class ScanProgress {
    data class Stage(val phase: ScanPhase) : ScanProgress()
    data class Counting(val total: Int) : ScanProgress()
    data class Hashing(val current: Int, val total: Int, val currentPhotoName: String) : ScanProgress()
    data class Comparing(val current: Int, val total: Int) : ScanProgress()
    data class Grouping(val groupsFound: Int) : ScanProgress()
    object Completed : ScanProgress()
    data class Error(val message: String) : ScanProgress()
    object Cancelled : ScanProgress()
}

enum class ScanPhase(val label: String) {
    METADATA("Fotoğraf listesi okunuyor"), HASHING("Fotoğraf özellikleri hesaplanıyor"),
    EXACT("Aynı dosyalar eşleştiriliyor"), VISUAL("Görsel benzerlikler karşılaştırılıyor"),
    BURST("Seri çekimler doğrulanıyor"), GROUPING("Sonuçlar hazırlanıyor")
}

/**
 * Progression steps shown in the live stream UI.
 */
enum class ScanStep(val label: String) {
    SCAN_FOLDERS("Yerel klasörler taranıyor..."),
    EXTRACT_METADATA("Üst veriler çıkarılıyor..."),
    COMPUTE_SIMILARITY("Benzerlikler hesaplanıyor..."),
    MATCH_HASHES("Hash değerleri eşleştiriliyor...")
}

data class ScanStepStatus(
    val step: ScanStep,
    val status: StepStatus = StepStatus.PENDING
) {
    enum class StepStatus { PENDING, IN_PROGRESS, DONE }
}
