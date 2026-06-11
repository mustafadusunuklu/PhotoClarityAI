package com.photoclarity.ai.domain.model

enum class HashAlgorithm(val displayName: String, val description: String) {
    MD5("MD5", "Hızlı, birebir aynı fotoğraflar için"),
    SHA256("SHA-256", "Güvenli, birebir aynı fotoğraflar için"),
    PHASH("pHash", "Perceptual – görsel benzerlik analizi"),
    AHASH("aHash", "Average Hash – hızlı görsel karşılaştırma"),
    DHASH("dHash", "Difference Hash – kenar bazlı karşılaştırma");

    val isPerceptual: Boolean get() = this == PHASH || this == AHASH || this == DHASH
    val isCryptographic: Boolean get() = this == MD5 || this == SHA256
}
