# PhotoClarity AI

Mevcut Kotlin/Compose Android uygulaması; MediaStore üzerinden fotoğraf listeler, MD5/SHA-256 byte hash'leri ve pHash/aHash/dHash görsel hash'leriyle gruplar üretir, kalite/seri çekim önerileri ve silme akışı sunar. Kodda bir ML model servisi yoktur. Bazı görünen ekranlar demo/placeholder'dır; tamamlanmış özellik envanteri ve riskler [ana analiz ve roadmap](PHOTOCLARITYAI_PROJECT_ANALYSIS_AND_ROADMAP.md) içindedir.

**Production-ready değildir.** Geri dönüşüm vaadi gerçek silme davranışıyla uyuşmuyor; keeper, cache transition ve silme sonucu reconciliation sorunları vardır. Faz 0 testlerinin geçmesi bu sorunların çözüldüğü anlamına gelmez. Gerçek kişisel galeride destructive QA yapmayın.

## Build ortamı

| Bileşen | Baseline |
|---|---|
| Gradle / AGP | Wrapper 8.7 / 8.5.2 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.27 |
| Android SDK | min 26, compile/target 34, build-tools 34.0.0 |
| CI JDK / bytecode | Temurin JDK 17 / JVM 17 |
| Yerel doğrulama | Android Studio JBR 21.0.10, Windows 11 |
| Unit test araçları | JUnit 4.13.2, Mockito 5.14.2, coroutines-test 1.9.0 |

JDK 17, AGP 8.5'in minimum ve CI referansıdır. Gradle 8.7 JDK 21 ile de çalışır; yerel test bununla yapılmıştır. Terminalin JDK 25 varsayılanını kullanmayın; Gradle 8.7 için desteklenen çalıştırma JVM'i değildir. Android Studio Gradle JDK ayarı terminalden ayrı olabilir. IDE sürüm adına güvenmek yerine Gradle JDK ve Wrapper çıktısını kontrol edin. Production dependency/SDK sürümleri Faz 0'da güncellenmemiştir.

Kaynaklar: [AGP 8.5 uyumluluğu](https://developer.android.com/build/releases/past-releases/agp-8-5-0-release-notes), [Gradle JVM matrisi](https://docs.gradle.org/current/userguide/compatibility.html), [Wrapper checksum](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

## Temiz checkout / çalıştırma

Bu komutları yalnız **yeni, boş bir çalışma konumu** için kullanın; mevcut proje üzerine clone/checkout/reset yapmayın.

```bash
git clone https://github.com/mustafadusunuklu/PhotoClarityAI.git
cd PhotoClarityAI
```

Bu runbook, Faz 0 baseline commit'ini içeren checkout için geçerlidir. Eski commit'leri veya başka bir yerel kopyayı açtıysanız önce branch/commit'i doğrulayın.

Android SDK Manager ile `platforms;android-34` ve `build-tools;34.0.0` kurun; lisansları kabul edin. SDK konumunu `ANDROID_HOME` ortam değişkeniyle ya da Git tarafından ignore edilen `local.properties` içinde belirtin. Windows örneği (kendi SDK konumunuza göre):

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
```

PowerShell (JDK 17 klasörünüzü kullanın; bu makinede JBR 21 de doğrulanmıştır):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat --version
.\gradlew.bat --no-daemon clean :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :app:lintDebug --console=plain
```

Linux/macOS:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash ./gradlew --version
bash ./gradlew --no-daemon clean :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :app:lintDebug --console=plain
```

Unix script'i Git index'inde executable olarak tutulur; README ve CI `bash ./gradlew` komutunu kullanır. İlk çalıştırma internetten dependency/Gradle indirebilir; offline build ancak eksiksiz cache ile mümkündür. Gradle `--version` içindeki Kotlin 1.9.22 **Gradle'ın embedded Kotlin sürümüdür**; uygulama plugin'i 2.0.21'dir.

Android Studio'da proje kökünü açın, Gradle JDK'yi seçin, sync ve Run `app` (debug) kullanın. Test cihazı/emülatöründe yalnız sentetik galeri kullanın. Faz 0'da uygulama kurulumu/cihaz taraması doğrulanmadı.

## Çıktılar ve testlerin anlamı

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (`com.photoclarity.ai.debug`).
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`; **unsigned**, Play'e yüklenebilir imzalı release kanıtı değildir. Signing key/secret oluşturulmadı.
- Testler: `app/build/reports/tests/testDebugUnitTest/index.html`, XML `app/build/test-results/testDebugUnitTest/`.
- Lint: `app/build/reports/lint-results-debug.html` ve `.xml`; mevcut warning'ler saklanır, suppress/lint-baseline eklenmedi.
- `:app:compileDebugShaders NO-SOURCE` gibi shader görevleri normal olabilir; `:app:testDebugUnitTest` **NO-SOURCE olmamalıdır**.

24 JVM testinin bazı beklentileri bilinen hataları kaydeder (R02/R03/R04/R07/R12/R13/R14). Faz 1'de risk düzeltildiğinde ilgili test güvenli kontrata değiştirilmelidir; testleri skip ederek yeşil sonuç almak kabul edilmez. Android ContentResolver/Uri/IntentSender mock; DAO/repository/settings fake'tir. Gerçek hash/analyzer/ViewModel çağrılır. Android decoder, gerçek Room, permission consent, UI/lifecycle/device davranışı bu JVM testleriyle doğrulanmaz.

Detaylar: [Faz 0 raporu](docs/PHASE0_BASELINE_REPORT.md), [benchmark planı](docs/BENCHMARK_PLAN.md), [fixture hakları](app/src/test/resources/fixtures/README.md).

## CI ve dosya güvenliği

`.github/workflows/android-baseline.yml`: push/PR/manual, JDK 17, SDK 34, checksum kontrollü Wrapper, clean debug, unsigned release, gerçek unit test sayısı kapısı ve lint. GitHub Actions yalnız sonuç raporlarını yükler; signing/credential istemez. Yerel build ve workflow statik kontrolü, GitHub Actions run sonucunun yerine geçmez; ilgili commit'in run durumunu ayrıca doğrulayın.

`.gitignore` heap dump, `.baseline`, IDE, build/cache, makine SDK ayarı ve signing/credential dosyalarını dışlar. Ignore, dosyaların diskten silinmesi veya anonimleştirilmesi değildir. Mevcut 3.61 GiB heap dump korunur; içinde kullanıcı verisi bulunabilir. Yerel snapshot kaynaklar ve `local.properties` içerir, özel yedek olarak tutulmalıdır; paylaşmayın. Ignore, daha önce track edilmiş secret'ı geçmişten kaldırmaz; baseline geçmişinde böyle bir dosya tespit edilmedi. Genel anahtar sözcük taraması secret yokluğu garantisi değildir.

## Mimari ve haklar

Tek `:app` modülü: `core` analiz/hash/media; `data` Room/DataStore/repository; `domain` modeller/interface; `di` Hilt; `ui` Compose/ViewModel. Katman isimleri tam clean architecture garantisi değildir: domain Android `Uri` taşır, sonuçlar global `ScanResultHolder`'dadır. Faz 0 bu yapıları değiştirmez.

Önceki GitHub README'si MIT lisansı beyan ediyordu; depoda `LICENSE` dosyası yoktur. Bu beyanın kaynak hak sahipleriyle doğrulanması ve gerçek lisans metninin kararlaştırılması gerekir. Bu fazda yeni uygulama lisansı atanmadı. Sentetik fixture/generator CC0-1.0; üçüncü taraf dependency lisansları release QA'da ayrıca envanterlenmelidir.

Mevcut README'deki takım atıfları korunmuştur:

| İsim | Mevcut atıf | GitHub |
|---|---|---|
| Mustafa Düşünüklü | Navigasyon ve ana ekranlar | [mustafadusunuklu](https://github.com/mustafadusunuklu) |
| Mehmet Arda Öztürk | Veri katmanı / repository | [ardaoztrk2](https://github.com/ardaoztrk2) |
| Hakan Arslan | Analiz ve hash algoritmaları | [Hakanars](https://github.com/Hakanars) |
| Gülizar Yıldırım | UI, ekranlar ve tasarım sistemi | — |
