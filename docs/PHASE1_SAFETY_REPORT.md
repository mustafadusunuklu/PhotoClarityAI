# Faz 1 — Veri kaybı bariyeri ve doğru sonuç

Tarih: 2026-10-03. Başlangıç revision: `479c75d017291a7cfc303822d03ec568aa005fa9` (Faz 0 baseline). Bu rapor çalışma ağacındaki Faz 1 değişikliklerini açıklar; başlangıç analizinin yerine geçmez. Ana roadmap'in Faz 0–7 sırası ve Faz 5 UX/UI gereksinimleri korunmuştur.

## Kullanıcının onayladığı silme kontratı

- Android 11+ (API 30+): yalnızca `MediaStore.createTrashRequest(..., true)` ile sistem çöp kutusuna taşıma. Uygulama onayını ayrıca sistem onayı izler. Sistem isteği oluşturulamazsa kalıcı silmeye geri düşülmez.
- PhotoClarityAI kendi çöp kutusunu, 30 günlük saklama sistemini veya geri yükleme özelliğini oluşturmaz. Geri yükleme, süre ve kalıcı silme sistem veya galeri uygulamasının sorumluluğundadır; uygulama süre/geri yükleme garantisi vermez.
- API 26–29: kalıcı silme açıkça yazılır; geri alınamazlık bilgisi ve ayrı uygulama onayı zorunludur. API 26–28'de eksik `WRITE_EXTERNAL_STORAGE` izni işlem öncesinde istenir; ret işlemi durdurur. API 29 `RecoverableSecurityException` için tekil sistem onayı sonrası yalnızca dondurulmuş kalan URI listesi tekrar işlenir.
- Çöp kutusuna taşınan dosyalar henüz cihazda bulunduğundan temizlenen/boşaltılan alan istatistiğine eklenmez. Kalıcı silmede yalnızca gerçekten silinen fotoğrafların taramadaki boyutu kaydedilir; bu boyut fiziksel disk boşluğunun kesin ölçümü değildir.

Android davranışı [MediaStore API referansı](https://developer.android.com/reference/android/provider/MediaStore#createTrashRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E,%20boolean)) ve [paylaşılan medya silme rehberi](https://developer.android.com/training/data-storage/shared/media#remove-item) ile karşılaştırıldı. API 29 izin verir; onay sonrası uygulamanın işlemi tekrar yapması gerekir. API 30 trash isteği işlemi sistemde gerçekleştirir; callback sonrasında uygulama doğrudan kalıcı delete çağırmaz.

## Değişikliklerin kodla eşleştirilmesi

| Risk / konu | Yapılan değişiklik | Kod |
|---|---|---|
| R01 yanlış geri alınabilirlik vaadi | Gerçek sürüme göre onay metni; bilgi ekranından 30 gün ve uygulama içi geri yükleme vaadi çıkarıldı. Drawer başlığı sistem bilgisi olduğunu belirtir. | `ui/components/DeleteConfirmDialog.kt`, `ui/trash/TrashScreen.kt`, `ui/dashboard/DrawerContent.kt` |
| Silme sonucu | `DeleteResult.Success` artık sayı yerine başarılı/başarısız URI kümeleri taşır. `ContentResolver.delete` sıfır satır döndürdüğünde başarılı kabul edilmez; URI bazlı hata korunur. Koleksiyon URI'si reddedilir. | `domain/repository/PhotoRepository.kt`, `data/repository/PhotoRepositoryImpl.kt` |
| Sistem onayı / doğrulama | API 30+ trash isteği; onay sonrası `QUERY_ARG_MATCH_TRASHED=MATCH_INCLUDE` ile `IS_TRASHED` kontrolü. Erişilemeyen, null cursor, bulunamayan veya değişmemiş satır başarı değildir. | `core/media/MediaRemovalPlatform.kt`, `data/repository/PhotoRepositoryImpl.kt`, `di/AppModule.kt` |
| R13 callback seçimi / tekrar basma | Uygulama onay ekranı açılırken seçim fotoğraf listesi olarak dondurulur. Seçim, yeni istek ve toplu seçim işlem boyunca kilitlidir. İptal, launch hatası ve tekrarlanan callback güvenli biçimde ele alınır. API 29 tekrar izni döngüsüne sınır konur. | `ui/results/ResultsViewModel.kt`, `ui/results/ResultsScreen.kt` |
| Keeper / waste | Keeper elle veya topluca silme için seçilemez. Keeper olmayan/missing-keeper grupta adaylık kapalıdır. Başarılı işlem sonrasında üyeler, keeper ve kalan waste yeniden uzlaştırılır. Burst waste gerçek keeper hariç hesaplanır. | `core/analysis/PhotoAnalyzer.kt`, `ui/results/ResultsViewModel.kt`, `ui/components/PhotoGroupCard.kt` |
| R02 ilgisiz düşük kalite grubu | Her düşük kalite bulgusu tekil inceleme önerisidir: benzerlik puanı ve waste sıfır, silme/toplu seçim kapalı. İlgisiz fotoğraflar birbirinin kopyası gibi gruplanmaz. | `core/analysis/PhotoAnalyzer.kt`, `ui/components/PhotoGroupCard.kt`, `ui/results/GroupDetailScreen.kt` |
| R03 null stream / başarısız decode | Null veya boş stream null hash verir; kriptografik okuma iptali yutulmaz. Decode/netlik ölçümü başarısızsa netlik `-1` olur ve fotoğraf analiz gruplarına alınmaz. Eski boş-digest cache değerleri geçersiz sayılır. | `core/hash/CryptographicHasher.kt`, `core/util/BitmapUtils.kt`, `core/analysis/PhotoAnalyzer.kt`, `core/analysis/QualityScorer.kt` |
| R04 cache completeness / retry | Geçerli cache'de sadece o tarama için eksik hash hesaplanır; diğer hash'ler korunur. Null hesaplama yeniden denenir. Eski netlik 0 değerleri failure ile karıştığından yeniden ölçülür. | `core/analysis/PhotoAnalyzer.kt` |
| R05 algo kombinasyonları | Tek tercih alanı korunur; efektif görsel algoritma ayrı türetilir. Crypto tercihi görsel aşamayı sessizce kapatmaz. a/dHash grup puanı gerçek Hamming benzerliğidir. | `domain/model/ScanSettings.kt`, `core/analysis/PhotoAnalyzer.kt`, `ui/settings/SettingsScreen.kt` |
| R07 bitmap sahipliği | Scale source bitmap'in kendisini döndürse de okuma tamamlanmadan recycle edilmez. Kaynak ve ayrı scaled bitmap hata yollarında da serbest bırakılır. pHash/decoder cancellation yutulmaz. | `core/hash/AverageHasher.kt`, `core/hash/DifferenceHasher.kt`, `core/hash/PerceptualHasher.kt`, `core/util/BitmapUtils.kt` |
| No-op ayarlar | Aynı klasör, metadata, GPS ve otomatik akıllı seçim anahtarları uygulanmış özellik gibi gösterilmez. Saklanan tercih alanları migration yapılmadan korunur. Manuel toplu düğme gerçek silme seçimi anlamını açıklar. | `ui/settings/SettingsScreen.kt`, `ui/results/ResultsScreen.kt` |

### Algoritma matrisi

| Saklanan tercih | Birebir kontrol açıkken | Görsel kontrol açıkken |
|---|---|---|
| pHash | MD5 | pHash |
| aHash | MD5 | aHash |
| dHash | MD5 | dHash |
| MD5 | MD5 | pHash |
| SHA-256 | SHA-256 | pHash |

İki aşamanın aç/kapa ayarları bağımsızdır. Bu aşamada yeni DataStore/Room şeması veya tercih migration'ı yoktur. Düşük kalite önerileri silme için seçilemez; amaç bulanıklık eşiğini kopya eşleşmesi gibi kullanmamaktır. Aynı/burst/benzer gruplardaki kalite skoru estetik değer veya korunması gereken içeriğin kesin ölçüsü değildir; kullanıcı keeper dışındaki adayları da işlemden önce incelemelidir.

## Doğrulama ve kalan sınırlar

Otomatik doğrulama sonucu bu raporun sonundaki kayıtla birlikte okunmalıdır. Testler gerçek galeriye erişmez ve kullanıcı fotoğraflarında silme/taşıma yapmaz.

- Faz 0'daki bilinen bug karakterizasyonları, düzeltilen davranışlar için güvenlik regresyonlarına dönüştürüldü. Eski beklentiler başlangıç Git revision'ında durur.
- Cache testleri gerçek analyzer ve fake DAO ile; deterministik hasher/decoder sınırlarıyla algoritma geçişlerini, eksik hash retry ve decode failure ayrımını sınar. Gerçek Android görüntü decoder doğrulaması değildir.
- Results testleri frozen snapshot, keeper/missing-keeper koruması, app confirmation, reentry, 0/partial delete, cancel, API 29 retry, callback tekrarları, API 30 doğrulama ve byte istatistiğini sınar.
- Repository testleri mock resolver/platform üzerinden satır sayısı, URI listeleri, trash hatasında fallback olmaması ve doğrulama başarısızlıklarını sınar.
- Bitmap sahiplik testleri Android static/bitmap mock'larıyla source=scaled ve hata yollarında okuma/recycle sırasını sınar. Gerçek Android codec/bitmap smoke testinin yerine geçmez.

**Faz 1 cihaz kabul kapısı henüz kapanmadı.** Aşağıdaki liste ilk cihazsız doğrulamanın açık maddeleridir; API 37 oturumunda kapanan alt senaryolar ve kalan sınırlar en sondaki cihaz kabul kaydında ayrılır. API 26–30 ve modern Android üzerinde yalnızca sentetik, yedekli test fotoğraflarıyla kontroller gerekir:

1. API 26–28: kalıcı silme uyarısı, write izin verme/ret, onay/iptal, 0 satır ve başka uygulama tarafından kaldırılmış dosya.
2. API 29: birkaç farklı sahibin fotoğrafıyla tekil RecoverableSecurityException onay/ret; erken başarıdan sonraki iptalde kısmi sonuç ve alan istatistiği.
3. API 30+: app onay + sistem trash onayı, cancel, gerçek `IS_TRASHED`, galeri/sistem çöp kutusunda görünme ve oradan geri yükleme. PhotoClarityAI içinde restore veya 30 günlük garanti beklenmez.
4. Provider/OEM query kısıtı: doğrulanamayan URI silindi kabul edilmemeli; hata görünür olmalı; yeniden tarama ile durum uzlaştırılmalı.
5. Sistem diyalogu açıkken rotation; geri/yeniden giriş; aynı callback tekrarları. Process death kalıcı işlem günlüğü/saved-state desteği Faz 3'te ele alınacaktır; bu sürüm kayıp işlemi tahmin ederek tekrar silmez.
6. p→a/d/SHA, exact/visual off→on, erişimi kaldırılan dosya, corrupt/null decode, source=scaled bitmap, gerçek küçük ve büyük fotoğraflar.

### Bilinen ve sonraki fazlarda ele alınacak noktalar

- Hedef SDK/Android 14 seçili fotoğraf izin modeli, batch limitleri ve izin kapsamı Faz 2; background scan/persistent state/process death ve invalidation Faz 3 kapsamındadır.
- Eşik kalibrasyonu, transitive görsel gruplar ve metadata tabanlı burst yanlış pozitifleri Faz 4'te devam eder. Faz 1 eşleşme doğruluğunun tamamını veya otomatik silmenin güvenli olduğunu iddia etmez.
- `-1` netlik failure sentinel'i mevcut Float şeması içinde tutulur. Eski 0 yeniden ölçülür; gerçekten düz görüntülerde netlik 0 olduğundan yeniden ölçüm maliyeti sürer. Ayrı typed analysis status ve cache algoritma sürümü Faz 3–4'te ele alınmalıdır.
- Hash/decode başarısız fotoğraflar adaylardan dışlanır; bu sürüm henüz UI'da toplam başarısız analiz sayısını ayrı bir partial-scan durumu olarak sunmaz. Boş sonuç, bütün galerinin başarıyla analiz edildiğinin kanıtı değildir; typed scan durumları Faz 3'te gereklidir.
- Bazı OEM/provider'lar trashed medyayı uygulamaya sorgulatmayabilir. Bu durumda işlem sistemde gerçekleşmiş olsa bile UI doğrulanamadı der; güvenli başarısızlık tercihi. Gerçek cihaz davranışı test edilmeli.
- Sonuç ekranında yalnızca Faz 1 güvenliği için metin, seçim/checkbox, feedback ve onay akışı değişti. Gruplar arası gezinme, adaptive/accessible UX ve comprehensive redesign Faz 5'teki gereksinimle aynen korunuyor.
- Release bundle hâlâ baseline signing/SDK durumu taşır; production veya Play Store readiness anlamına gelmez.
- Bu görevde commit, push, CI yayınlama veya sonraki faz başlatma yapılmadı.

## Çalıştırma kaydı

İlk doğrulama oturumu: JDK Android Studio JBR 21.0.10; bytecode JVM 17. Gradle 8.7 / AGP 8.5.2, compile/target SDK 34, min SDK 26 değişmedi. O sırada cihaz listesi boştu (`adb devices -l`); cihaz smoke/codec/rotation/restore testleri çalıştırılmadı. Sonraki API 37 oturumu aşağıda ayrıca kayıtlıdır.

Nihai komut:

```powershell
.\gradlew.bat --no-daemon --max-workers=2 testDebugUnitTest assembleDebug bundleRelease lintDebug
```

Sonuç: **BUILD SUCCESSFUL**, 5 dakika 32 saniye; 108 görev (36 executed, 72 up-to-date).

| Kontrol | Sonuç |
|---|---|
| `testDebugUnitTest` | **53 test, 0 failure, 0 error, 0 skipped** |
| `assembleDebug` | Başarılı; APK üretildi |
| `bundleRelease` | Başarılı; AAB üretildi; production signing yapılandırılmadı |
| `lintDebug` | **0 hata, 58 uyarı**; uyarılar sonraki fazlara taşınır, bastırılmadı |
| `git diff --check` | Başarılı |
| Eski ana analiz ve Faz 0–7 | Önceki belgenin içeriği prefix olarak aynen korunuyor; yalnızca bölüm 22 eklendi |
| SDK / Gradle / manifest / resources | Değişmedi |
| Secret pattern taraması | İncelenen kaynak/dokümanlarda yeni token/private-key örüntüsü bulunmadı; genel tarama mutlak secret yokluğu kanıtı değildir |
| Git | HEAD başlangıç revision'ında; index boş, yeni commit/push yok |
| GitHub Actions | Faz 1 çalışma ağacı yayınlanmadığı için bu değişikliklere ait CI sonucu yok; Faz 0 CI başarısı ayrı ve tarihsel kayıttır |
| Gerçek cihaz smoke testleri | İlk oturumda yapılmadı; sonraki API 37 sonuçları aşağıda. Faz 1 genel kabul kapısı açık. |

Test dağılımı: cache/analyzer 13; kriptografik hash 8; Results state 21; bitmap ownership 3; removal repository 6; fixture integrity 2. Runtime/provider/UI testleri yerine geçtiği iddia edilmez.

Yerel, ignore edilen kanıtlar: `.baseline/phase1-final-check.log`, `app/build/test-results/testDebugUnitTest`, `app/build/reports/lint-results-debug.*`, `.baseline/phase1-validation.json`. APK/AAB/log/rapor çıktıları Git'e dahil edilmedi. Nihai doğrulama öncesindeki JUnit dönüş tipi, DI bağlantısı, eşzamanlı fake DAO ve lint API kontrolü hataları düzeltildi; başarısız koşular başarılı diye raporlanmadı.

## API 37 cihaz kabul oturumu — 2026-10-03

Kullanıcının açtığı `Pixel_4` AVD, `emulator-5554`: SDK **37**, Android **17**, codename REL, x86_64, 1080×2280; `getconf PAGE_SIZE` **16384**. Fingerprint: `google/sdk_gphone16k_x86_64/emu64xa16k:17/CP21.260330.005/15181570:user/dev-keys`. Bu oturum modern Android debug runtime kanıtıdır; API 26–30 veya signed release/split/Play doğrulaması yerine geçmez.

### Test yöntemi ve veri güvenliği

Yeni test kaynağı: `app/src/androidTest/java/com/photoclarity/ai/safety/Phase1DeviceAcceptanceTest.kt`; çalıştırma sınırları `app/src/androidTest/README.md` içinde. İlk cihaz oturumunda test APK'sındaki PNG, Faz 0 CC0 fixture'ının birebir kopyasıydı: 64×64, 12.420 bayt, EXIF/GPS/kişi içeriği yok. Yayın kontrolünde bu yeni medya kopyası commit dışında bırakıldı; aynı baytlar `Phase1SyntheticMedia.kt` ile bellekte üretilir. Provenance ve SHA-256 `app/src/androidTest/assets/phase1/README.md` içinde; her setup boyut/hash kontrol eder.

Her test benzersiz `Pictures/PhotoClarityAI_Phase1_<UUID>/` altında dört sentetik MediaStore satırı oluşturur. Temizlik yalnız kaydedilen tekil URI ve o koşunun doğrulanmış display-name prefix'i ile yapılır; geniş galeri silme sorgusu kullanılmaz. Ayrıca tek bir CC0 dosya shell ile benzersiz harici klasöre kondu, dosya bazlı media scan yapıldı; gerçek `owner_package_name=com.android.shell` doğrulandı. Böylece uygulamaya ait olmayan medyanın onay yolu da sınandı. Kullanıcı fotoğrafları seçim/silme/taşıma testine alınmadı.

Sonuç güvenlik testleri gerçek Compose `ResultsScreen`, gerçek `ResultsViewModel`/repository/platform, ActivityResult ve MediaStore kullanır; gruplar kontrollü fixture listesiyle kurulur, alan istatistiği fake'tir. Kısmi sonuç tesliminde tek sınır repository sonucunun enjeksiyonudur: gerçek provider'da bir satır trashed, bir satır değişmemiş durumdadır; ikisi gerçek `verifyTrashedPhotos` ile sorgulanır ve yalnız o talebin URI'leri VM'ye teslim edilir. **Doğal bir sistem batch'inin kısmi başarısızlığı üretildiği iddia edilmez.**

Bu kontrollü testten **ayrı olarak**, son ek test sistem onayı açıkken yalnız bir test-owned URI'yi dış galeri değişikliği gibi kaldırır. Gerçek MediaProvider kalan iki URI'yi trash'e taşıyıp gerçek callback döndürür; repository/VM sonucu fake değildir. API 37'de bu deterministik yarışla **gerçek kısmi sistem batch sonucu** elde edildi. Diğer OEM/provider hata modellerine genellenmez.

Analyzer/navigasyon testi gerçek Android hash/decoder, `PhotoAnalyzer`, in-memory Room DAO ile yalnız dört test fotoğrafını analiz eder; sonucu normal MainActivity → Dashboard → Kopyalar → NavHost/Hilt Results akışına verir. Kullanıcının bütün galerisi taranmaz; DataStore scan tercihleri değiştirilmez. Activity yeniden oluşturma ve sistem diyaloğu açıkken portrait→landscape dönüşü gerçek navigasyon/ViewModel sahipliğiyle test edilir. Bu, unrestricted Hızlı Tarama'nın baştan sona veya process death'in test edildiği anlamına gelmez.

### Ortam ve test harness sorunları

1. İlk `connectedDebugAndroidTest` koşusu **0 test** ile süreç çökmesi raporladı. `dumpsys activity exit-info`, uygulama sürecinin nedenini **LOW_MEMORY** olarak kaydetti; Java uygulama exception'ı bulunduğu iddia edilmedi. AVD başlangıçta yaklaşık 2 GB RAM ve dolu swap kullanıyordu. Aynı AVD `-memory 4096 -no-snapshot-load -no-snapshot-save` ile bu oturum için cold boot edildi. `-wipe-data` kullanılmadı, AVD kalıcı config'i veya proje SDK ayarları değiştirilmedi. Yeni MemTotal yaklaşık 4 GB idi.
2. İlk başarısız UTP koşusu hedef debug uygulama ve test paketini otomatik kaldırdı; bu işlem hedef uygulamanın **özel verilerini/ayarlarını kaldırmış olabilir**. Galeri medyasının silindiği iddia edilmez. APK'lar yeniden kuruldu; bundan sonraki koşular doğrudan adb instrumentation ile yapıldı. Mevcut özel app state'in ilk UTP koşusu boyunca korunduğu garantisi verilemez.
3. Compose/Espresso kuralıyla API 37 koşusunda **8/8 test harness hatası** oluştu: Espresso 3.6.1, kaldırılmış gizli `android.hardware.input.InputManager.getInstance()` metodunu aradı. Test gövdeleri başlamadan hata verdi. Dependency yükseltilmedi; test kuralı ActivityScenario ve UiAutomation'a çevrildi. Bu hata production uygulama exception'ı olarak sınıflandırılmadı.
4. İlk geniş koşuda **11 test / 1 hata**, teşhis koşusunda **2 test / 1 hata**: dönüş testi, landscape snackbar altında kalan empty-state alt metnini erişilebilir ağaçta bulamadı. Screenshot ve ağaçta **boş sonuç başlığı ve doğru 3-fotoğraf trash başarı mesajı** görüldü; MediaStore ve keeper assertion'ları geçmişti. Güvenlik assertion'ı görünür boş sonuç başlığı + gerçek provider sonucu + güncellenmiş ScanResultHolder + farklı Activity instance kontrolüne düzeltildi. UI örtüşmesi gizlenmedi; aşağıdaki Faz 5 bulgusu olarak korundu.

### Cihaz kabul matrisi

| Senaryo / test | Doğrulanan davranış | Sınır |
|---|---|---|
| `keeperAndAppCancelNeverMutateMedia` | Keeper UI/state koruması; toplu seçim keeper hariç; uygulama onayı iptalinde dört fotoğraf aktif | Kontrollü exact group |
| `manualSelectionCanBeReversedWithoutADeleteRequest` | Gerçek fotoğraf kartına dokunarak tekil seçim ve seçimi kaldırma; sistem isteği yok, medya değişmiyor | Küçük grup |
| `lowQualityAndMissingKeeperHaveNoDeletionCandidates` | Low-quality ve bulunmayan keeper için toplu/elle adaylık yok | Result fixture/state guard |
| `systemCancelPreservesSelectionAndPhotos` | Gerçek MediaProvider Deny; grup ve seçim korunur, pending temizlenir, hata görünür state'te; hiçbir fotoğraf trashed değil | API 37 |
| `systemApprovalTrashesOnlyFrozenNonKeepersAndCanRestoreThroughSystem` | Gerçek Allow; seçim/reentry kilidi; yalnız dondurulmuş üç non-keeper trashed; keeper aktif; normal scanner yalnız keeper'ı görür; freed-byte kaydı yok | Geri getirme IS_TRASHED=0 ile provider seviyesinde; galeri restore UI değil |
| `nonOwnedPhotoRequiresSystemConsentAndIsVerifiedAfterTrash` | Shell'e ait gerçek satır için uygulama + sistem onayı; yalnız o URI trashed; keeper korunur; callback sonrası sonuç sayısı 1 ve error null | Tam READ_MEDIA_IMAGES izni; tek dış sahip |
| `missingUriVerificationAndInvalidCollectionFailClosed` | Koleksiyon URI'si işlem hatası; bulunmayan URI/değişmemiş satır başarılı sayılmaz; dört fotoğraf aktif | API 37 query/delete-request sınırı; legacy ContentResolver.delete 0 satır cihaz testi değil |
| `mixedTrashedAndUnchangedRowsAreReconciledByUri` | Gerçek mixed provider durumunu URI bazında ayırma; yalnız başarılı üye UI'dan çıkar; başarısız aday seçili/kalan grupta; hata ve keeper korunur; freed-byte yok | Kısmi sonucun delivery'si kontrollü; doğal OEM batch hatası değil |
| `disappearingCandidateDuringSystemConsentIsNeverReportedAsTrashed` | Gerçek consent açıkken bir test-owned aday dışarıdan kaldırılır; Allow sonrası gerçek sistem batch'i kalan **2** adayı trashed yapar; VM **2** başarı + **1** doğrulanamayan URI bildirir; keeper aktif, başarısız aday seçili/kalan grupta, freed-byte yok | Gerçek API 37 yarış/partial batch; arbitrary OEM query/revocation davranışı değil |
| `realAndroidDecoderAndAllHashersCanReadSyntheticMedia` | Gerçek PNG decode/netlik; a/d/pHash non-null; birebir byte kopyalarında SHA-256 aynı | JPEG/HEIC/RAW/orientation/corrupt/large bitmap değil |
| `realAnalyzerAndNavigationPreserveSelectionAfterActivityRecreation` | Gerçek analyzer/Room'da 4 fotoğraflı exact grup/keeper; gerçek Hilt/nav ekranda 3 adaylık Activity recreation sonrası korunur; iptal medyayı değiştirmez | In-memory cache, dört küçük PNG; cache/algo matrisi JVM kanıtıyla birlikte okunur |
| `systemConsentSurvivesLandscapeActivityRecreation` | Sistem onayı açıkken gerçek Activity dönüşü; Allow sonrası 3 URI trashed, keeper aktif; boş sonuç/ScanResultHolder uzlaşır; Activity instance değişmiştir | Tek API 37 AVD; process death veya OEM davranışı değil |

Gerçek sistem pencere paketi `com.google.android.providers.media.module`, başlık `Allow PhotoClarity AI to move 3 photos to trash?`, düğmeler `android:id/button2` **Deny** ve `android:id/button1` **Allow** olarak gözlendi. Dış sahip testinde tek fotoğraf başlığı ve `removed=1, error=null` kaydı alındı. Uygulama/MediaProvider onayları sahte callback ile geçirilmedi.

### Faz 1 kabulünün yeniden değerlendirilmesi

**Genel durum: PARTIAL; Faz 1 tamamlandı sayılmaz.** Modern Android sistem-trash, app/system consent, keeper, manual/bulk selection, frozen request, cancel ve URI reconciliation alt kapılarının cihaz kanıtı sağlandı. Önceki **53 JVM testi / 0 hata** analyzer/cache/null/failure/legacy outcome kontratlarını tamamlayıcı kanıt olarak korur; mock testlerinin gerçek eski Android'e eşdeğer olduğu iddia edilmez.

Kapanmayan kabul maddeleri:

- **API 26–28:** kalıcı silme uyarısı, WRITE izin grant/ret, app onay/iptal, gerçek 0 satır ve dışarıdan kaldırılmış medya. **API 29:** farklı sahipler, RecoverableSecurityException tekil onay/ret/retry ve erken başarıdan sonraki iptalde gerçek kısmi silme/byte istatistiği. **API 30** minimum sistem-trash sürümü ayrıca koşulmalı; API 37 bu sınırları çalıştıramaz.
- Galeri/sistem çöp kutusu ekranında test medyasının görünmesi ve **oradan** restore/permanent delete akışı. Provider seviyesinde restore geçti; Google Photos/OEM galeri ürün davranışı veya uygulamaya geri yükleme süresi garantisi çıkartılamaz.
- Gerçek permission revocation / seçili fotoğraf erişimi, provider query kısıtı ve diğer OEM partial/failure nedenleri. API 37 shell-owner testi full read ile geçti; kaybolan aday testi app-owned erişimle READ_MEDIA_IMAGES yeniden verilmeden geçti. Onay sırasında dışarıdan kaybolan adayın **gerçek kısmi batch** alt senaryosu kapandı; bu tüm izin/OEM kombinasyonlarını kapatmaz.
- Gerçek p→a/d/SHA cache geçişleri, exact/visual off→on kombinasyonları, erişimi iptal edilmiş/corrupt medya ve çok küçük/büyük/HEIC/EXIF kaynaklar. JVM matrisi ve PNG cihaz smoke testleri farklı kanıt seviyeleridir.
- Sistem consent sırasında Home/back/geri giriş ve iptal + dönüş kombinasyonları, process death. Aynı callback/reentry JVM'de sınanmıştır; cihazda tam lifecycle matrisi değildir. Kalıcı session/process-death tasarımı Faz 3 kapsamı korunur.

**Yeni UX gözlemi (Faz 5):** `EmptyStateView` landscape'te kısıtlı yükseklik kullanıyor; trash başarı snackbar'ı boş sonuç alt metnini örtüyor. Başlık ve doğru işlem mesajı görünür, ancak bütün metnin okunabilirliği sağlanmıyor. `ResultsScreen`/`EmptyStateView` adaptive layout, snackbar/insets ve font-scale matrisi Faz 5'te test edilmelidir. Ayrıca birkaç test fotoğrafının kaldırılmasından sonra “Galeriniz Tertemiz” ifadesi bütün galeri analizi garantisi değildir; kapsamı doğru anlatma gereksinimi korunur. Bu oturumda production UI değiştirilmedi.

### Nihai kayıt ve çalışma ağacı

Bu oturum yalnız `app/src/androidTest` test/fixture/provenance/runbook ve analiz dokümanlarını ekler/günceller. Production kaynak/resource, manifest, SDK, Gradle veya dependency değişikliği yapılmadı. Debug APK ve release AAB SHA-256 değerleri ilk Faz 1 doğrulama kaydıyla aynıdır; test APK ayrı artifact'tır. Test paketinin build'i başarılıdır. Nihai instrumentation sonucu ve cleanup kaydı aşağıdaki ek ile birlikte okunmalıdır.

Yerel kanıtlar: `.baseline/phase1-device-final.log`, `phase1-device-assemble.log`, `phase1-device-eight-tests.log`, `phase1-device-eleven-tests-first.log`, `phase1-device-rotation-probe.log`, `phase1-device-system-prompts.log`, `phase1-device-evidence/`; ilk başarısız harness/LOW_MEMORY logları da saklandı. Bunlar ve APK/build çıktıları ignore edilir; screenshot/log/medya kullanıcı verisi olarak repository'ye taşınmaz. İlk cihaz testi için hazırlanan yeni PNG kopyası yerelde korunur, Faz 1 commit'ine alınmaz; test generator kaynak kodu aynı sentetik baytları bellekte üretir. Önceden Faz 0'da commit edilmiş JVM fixture'ları bu yayın değişikliğine dahil değildir.

HEAD `479c75d017291a7cfc303822d03ec568aa005fa9` olarak korunur; index boş. Commit/push/CI çalıştırma veya Faz 2 implementasyonu yapılmadı. Faz 0–7 sırası ve Faz 5 tasarım gereksinimleri değişmedi.

**Nihai cihaz koşusu:** doğrudan `am instrument -w -r`, dış fixture URI/folder argümanlarıyla **OK (11 tests)**; **11 passed / 0 failure / 0 skipped**, **35.237 saniye**. Son test APK build'i `assembleDebugAndroidTest`: **BUILD SUCCESSFUL, 47 saniye**, 51 görev (6 executed / 45 up-to-date). Yeni cihaz testleri eklendikten sonraki `lintDebug`: **BUILD SUCCESSFUL, 32 saniye**; rapor **0 error / 58 warning**, yeni production/dependency değişikliği yok. Önceki 53 JVM testi bu oturumda tekrar koşulmadı; önceki successful kaydı değişmemiş production APK/AAB ile birlikte tarihsel kanıttır. `git diff --check` başarılı; ana roadmap'in HEAD içeriği satır bazında prefix olarak korunuyor.

**Temizlik:** Her testin @After bloğu o testin eklediği medyayı kaldırdı. Shell-owned dış fixture son kez geri açılıp exact display name ve owner eşleşmesi kontrol edilerek yalnız kendi URI'si/dosyası temizlendi; son URI sorgusu **No result found**. Bilinen staging dosyaları ve dış fixture klasörü de kaldırıldı. Geniş galeri veya bilinmeyen klasör temizlik komutu kullanılmadı. Testlerin MediaStore üzerinden oluşturduğu boş klasörler cihazda kalabilir; kullanıcı dizinlerini tarayıp temizleme yapılmadı. Geçici **READ_MEDIA_IMAGES izni granted=false** durumuna döndürüldü; READ_MEDIA_VISUAL_USER_SELECTED da false. Emülatör açık ve boot tamam; app/test APK'ları kuruludur. Bu oturumdaki bellek artışı geçicidir. Snapshot wipe yapılmadı; ilk UTP uninstall'ın özel app state üzerindeki etkisi yukarıda açık bırakılmıştır. Screenshot/log kanıtları yerel ignore edilen dizinde tutuldu. Yeni test/dokümanlarda yaygın credential/private-key örüntüsü bulunmadı; bu tarama mutlak secret yokluğu garantisi değildir.

**Ek gerçek race/partial koşusu:** Son yeni test ayrı class#method ile **OK (1 test), 0 hata/atlama, 5.760 saniye**; APK build **35 saniye** başarılı. Kanıt `.baseline/phase1-device-race.log`, `phase1-device-race-outcome.log`, `phase1-device-race-assemble.log`, `phase1-device-evidence/disappearing-during-consent.png`. Gerçek kayıtta `actuallyTrashed=2, reported=2` ve `1 fotoğraf için işlem doğrulanamadı; bu fotoğraflar silinmiş kabul edilmedi.` görülür. Böylece **12 farklı cihaz senaryosu geçti: 11'lik tam koşu + yeni tekil koşu**; 12'lik tek bir suite koşusu iddia edilmez. Son test cleanup'ı da yalnız kendi fixture URI'lerini kaldırdı; okuma izni false olarak kaldı. Kayıp dosya güvenli biçimde başarılı sayılmasa da UI'da doğrulanamayan aday olarak kalır; gerçek provider'da artık bulunmayan üyeleri rescan/invalidation ile uzlaştırma işi Faz 3 kapsamında izlenmelidir.

Son yeni race testi sonrası lint tekrar doğrulandı: **BUILD SUCCESSFUL, 33 saniye, 0 error / 58 warning** (`.baseline/phase1-device-lint-final.log`). Kısmi sonuç screenshot'ında kayıp adayın eski thumbnail'ı hâlâ görünebilir; cache'deki önizleme dosyanın provider'da mevcut olduğunu kanıtlamaz. Hata açıkça gösterilir ve o URI başarıya katılmaz. Faz 3 media invalidation/cache reconciliation bu stale preview/metadata durumunu da ele almalıdır; bu oturumda uygulama davranışı değiştirilmedi.

## Kullanıcı onaylı Faz 1 yayın kontrolü — 2026-10-03

Yukarıdaki “commit/push yapılmadı” ifadeleri önceki implementasyon ve cihaz testi oturumlarının tarihsel kaydıdır. Kullanıcı şimdi mevcut Faz 1 değişikliklerini tek commit ile main'e yayımlamayı ayrıca yetkilendirmiştir. Bu, Faz 1 genel kabulünün tamamlandığı veya Faz 2'ye geçilebileceği anlamına gelmez.

- Yeni sentetik PNG kopyası `.baseline/phase1-publication/base-reference.png` içine taşındı; `.baseline` ve androidTest yerel PNG kopyaları ignore edilir. Yeni commit medya içermez. `Phase1SyntheticMedia.kt`, CC0 generator'ın byte-identical Kotlin portudur; production kaynak/resource/SDK/dependency bu yayın kontrolünde değiştirilmedi.
- Medya dosyası olmadan temiz cihaz-test kaynağı derlendi. `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL, 52 saniye**, JVM raporu **53 test / 0 failure / 0 error / 0 skipped**, lint **0 error / 58 warning**. JVM görevi mevcut geçerli sonuçları kullandı; GitHub workflow temiz Linux build ve test koşusunu ayrıca çalıştırır.
- Son test APK'sıyla dış fixture argümanları verilerek **12 test tek koşuda / 0 failure / 0 skipped**, **36.095 saniye**. Her test setup'ında üretilen PNG boyutu ve SHA-256 aynı referansla doğrulandı. Sonuçlar `.baseline/phase1-publication/device-check.log` ve `local-check.log` içinde; log/APK/PNG commit dışındadır. Yalnız belirli test URI/dosyaları temizlendi, geçici READ_MEDIA_IMAGES izni önceki false durumuna döndü.
- Açık kabul maddeleri aynen korunur: **API 26–30 cihaz matrisi**, **galeri UI üzerinden geri yükleme**, **diğer OEM/provider/izin senaryoları** ve **landscape snackbar/empty-state UX örtüşmesi**. Faz 1 genel durum **PARTIAL**; Faz 2 başlatılmadı.
- Yayın kapsamı production Faz 1 güvenlik düzeltmeleri, JVM/cihaz test kaynakları, yeni medya kopyasını dışlayan ignore kuralı ve rapor/roadmap/runbook'tur. Commit adayı staged blob düzeyinde secret/private-key, binary media, heap dump, build/emulator/local dosya ve kullanıcı verisi bakımından kontrol edilir. CI sonuçları commit sonrası GitHub Actions ve son sohbet kaydıyla doğrulanmalıdır; bu belge henüz çalışmamış CI'ı başarılı saymaz.
