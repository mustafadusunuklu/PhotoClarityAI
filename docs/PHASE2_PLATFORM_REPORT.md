# Faz 2 — Platform, izin ve yayın tabanı

Tarih: 2026-10-03–04. Başlangıç: Faz 1 commit'i `75c70152aea88558424afdc7ae5f2a0120b9b7d6`.

**Durum: implementasyon ve mevcut emülatörde temel doğrulama tamamlandı; genel kabul PARTIAL.** Eksik OS/OEM/picker/backup matrisi aşağıda açıkça korunur. Faz 3'e geçilmedi; commit/push yapılmadı. Ana roadmap'in Faz 0–7 sırası ve Faz 5 implementasyon öncesi UX tasarım gereksinimi korunur.

## Amaç ve sınır

Çalışan Faz 1 keeper/waste/silme kontratını koruyarak güncel target API, fotoğraf erişimi, sistem onay bölümleri, sistem barları ve yedekleme tabanını düzeltmek. Kalıcı typed ScanSession, WorkManager/foreground service, süreç ölümü recovery, algoritma/ölçeklenebilirlik değişiklikleri ve sonuç ekranı yeniden tasarımı yapılmaz. Global holder yalnız izin geçersizleştirmesinin mevcut ekranlara yansıması için observable yapıldı; bu Faz 3 hedef mimarisinin yerine geçmez.

## Platform ve dependency kararı

| Bileşen | Faz 1 | Faz 2 çalışma ağacı | Gerekçe |
|---|---|---|---|
| compile/target/min SDK | 34/34/26 | 36/36/26 | Güncel mobil Play gönderim tabanı; eski destek tabanı korunur |
| AGP | 8.5.2 | 8.10.1 | API 36'yı resmî destekleyen sürümün patch düzeyi |
| Gradle Wrapper | 8.7 | 8.11.1 | AGP uyumluluk matrisi; dağıtım ve JAR checksum kontrolü |
| Build Tools | 34.0.0 | 35.0.0 | AGP 8.10 varsayılanı; compile SDK ile aynı sayı olması gerekmez |
| CI JDK / bytecode | 17/17 | 17/17 | CI referansı ve minimum JDK korunur |
| Yerel runtime | JBR 21.0.10 | JBR 21.0.10 | Desteklenen runtime; terminalin JDK 25'i kullanılmaz |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.27 | Aynı | Derleyici/processor değişikliklerini aynı anda genişletmemek |
| Hilt / Room | 2.51.1 / 2.6.1 | Aynı | Yeni AGP ile build/test kapısı; daha geniş dependency güncellemeleri ayrı kontrol gerektirir |
| Compose/Coil/DataStore/AndroidX test | Önceki sürümler | Aynı | Bu fazda kapsam dışı toplu sürüm yükseltmesi yok |
| Accompanist Permissions / Gson | Doğrudan dependency | Çıkarıldı | Production ve test kaynaklarında kullanım/import yok; mevcut izin akışı ActivityResult kullanır |

Wrapper dağıtım SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`; JAR SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. Resmî checksum endpoint'leriyle karşılaştırıldı. Wrapper task iki sürümde çalıştırılarak JAR ve platform scriptleri yenilendi. CI aynı checksumları ve SDK paketlerini kullanır; en az 63 gerçek JVM sonucu ister.

3 Ekim 2026 kontrolünde mobil yeni uygulama/güncelleme için 31 Ağustos 2026'dan itibaren target API 36 gerekir. Mevcut uygulamanın keşfedilebilirlik kuralı farklıdır. Play Console uzatma veya izin beyanı onayı varsayılmadı. Kaynak: [Google Play target API politikası](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). Toolchain seçimi: [AGP 8.10 uyumluluğu](https://developer.android.com/build/releases/agp-8-10-0-release-notes).

## Erişim kontratı ve veri akışı

- Manifest artık `READ_MEDIA_VISUAL_USER_SELECTED` içerir. API 34+: `READ_MEDIA_IMAGES` ve selected izni birlikte; API 33: images; API 26–32: legacy read istenir. WRITE hâlâ yalnız API 26–28 içindir; hiçbir geniş dosya yöneticisi izni eklenmedi.
- `core/media/PhotoAccessPolicy.kt`: FULL / LIMITED / DENIED kararını OS sürümüne uygun grant üzerinden verir. Eski veya alakasız bir grant yeni Android'de tam erişim kabul edilmez. FULL selected grant'ten önceliklidir.
- `PhotoAccessManager.kt`: uygulama singleton'ı, OS grant'lerini her Activity resume ve izin callback'inde tekrar okur. Grant/URI listesi diske yazılmaz. LIMITED erişimin görünen ID seti IO thread'inde tekrar sorgulanır; aynı grant türünde reselection olursa revision değişir. Trashed satırlar fingerprint'e dahil edilir; uygulamanın kendi trash işlemi izin seçimi değişikliği sayılmaz. Başarısız sorgu güvenli hata verir.
- İzin callback'i LIMITED seçimi açıkça değiştirmiş olabilecekse revision yenilenir. FULL erişimde yeniden request hemen grant dönebileceğinden yönetim düğmesi cihaz izin ayarlarına gider. LIMITED düğmesi sistem reselection akışını yeniden açar. DENIED ilk/rationale durumunda runtime request; tekrar sorulamayan durumda cihaz ayarlarına erişim vardır. Saklanan `requested` bayrağı bir grant değildir; backup dışındadır.
- `MainActivity.kt`: başlangıç LIMITED ise Dashboard'a girebilir; izin durumu başlangıç rotasının tek remembered Boolean'ına bırakılmaz. Uygulama genelindeki `PhotoAccessBanner` scope'u gösterir; LIMITED için tüm galeri tarandı iddiası yoktur. DENIED başlangıç/onboarding ekranında da erişim/ayar eylemi görünür. `OnboardingScreen` navigation'ı gerçek OS durumuna göre yapar.
- `MediaStoreScanner.kt`: erişim yokken query çalıştırmaz, SecurityException verir. Null cursor artık boş/temiz galeri veya sıfır sayısı değildir. Önceki MIME/min-size/folder filtreleri korunur; FULL izin, her formatın veya her boyutun analiz edildiği anlamına gelmez.
- `DashboardViewModel`: resume/izin snapshot'ı yenilendiğinde erişilebilen sayıyı tekrar yükler; önceki load iptal edilir. Hata eski sayıyı ekranda tutmaz. Ana sayaç metni erişilebilen fotoğraf sayısı olarak etiketlendi. Tarama eylemi DENIED veya erişim doğrulama hatasında önce izin yönetimine yönlendirir.
- `ScanViewModel`: erişim revision'ı tarama başlangıcında tutulur. Tarama sırasında değişirse job iptal edilir, eski gruplar temizlenir, tekrar tarama istenir. Eski job'ın cancellation sonucu yeni taramanın/izin hatasının üstüne yazamaz. Başarısız veya iptal edilmiş tarama eski holder gruplarını başarılı sonuç diye taşımaz. Hatalı/boş sonuç metni galeri temizliği garantisi vermez.
- `ResultsViewModel`: erişim değişince gruplar, seçim ve henüz işlem başlamamış app confirmation geçersizleşir. Sistem onayı açıksa frozen transaction korunur; callback'in gerçek URI sonucu uzlaştırılır, sonraki bölüm başlatılmaz, eski sonuçlar ardından temizlenir. Aynı scope/seçimle normal resume/rotation kullanıcının seçimini bozmaz. Grup detay ekranı da sonuç state'ini izler.
- `ScanResultHolder`: gruplar Compose state olur; gallery/history gibi holder kullanan mevcut ekranlar erişim geçersizleştirmesini görür. Holder'a scope/revision/error eklenmesi küçük geçiş korumasıdır; kalıcı sonuç deposu veya süreç ölümü recovery değildir.

Android'in kısmi erişim ve resume sonrası grant/MediaStore yenileme beklentisi: [Selected Photos Access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access). Sınırlı kullanıcı erişiminde cached Room URI/hash kayıtları fiziksel olarak silinmez; tarama yalnız güncel scanner listesini kullanır. Cache retention ve identity/observer iyileştirmeleri Faz 3–4'te hâlâ gereklidir.

## Silme bölümleri ve Faz 1 güvenliği

API 30+ hâlâ yalnız `MediaStore.createTrashRequest(..., true)` ve gerçek sistem onayı kullanır. Uygulama kendi trash/30 gün deposu oluşturmaz; unavailable durumda kalıcı silme fallback'i yoktur. API 26–29 açık app confirmation ve gerektiği yerde WRITE/per-item recovery kontratı korunur.

`RemovalBatchPolicy.MAX_TRASH_URIS = 2000`; platform sınırı target 36+ için geçerlidir. `PhotoRepositoryImpl` tüm frozen URI listesini doğrular, duplicate URI'leri çıkarır, yalnız ilk 2.000'i sistem isteğine koyar; `RequiresPermission.trashUris` ve `remainingTrashUris` açıkça ayrıdır. Legacy `retryUris` anlamı değişmez. `MediaRemovalPlatform` da büyük/boş isteği OS çağrısından önce reddeder. Kaynak: [MediaStore createTrashRequest](https://developer.android.com/reference/android/provider/MediaStore#createTrashRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E,%20boolean)).

`ResultsViewModel` yalnız onaylanan bölümün URI'lerini doğrular. Başarılı bölümden sonra kalan immutable liste için yeni sistem onayı oluşturur. Bir bölüm iptal/başarısız olursa önceki doğrulanan işlemler korunur, sonraki bölümler yapılmaz. Doğrulanamayan bir bölüm de kuyruğu durdurur. Başarı sayısı tüm transaction'daki doğrulanan URI sayısıdır; trash dosya boyutu boşalan alan sayacına eklenmez. Keeper hiçbir bölüme dahil edilmez.

App confirmation ve devam ekranı bölüm sayısını/ilerlemesini belirtir; iptal önceden tamamlanmış bölümleri geri almaz. Geri yükleme için sistem veya galeri uygulamasına yönlendiren önceki metin korunur. API 26–28 WRITE reddedilirse gerekçe ve izin ayarlarını açma eylemi sunulur; storage yazma izni sistem trash modu için istenmez.

## Edge-to-edge ve UX sınırı

`enableEdgeToEdge()` korunur. Ana drawer content içinde `WindowInsets.safeDrawing` uygulanıp child scaffold'lara consume edilir; böylece toolbar, eski onboarding/scan ekranları ve alt işlem barı sistem barları/cutout altında kalmaz, nested scaffold aynı inset'i iki kez uygulamaz. Yeni izin alanı 600 dp'den geniş içerikte yatay yerleşir; küçük pencerede alt alta görünür. Edge-to-edge'i opt-out eden flag eklenmedi. Kaynak: [Compose window insets](https://developer.android.com/develop/ui/compose/system/insets).

Bu bir Faz 5 görsel yeniden tasarımı değildir. Landscape snackbar'ın empty-state metnini örtmesi, 200% font, küçük landscape'te mevcut onboarding/scan'ın sabit boyutları, TalkBack ve tablet/foldable akışları açık UX kontrol maddeleridir. Faz 5'te çok sayıda benzer fotoğraf grubunu hızlı inceleme tasarımı; carousel/grid/overview/master-detail alternatifleri, keeper ayrımı, seçim güvenliği ve performans üzerinden önce tasarlanıp açıklanacak, sonra uygulanacaktır.

## Backup / D2D politikası

Auto Backup ve cihazdan cihaza aktarım **allowlist** kullanır: yalnız `file/datastore/photoclarity_settings.preferences_pb`. Bu, `SettingsDataStore`'un `preferencesDataStore(name = "photoclarity_settings")` dosya yoludur; mevcut algoritma/threshold/diğer tercihleri ve aylık silme istatistiğini içerir. Photo URI, fotoğraf baytı, kullanıcı hesabı veya credential bu dosyada yoktur. İstatistik tarihsel işlem kaydıdır; yeni cihazda gerçek anlık boşalan alan garantisi değildir.

Room database/WAL/SHM, image cache, external test evidence, private permission request history ve diğer app dosyaları allowlist dışında kalır. Android 31+ cloud ve D2D bölümleri ayrı ayrı tanımlıdır; eksik D2D bölümünün varsayılan olarak diğer app verilerini taşıması engellenir. Cloud için encryption capability zorunludur. Android 26–30 eski XML yalnız aynı dosyayı dahil eder; bu cihazlarda yeni XML'in encryption bayrağı uygulanmaz. Mevcut dosya/ayarlar silinmedi, veri migration'ı yapılmadı.

Kuralların derlenmesi/yol eşlemesi gerçek backup export+restore kanıtı değildir. Yeni cihaz/temiz installation sonrası gerçek cloud/D2D restore, yeniden izin alma ve URI cache yokluğu manuel kabul kapısıdır. Kaynak: [Android Auto Backup include kuralları](https://developer.android.com/identity/data/autobackup).

## Doğrulama kayıtları

İlk kontrollü build'de 63 JVM testi, 0 failure/error/skipped; debug/test APK ve lint Gradle görevleri tamamlandı. Lint 0 error/38 warning. Wrapper task aynı çalışan `gradlew.bat` dosyasını yenilerken, Gradle başarı çıktıktan sonra çağıran batch `PATH*` komut hatasıyla **9009** döndü; bütün shell kontrolü başarılı sayılmadı. Yenilenmiş wrapper ayrı `--version` çağrısıyla ve son temiz build ile tekrar kontrol edilir. SDK XML v3/v4 tool metadata uyarısı da gizlenmez; AGP mevcut kabul edilmiş lisanslarla SDK 36 revision 2 / Build Tools 35'i kurdu.

Son temiz build `clean testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease lintDebug lintRelease`: **BUILD SUCCESSFUL, 6m 42s**, 151 task. İzin alanının geniş pencere düzeltmesinden sonra tekrar debug/test APK, minified release/AAB, JVM ve her iki lint **BUILD SUCCESSFUL, 5m 43s**. JVM: **63 test / 0 failure / 0 error / 0 skipped**. Debug ve release lint: **0 error / 38 warning**; eski AutoMirrored ikon, schema/tool metadata ve Jetifier uyarıları hâlâ bakım borcudur. Yeni AGP lint motorunun 58→38 uyarı değişimi tüm eski uyarıların düzeltildiği anlamına gelmez. JBR 21 gerçek yerel runtime; JDK 17 workflow referansı, bu değişikliklere ait uzaktaki CI henüz çalışmadı. Yenilenmiş wrapper `--version` exit 0 verdi; normal son build'lerde önceki batch 9009 tekrarlanmadı.

API 37 Android 17/Pixel_4 (`emulator-5554`), x86_64, **16.384 byte sayfa**:

| Koşu | Sonuç | Kanıt sınırı |
|---|---|---|
| Faz 1 gerçek consent/keeper/partial/rotation regresyonu | **12/12**, 0 failure/skip, **33.652 s** | Önceki güvenlik testleri target 36 ile; başlık yeni dürüst tarama metnine uyarlanmıştır |
| Gerçek 2.000+1 trash kuyruğu | **1/1**, 0 failure/skip, **201.533 s** | 2.002 own PNG, 1 keeper; ilk 2.000'e sistem onayı, kalan 1'e ikinci sistem onayında iptal; 2.000 gerçek IS_TRASHED, keeper+kalan aktif, stats 0 |
| FULL, manifest/target, insets, recreation, gerçek DataStore yolu | **1/1**, **4.120 s** | Runtime grant lab ortamında preset; permission ayar düğmesi ve OS grant kontrolü |
| LIMITED etiketi + recreation | **1/1**, **3.916 s** | OS selected grant preset; gerçek insan picker seçimi/URI kapsamı değildir |
| DENIED scanner/count fail-closed + recreation | **1/1**, **3.552 s** | Null/boş başarılı galeri yerine SecurityException; erişim yok etiketi |
| Trashed dahil sentetik batch cleanup audit | **1/1**, **2.416 s** | Yalnız app-owner/test-prefix query, MATCH_INCLUDE; aktif veya trashed artık 0 satır |

Toplam **17 farklı cihaz testi başarılı** (12 regresyon + 5 Faz 2 kontrolü); ayrı instrumentation koşularıdır. Büyük batch testi küçük bir consent host'unda gerçek repository/ResultsViewModel/ActivityResult/System MediaProvider kullanır. 2.000 kart içeren production ResultsScreen'i, hash analyzer'ı veya 20.000 galeri performansını test etmez; mevcut büyük sonuç UI borcu Faz 4–5'te kalır.

İlk Faz 1 regresyonunda landscape'te yeni iki satırlı izin alanı, bilinen snackbar örtüşmesini genişletip boş sonuç başlığını gizledi: **12 test / 1 başarısızlık**. Gerçek işlem/keeper doğrulaması geçti, UI başlık assertion'ı kaldı. Yeni izin kontrolü geniş pencerede tek satıra alındı; assertion kaldırılmadan aynı test geçti. Subtitle/snackbar UX bulgusu açık kalır. İlk üç yeni cihaz check'i JUnit class initialization sırasında **denied test'in void olmayan dönüş tipi** yüzünden başlamadı. İmza `Unit` yapıldı; SDK 36 nullable requestedPermissions listesi de testte `orEmpty()` ile ele alındı. Bu test harness/derleme denemeleri uygulama test başarısı diye sayılmadı; düzeltilmiş APK ile yukarıdaki gerçek koşular tekrar çalıştı.

Paket doğrulaması:

- Debug APK, unsigned minified release APK ve AAB'nin her birinde 4 ABI × 2 native `.so` = 8 kütüphane; bütün ELF PT_LOAD hizalamaları **16.384**. `zipalign -c -P 16` debug/release ve emülatöre uygun 4 split üzerinde başarılı. BundleConfig `PAGE_ALIGNMENT_16K`, uncompressed native enabled. [16 KB gereksiniminin yöntemleri](https://developer.android.com/guide/practices/page-sizes).
- Resmî [Google bundletool 1.18.3](https://github.com/google/bundletool/releases/tag/1.18.3) asset digest'iyle doğrulandı; JAR SHA-256 `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`. AAB validate geçti; x86_64/base/en/xxhdpi split'leri üretildi.
- Minified split uygulaması mevcut, standart yerel **debug** key ile test için imzalandı; önceden bulunmayan `com.photoclarity.ai` paketine kuruldu, cold launch başarılı, process canlı ve Activity **RESUMED**. Target 36, iki photo grant false. Fotoğraf erişimi verilmedi; boş test release paketi sonunda kaldırıldı. Release AAB hâlâ **unsigned**; bu production signing/Play upload kanıtı değildir. İlk smoke helper yalnız eski `mResumedActivity` adını aradığı için yanlış kontrol hatası verdi; Android 17'nin resumed alanı kaydedilip kontrol edildi, uygulama kaynaklarında smoke için değişiklik yapılmadı.
- Backup XML allowlist kontrolleri geçti; Jetpack'in gerçek `preferencesDataStoreFile` yolu FULL cihaz testinde aynı canonical path'i verdi. Cloud export/restore ve D2D uygulanmadı.
- Son runner sonrası READ_MEDIA_IMAGES ve selected grant'leri başlangıçtaki **false/false** durumuna döndü. Batch cleanup test logcat'i **2.002 fixture cleanup** bildirir; test klasörü dosya kontrolü boş. Shell'e ait dış fixture belirli URI/display-name/owner ile kaldırıldı. Normal query trash'i gizlediği için ek Android query testi **MATCH_INCLUDE ile 0 own sentetik satır** doğruladı; bu, galeri geneli silme değildir. CLI `content --extra` key içindeki `:` karakterini parse edemediğinden bu kontrol gerçek ContentResolver Bundle ile yapıldı; başarısız CLI kontrolü temizliğin kanıtı sayılmadı. Gözlenen boş test batch klasörü de kaldırıldı.

Son kaynak/test hâli için `testDebugUnitTest assembleDebug assembleDebugAndroidTest bundleRelease lintDebug lintRelease`: **BUILD SUCCESSFUL, 57 s**, 144 task (10 executed/134 up-to-date). Mevcut geçerli 63 JVM XML sonucu yeniden denetlendi; bu son komutta Unit task up-to-date idi. Yeni cleanup cihaz testi derlendi ve 2.416 saniyede ayrıca çalıştırıldı. Değişmeyen 12+4 davranış testleri sırf tekrar etmek için yeniden koşulmadı. Bu son kontrolün log'u `final-state-check.log` içindedir.

`actionlint 1.7.12` yeni SDK/checksum/test-APK/release-lint workflow'u için geçti. Güncel CI en az 63 JVM testi ister; henüz push veya bu çalışma ağacına ait Actions sonucu yoktur. Son Git audit: **40 değişen/yeni dosya**, yalnız kaynak/test/resource/Gradle/workflow/Markdown ve doğrulanmış wrapper JAR; sentetik medya, APK/AAB, heap dump, local/IDE/emulator dosyası ve bilinen secret pattern'i yok; index boş ve HEAD başlangıç commit'inde. Ana roadmap ve Faz 1 raporunun tarihsel içeriği Git HEAD'e karşı prefix kontrolüyle korunmuş bulundu. Genel pattern taraması matematiksel secret yokluğu garantisi değildir.

Yerel ham kanıtlar `.baseline/phase2/` altında ignore edilir: `build-final.log`, `build-verified.log`, `final-state-check.log`, `phase1-regression.log`, `phase2-batch.log`, `phase2-full.log`, `phase2-limited.log`, `phase2-denied.log`, `cleanup-inclusive-device.log`, `packaging-final.log`, split/startup/permission/cleanup kayıtları. Medya, JAR/APK araç çıktısı, dump ve cihaz log'u version control'e eklenmez. Wrapper JAR build tabanının kasıtlı versionable tek binary dosyasıdır.

## Kabul kapısı ve açık maddeler

| Kriter | Mevcut sınır |
|---|---|
| Güncel compile/target, desteklenen toolchain, gerçek JVM test | PASS yerel JBR 21, 63 test; JDK 17 uzak workflow koşusu yayın sonrası |
| FULL/LIMITED/DENIED ve sürüme uygun permission seçimi | JVM karar matrisi + API 37 üç gerçek grant dalı PASS; eski OS/OEM açık |
| LIMITED etiketi, denied/null query fail-closed | API 37 LIMITED/denied PASS; null/OEM query failure geniş matrisi açık |
| Reselection/revoke/resume ve selection güvenliği | JVM değişim/lock testleri ve normal recreation PASS; gerçek picker reselection/permanent deny/app settings revoke/OEM açık |
| 2.000 sınırı ve çoklu sistem onayı | Repository 2.001 + VM/cancel/partial PASS; gerçek API 37 target-36 2.000+1 sistem onayı PASS; API 36 özellikle ve OEM açık |
| API 26/28/29/30/32/33/34/35/36 OS matrisi | Açık; SDK sürümünü JVM policy testine vermek gerçek cihaz kabulü değildir |
| Edge-to-edge, native 16 KB ve release splits | ELF/ZIP/AAB/x86_64 split startup/portrait inset PASS; diğer OS/ABI/cutout/gesture/large-screen/200% font matrisi açık |
| Backup allowlist ve DataStore yolu | XML + Jetpack gerçek canonical yol PASS; gerçek export/restore/D2D açık |
| GitHub Actions | Push yapılmadığı için bu çalışma ağacına ait CI run yok; actionlint ayrı kontrol |
| Faz 1 açık kabul maddeleri | API 26–30, galeri UI restore, OEM/izin/provider ve landscape snackbar UX aynen açık |

Kalan Faz 2 acceptance açıkken bu çalışma production-ready veya bütün matrisi geçen release olarak sunulamaz. Faz 3'e otomatik geçiş yapılmaz. Büyük gallery performans borcu ve süreç ölümü transaction recovery ana roadmap'te korunur.

## Checkpoint yayın kontrolü — 2026-10-04

Kullanıcı Faz 2 değişikliklerini tek commit ile `main` branch'ine push etmeyi yetkilendirdi. Yukarıdaki commit/push/CI yapılmadı ifadeleri geliştirme oturumunun tarihsel kaydıdır. Commit mesajı: `Establish PhotoClarityAI Phase 2 platform checkpoint`. Bu yayın sırasında uygulama davranışına veya Faz 3 kapsamına ek değişiklik yapılmaz.

Yenilenen Git kontrolünde 40 kaynak/test/resource/Gradle/workflow/Markdown dosyası ve bunlar içindeki tek binary olan checksum doğrulanmış Gradle Wrapper JAR'ı incelendi. Sentetik test medyası, build/test çıktısı, heap dump, IDE/emulator/local dosyası, signing materyali ve kullanıcı verisi commit adaylarında yoktur; bilinen secret/credential örüntüsü bulunmadı. Test kaynakları medyayı bellekte üretir; medya baytları veya cihaz kayıtları eklenmez. Staged blob kontrolü, push sonrası local HEAD / origin/main / GitHub main eşleşmesi ve ilgili Actions sonucu yayın işleminin ayrıca doğrulanacak kapılarıdır; sonuçlar yayın oturumunda bildirilir. CI başarısı cihaz kabulünün yerine geçmez.

**Genel kabul PARTIAL olarak korunur:** API 26–36/OEM matrisi, gerçek picker/reselection/revoke/permanent-deny, özellikle API 36 batch onayı, galeri üzerinden restore, gerçek cloud/D2D restore ve landscape snackbar UX maddeleri açık kalır. Faz 0–7 sırası, Faz 5 implementasyon öncesi UX tasarım ve alternatif değerlendirme gereksinimi değişmez. Bu checkpoint Faz 3'e geçiş değildir.
