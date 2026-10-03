# Faz 0 sentetik fixture seti

Üç dosya `tools/GenerateTestFixtures.java` tarafından sıfırdan üretilmiştir: 64×64 RGB PNG, EXIF/GPS/kişi/yüz/kullanıcı fotoğrafı içermez. Her dosya tarayıcının 10 KiB minimumundan ve hash okuyucunun 8 KiB tamponundan büyüktür.

| Dosya | Kontrat |
|---|---|
| base.png | Sabit seed ile oluşturulan renkli gürültü |
| exact-copy.png | base.png ile byte düzeyinde aynı; adı farklı |
| brighter.png | Her renk kanalına saturating +20; byte düzeyinde farklı |

Generator ve bu üç fixture **CC0-1.0** kapsamında kamuya adanmıştır: [CC0 metni](https://creativecommons.org/publicdomain/zero/1.0/). Bu bildirim uygulamanın mevcut kaynak kodunun lisansını değiştirmez. Kaynak kod lisansı ayrıca doğrulanmalıdır.

JDK 17+ ile proje kökünden `java tools/GenerateTestFixtures.java`. Generator mevcut dosya varsa hata verir; otomatik silme/overwrite yapmaz. Yeniden üretimi boş, izole proje kopyasında yapın. `SHA256SUMS` binary referansıdır; generator her JDK/OS'ta aynı uncompressed PNG/zlib byte'larını üretir.

Bu set full decode/pHash ground truth, JPEG/HEIC/RAW, gerçek MediaStore izinleri veya 10k/20k performans kanıtı değildir. Brighter varyantın perceptual threshold sınıfı henüz kalibre edilmemiştir. JVM testleri stream/hash ve fixture bütünlüğünü doğrular; gerçek Android bitmap/Room/izin testleri sonraki fazlardadır.
