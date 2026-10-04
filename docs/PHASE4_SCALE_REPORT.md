# Faz 4 — Ölçek ve algoritma kalibrasyonu

Tarih: 2026-10-04. Başlangıç: `main`, `691f9a9386b3d51d82076c7ab29dbf4a09e08ef2` (Faz 3 checkpoint). Kullanıcının “Faz 4'e devam et” talimatı kapsamında hazırlanmıştır. Önceki analiz/riskler, Faz 0–7 sırası ve Faz 5'in implementasyondan önce UX/UI seçeneklerini değerlendirme kapısı korunur. Bu çalışma commit/push veya Faz 5'e geçiş değildir.

## 1. Sonuç ve kabul durumu

**Genel kabul PARTIAL.** Ölçek altyapısı uygulanmış, 109 JVM testi ve gerçek API 37 decoder/Room/MediaStore/lifecycle testleriyle doğrulanmıştır. Üçer tekrarlı 1k/10k/20k sentetik dosya ölçümleri; indeks/exhaustive oracle ve üç hash için sınırlı kalibrasyon vardır. Fiziksel ARM64/gerçek galeriler, HEIC/OEM, native I/O iptali ve frame bütçeleri production kabulü olarak kapanmaz. Lazy 20k ekran ölçümleri önerilen 32 ms frame p95 bütçesini aşmıştır.

Faz 1'in keeper/frozen seçim, uygulama+sistem onayı, API 30+ sistem trash, eski OS açık kalıcı-silme uyarısı, bilinmeyen sonucu başarı saymama ve trash'e boşalan alan kredisi vermeme kontratları korunur. Kendi çöp kutusu/restore/30 gün garanti mekanizması eklenmedi. Tarama hâlâ görünür uygulamada kullanıcı başlatımıyla çalışır; background/process sonrası otomatik devam/consent yoktur.

## 2. Kodla eşleştirilmiş değişiklikler

| Yapı | Gerçek değişiklik |
|---|---|
| `core/analysis/PhotoAnalyzer.kt` | Aynı pozitif boyut exact ön filtresi; URI kimliği; 4 worker; 256 cache read/write; tek decode'dan özellik/netlik; stage süreleri ve iş sayaçları; keeper yıldızları; gerçek burst skoru |
| `core/analysis/HammingIndex.kt` | Dört bit block ile exact radius araması; tekrar hash'leri tek node; geniş radius için exhaustive fallback; bit sayımıyla doğrusal grup ortalaması |
| `core/analysis/KeeperMatcher.kt` | Kalite/URI sıralı, disjoint ve doğrudan keeper eşiğini sağlayan gruplar; recursive union-find veya çift grafiği yok |
| `core/hash/PerceptualDct.kt`, `PerceptualHasher.kt` | Separable 32×32→8×8 DCT; DC hariç 63 bit; sayısal flat-image gürültüsü temizliği; borrowed bitmap API |
| `AverageHasher.kt`, `DifferenceHasher.kt` | 64 bit korunur; source bitmap ödünç alınır, yalnız ayrı scaled bitmap sahipliği/recycle; cancellation aktarılır |
| `CryptographicHasher.kt` | Aynı digest/stream kontratı; Formatter yerine hex karakter dizisi; MD5/SHA seçimi değiştirilmedi |
| `core/util/DecodeBudget.kt`, `BitmapUtils.kt` | Long/ceil/power-of-two sınır hesabı; uzun kenar ≤256, piksel ≤65.536; panorama OR sınırı; EXIF flip/rotation; negatif/invalid decode kapalı; primitive netlik dizileri |
| `domain/model/AnalysisVersion.kt` | Pipeline sürümü 2; worker/batch/decode sınırları tek yerde |
| `HashCachePolicy.kt`, `HashCacheEntity.kt`, `HashCacheDao.kt` | Sürüm, generation/version, URI ve temel metadata doğrulaması; toplu URI sorgusu; tamamlanmış warm satıra timestamp-only yazım yok |
| `PhotoClarityDatabase.kt`, `AppModule.kt`, `app/schemas/.../3.json` | Room 3; explicit v2→v3 migration, v1→v2 korunur; eski cache silinmeden legacy version 0 olur |
| `SessionCodec.kt`, `ScanSession.kt`, `ScanCoordinator.kt` | Eski eksik analysisVersion=0; eski COMPLETED sonuçlar STALE/re-scan olur; issued recovery önce korunur; aşama ve iptal-isteniyor feedback'i |
| `MediaStoreScanner.kt`, `PhotoRepository.kt/Impl.kt` | IO cursor'dan 256 metadata sayfası; gerçek minFileSize ayarı; volume/collection başına 256 ID freshness sorgusu |
| `MediaRemovalPlatform.kt` | Sadece frozen URI setinin 256'lık trash-inclusive doğrulanması; başarısız batch/eksik satır başarı değildir; 2.000'lik sistem consent sınırı korunur |
| `RoomScanSessionRepository.kt`, `ScanSessionDao.kt` | Frozen item payload ilk yazımda; aynı request checkpoint'inde sadece issued/removed/failed flags; monotonic başarı/başarısızlık ve immutable payload guard'ları, transaction korunur |
| `ResultsScreen.kt`, `GroupDetailScreen.kt`, `PhotoCard.kt` | Grup içindeki her üye ayrı lazy item ve URI key; keeper badge URI ile; detay skoru “Ortalama benzerlik” |
| Silinen `HammingDistance.kt` / eski `PhotoGroupCard` | Sabit 64 bit yardımcı ve kullanılmayan eager grup wrapper'ı kaldırıldı; mevcut PhotoCard bileşeni korunarak dosyası yeniden adlandırıldı |

SDK/AGP/Gradle/Kotlin/runtime dependency/manifest/permission/resource yapılandırmaları değiştirilmedi. CI yalnız gerçek JVM test alt sınırını 109'a ve gerekli Room şemalarını 1/2/3'e taşıdı; action pin'leri korunur.

## 3. Algoritma ve veri güvenliği kontratı

### Exact

Pozitif dosya boyutu en az iki URI'de görülmüyorsa tam dosya digest'i istenmez. Gruplama `(size, digest)` üzerindedir. Aynı boyutlu yeni dosya geldiğinde daha önce hesaplanmayan crypto alanı tamamlanır. `SHA256` tercihi SHA-256, mevcut diğer tercihler exact için MD5 kullanmaya devam eder; bağımsız visual tercih pHash/aHash/dHash'tir. Bu faz varsayılan tercihi sessizce değiştirmedi. Boyut+digest, her çift için byte-by-byte doğrulama değildir; özellikle adversarial MD5 collision ihtimali matematiksel olarak yok sayılmaz. Gerçek fotoğraflarda hash etiketi veya görsel öneri silme garantisi değildir; explicit review/onay korunur.

### Görsel indeks ve keeper

pHash 63 aktif bit, aHash/dHash 64 bittir. Eşik, `1f - distance/bits >= threshold` predicate'ine göre integer radius'a çevrilir; kaydedilmiş eşik değeri değiştirilmez. Varsayılan .85 için radius 9'dur. Dört disjoint block'ta radius-r komşunun en az bir block'u `floor(r/4)` içinde bulunmalıdır; indeks bu adayları toplar ve tam Hamming kontrolü yapar. Yaklaşık pruning yoktur. Radius >12 exhaustive unique-node fallback kullanır; .70 gibi geniş eşikler ve yoğun/adversarial aynı-prefix dağılımları **hâlâ O(n²)** maliyete gidebilir. İndeks her dağılım/eşikte subquadratic süre garantisi değildir.

Önce en yüksek qualityScore, eşitlikte URI sırası keeper olur. Her üye keeper ile eşiği sağlamalıdır. A–B ve B–C, A–C'nin aynı gruba alınmasını zorunlu kılmaz. Gruplar greedy/disjoint keeper yıldızlarıdır; maximal-clique veya tüm üyeler arası minimum benzerlik vaadi yoktur. Ortalama benzerlik tüm çiftlerin gerçek Hamming ortalamasıdır: her bit için `ones*(n-ones)` farklı çift sayısıyla O(bits*n) hesaplanır. 0/all-one bilgi yoksunu hash'ler visual/burst önerisinden çıkarılır; bu bilinçli konservatif filtre bazı gerçek görsel benzerlikleri kaçırabilir. Exact byte-hash yolu bu fotoğrafları ayrıca bulabilir.

Burst: aynı volume/collection ve bucket, en az üç fotoğraf, ilk çekimden itibaren toplam ≤2 saniye; sonra gerçek visual keeper eşiği ve ölçülmüş skor. Yalnız zaman yakınlığı veya eski sabit .95 artık benzerlik kanıtı değildir. LOW_QUALITY hâlâ tekil netlik önerisi, keeper korumalı, silme adayı olmayan sıfır waste grubudur.

### Cache ve persisted findings

Eski algoritma/metadata cache'i DB migration'da tutulur ama hit değildir. URI, pipeline version, size, dateModified/dateAdded, width/height/MIME, generationModified ve MediaStore version eşleşmelidir. Feature seçimi değişince yalnız eksik alan tamamlanır. Current-version sıfır sharpness geçerli flat ölçümdür; negatif/eksik ölçüm tekrar denenir. Expiry eski 30 günlük **analiz cache** kuralıdır; fotoğraf trash süresiyle ilgili ürün vaadi değildir.

Session analysisVersion eski/missing ise sonuçlarla işlem açılmaz; yeniden tarama gerekir. Önceden issued trash'in read-only recovery'si bu kontrolün öncesinde korunur. Tercihler/fotoğraflar/stats silinmez; destructive migration yoktur. URI, volume'lar arası Long ID çakışmasını ayırır; eski Long ID tabanlı UI seçimi belirsiz ID'lerde kapalı kalır.

## 4. Ölçek ve maliyet sınırları

- En çok dört çalışan fotoğraf görevi; batch içinde iş dağıtımı atomic index ile. Binlerce coroutine/bitmap aynı anda açılmaz. Normal cold ihtiyaçta tek bounded bitmap quality ve seçilen visual hash için paylaşılır; source finally recycle edilir, source=scaled durumunda double recycle yoktur.
- DCT için kullanılan çarpım sayısı 10.240'a düşer; önceki tam 32×32 frekans yaklaşımı yaklaşık 1.048.576 çarpım yapıyordu. EXIF/resampling/numeric değişimleri cache sürümüne bağlıdır.
- 20k cache read 79 batch'tir; Room/SQLite bind limitinin altında 256 placeholder kullanılır. Warm tam özelliklerde timestamp yenilemesi nedeniyle gereksiz yazım yapılmaz. Expiry veya eksik/değişmiş özelliklerde warm iş sıfır olmak zorunda değildir.
- Metadata sayfalanır ama analiz katalogu, feature/index, findings ve session reload **O(n) bellekte** kalır. Persisted generation checkpoint ile yalnız incremental metadata delta uygulanmadı; her tarama erişilen katalogu yeniden enumerate eder. Bu, kaldırılan/değişmiş/limited URI'leri atlayan güvensiz bir delta varsayımından kaçınır; gelecekte doğru delete/revoke/version-reset invalidation ile tasarlanmalıdır.
- Çok büyük grup üyeleri lazy compose edilir; tüm thumbnails aynı anda istenmez. Group/Photo modelleri hâlâ bellektedir; Room Paging ile end-to-end bounded model storage yoktur. Seçim projeksiyonları/JSON reload bazı işlemlerde O(n), history ve stats dedup ledger retention önceki gibi açık borçtur.
- ResultsScreen'in eski `onGroupClick` callback'i hâlâ kullanılmaz; yeni detail navigation açılmadı. Detail lazy testi kontrollü host ile ekranı doğrudan açar, production gezinme kanıtı değildir. Mevcut uzun dikey akış/inceleme sorunu ve tam görüntü/review ürün kararı Faz 5'te yeniden değerlendirilecektir.
- İptal isteği synchronous “durduruluyor” state'i üretir; yeni iş/operation gate ancak gerçek job bitince açılır. CPU döngüleri cancellation kontrolü yapar. Native decoder, provider query/open ve blocking I/O'nun her OEM'de ≤2 saniyede kesildiği kanıtlanmadı; anlık state feedback, gerçek ekran ≤100 ms ölçümü yerine sayılmaz.

## 5. Benchmark kanıtı — API 37 / 4 GB emülatör

Pixel_4 oturumu, `sdk_gphone16k_x86_64`, API 37, MemTotal 4.008.492 KiB (~4 GB runtime). AVD config/userdata değiştirilmedi. JBR 21.0.10 / mevcut JDK17 bytecode / mevcut proje toolchain. Debug instrumentation; fiziksel ARM64 veya minified release performans kanıtı değildir.

40 ayrı seeded 1440×1440 JPEG (yaklaşık 2,07 MP), quality 65, her aileye byte-identical gerçek dosya kopyaları. Ortalama dosya ~116.594 byte; 20k toplam 2.331.870.500 byte (~2,17 GiB). App-private file URI'leri, 40 exact aile (20k'da aile başına 500); gerçek crypto/pHash/decoder ve disk-backed Room cache. Başka uygulama galerisi taranmadı. Tamamlanan findings ayrıca isolated Room'a yazılıp yeniden okunur.

Cold = boş UUID hash cache; warm = hemen sonraki tam feature cache. OS page cache/JIT cold değildir. Timer, dosya yaratma/kopyalama ve MediaStore discovery'yi kapsamaz; persistence ayrıca ölçülür. PSS aynı test/target process'inde 100 ms aralıklarla örneklenir; her çok kısa transient tepeyi yakalama garantisi yoktur. Logical bytes bir I/O counter değildir; bounds/EXIF/decode/provider/native working-set ayrı trace edilmedi. İlk 1k ve bazı tekrarlar host Gradle doğrulamasıyla çakıştı; bu kontrollü fiziksel laboratuvar SLO ölçümü değildir.

| Dataset, üç tekrar | Cold median [min–max], ms | Warm median [min–max], ms | Findings persist median [min–max], ms | Peak PSS median / en yüksek, KiB |
|---|---:|---:|---:|---:|
| 1.000 | 2.527 [2.412–5.984] | 246 [162–480] | 160 [128–344] | 178.745 / 180.571 |
| 10.000 | 33.867 [31.562–34.288] | 1.190 [1.182–1.675] | 1.365 [1.353–1.533] | 187.714 / 195.108 |
| 20.000 | 66.946 [65.580–67.063] | 2.352 [2.256–2.494] | 2.829 [2.773–3.691] | 206.110 / 208.331 (~203,4 MiB max) |

Bütün koşularda 40 exact grup doğru; failed=0; cold decode/full-file hash/write sayısı n; peakWorkers=4. Warm decode=0, full-file hash=0, cache write=0; cache read 1k/10k/20k için 4/40/79. Bu duplicate-heavy set visual stage'i exact sonrası boş bırakır; bağımsız 20k visual indeks stresini aşağıdaki oracle/CPU testi sağlar. Benchmark planının %70 independent, %15 exact, %10 near, %5 burst, gerçek JPEG/PNG/HEIC ve 0,2–8 MiB fotoğraf karışımıyla eşdeğer değildir.

JVM seeded **20k bağımsız** 63 bit hash, .85: **2.008.108** tam Hamming kontrolü ve 10.640.000 bucket lookup, bu makinede test süresi 0,861 saniye. 20k aynı feature tek unique node, tek keeper grubu ve tek Hamming kontrolüdür. JVM süresi Android cihaz süresi yerine kullanılamaz; random/duplicate dağılımları kötü durum dağılımını kanıtlamaz.

Android'de çalışan 30k record / .70 wide-radius CPU görevine iptal: **311 ms**. Native decode/provider veya bütün gerçek scan cancellation ölçümü değildir. 20k virtual üyeli Results+Detail ekranı bounded tree ve gerçek touch swipe kontrollerini geçti; üç koşuda görünür image node sayısı 4, frame p95 **46,44 / 35,25 / 35,80 ms** (101/103/106 frame). Hepsi önerilen 32 ms üstündedir. URI'lerde gerçek thumbnail bytes yoktur; GPU/cache/physical jank kabulü açık kalır. İlk accessibility scroll-action tabanlı test swipe'ı doğrulayamadı; gerçek touch swipe sürümünde geçti, bu başarısız ön koşu silinmedi.

## 6. Kalibrasyon — semantic doğruluk sınırı

40 seeded renk deseninin JPEG95/JPEG65 yeniden kodlamaları labelled positive; farklı ailelerden 780 pair negative. Her algoritma 40 positive / 780 negative ile aynı threshold predicate'inde ölçülür. Gerçek fotoğraf, crop, panorama, screenshot, face, burst hareketi, gece görüntüsü veya HEIC gold seti değildir. Production threshold recommendation, istatistiksel confidence interval veya gerçek galeri precision garantisi verilmez; default .85 değiştirilmedi.

| Algoritma | Threshold | TP / FN | FP / TN | Precision | Recall |
|---|---:|---:|---:|---:|---:|
| pHash/aHash/dHash (her biri) | .70 / .85 / .90 (her eşik ayrı) | 40 / 0 | 0 / 780 | %100 | %100 |
| pHash | .95 | 39 / 1 | 0 / 780 | %100 | %97,5 |
| pHash | .99 | 28 / 12 | 0 / 780 | %100 | %70 |
| aHash | .95 | 38 / 2 | 0 / 780 | %100 | %95 |
| aHash | .99 | 7 / 33 | 0 / 780 | %100 | %17,5 |
| dHash | .95 | 38 / 2 | 0 / 780 | %100 | %95 |
| dHash | .99 | 14 / 26 | 0 / 780 | %100 | %35 |

**Candidate recall** başka ölçüdür: 63/64 bit, bütün integer radius'lar, seeded random+yakın+duplicate/sign-bit hash setlerinde exhaustive oracle ile indeks sonuçları aynı, observed recall %100. Keeper-star sonucu ayrıca exhaustive greedy-star oracle ile karşılaştırılır. Bu, fotoğraf semantic recall'ünün %100 olduğunu söylemez.

## 7. Test/build kanıtları ve ortam hataları

- `Phase4AlgorithmTest`: 12 JVM oracle/DCT/chain/20k/pair-mean/decode/cancellation testi. `CacheTransitionCharacterizationTest`: 21 cache/flag/URI/worker/ownership/flat/burst testi. Önceki keeper/consent/stats/session/platform testleriyle toplam **109/109**, skip/error/failure yok.
- `Phase4ScaleDeviceTest` + `Phase4LazyResultsDeviceTest` + güncel Phase3 persistence/lifecycle: **16/16** API 37 core kontrolü. Room v1→v2→v3 cache ve tercihler korunur, legacy algorithmVersion 0/missing session version davranışı sınanır. 2.001 journal item üzerinde SQL trigger audit, checkpoint sırasında INSERT/DELETE=0, 2.000 removed + 1 issued flag doğrular.
- 600 owned PNG, configured minimum size sınırı, 256/256/88 page, restore sonrası generation invalidation, deleted row, 270 trashed/nontrashed mixed set doğrulaması gerçek provider ile geçti. Testin doğrudan owned-row trash/restore ayarı production consent'in yerine sayılmaz; consent ayrı regresyondur.
- Üçer tekrarlı 1k/10k/20k analyzer koşuları ve iki ek lazy koşu geçti. İptal-isteniyor feedback'i coordinator JVM testinde job tamamlanmadan kontrol edilir; gate gerçek termination'a kadar tutulur.
- **Final consent/process regresyonu:** 12/12 Faz 1 (dış sahipli fixture dahil), 2.000+1 gerçek consent ve FULL/LIMITED/DENIED olmak üzere 4/4 Faz 2 kontrolü geçti. Batch koşusu 205,125 saniye sürdü; önceki 328,682 saniyelik koşu/timeout kök nedeninin çözüldüğü iddia edilmez. Üç ayrı seed→force-stop→farklı PID→verify zinciri ve scoped cleanup geçti: tamamlanmış seçim, yarım tarama, callback teslim edilmemiş approved trash. Keeper ve sıfır trash kredisi korundu. Önceki faz raporları tarihsel olarak korunur.
- Son iptal feedback kaynakları dahil debug/test APK, minified unsigned AAB, 109/109 JVM ve debug/release lint başarılı (final build 7m25s). Lint 41 warning / 0 error; eski warning'ler suppress edilmedi. CI actionlint geçer. Uzak CI bu uncommitted ağacı doğrulamadı; Faz 3 checkpoint'in başarılı CI'si Faz 4 kanıtı değildir.

Başarısız/ara kontroller saklanır: lazy dosyalardaki eksik mediaKey import'u ilk derlemede düzeltildi. İlk cihaz koşusu 4/6 geçti; metadata testi başlangıçtaki DENIED app izniyle fail-closed kaldı (shell identity app permission snapshot'ını değiştirmedi), hard link benchmark kurulumu EACCES aldı. FULL izin dışarıda kaydedilip geçici verilerek ve gerçek dosya copy kullanılarak yeniden sınandı. Bu hatalar production kodunda izin bypass'ı, exception swallow veya skip ile gizlenmedi. Önceki 2 GB AVD LMK ve büyük consent timeout bulguları kapanmadı.

## 8. Açık kabul kapıları / sonraki kontrollü işler

| Kapı | Durum / gerekli kanıt |
|---|---|
| Fiziksel 4 GB ve 8 GB+ ARM64, API 29/34/35/36 | Açık; 3 tekrar, kontrollü host/OS, representative gerçek cold/warm galeri, PSS/CPU/I/O/trace ve ANR/OOM |
| Mixed gerçek 10k/20k ve adversarial yoğun indeks | Açık; duplicate-heavy JPEG/private URI + random hash seti tam karşılığı değildir; wide-radius worst case maliyeti kalır |
| Native/provider cancellation ≤2 s; ekran feedback ≤100 ms | CPU ve synchronous state kanıtı var; blocking native/provider ve gerçek ekran gecikme ölçümü açık |
| Frame p95 ≤32 ms, jank < %5 | Emülatör virtual-screen p95 bütçe üstü; gerçek thumbnails/physical trace/jank oranı açık |
| Representative semantic precision/recall | Sentetik recompression kalibrasyonu var; hakları belli gerçek gold set + crop/rotate/edit/burst/screenshot/HEIC stres açık |
| Tam incremental metadata delta / end-to-end Paging | Uygulanmadı; O(n) katalog/findings, selection projeksiyonları, JSON reload ve retention borcu kalır |
| API 26–29 metadata fallback ve permanent/process-kill | Generation/version yokken aynı metadata ile değiştirilmiş byte'ları warm cache kaçırabilir; OEM/izin/kaybolan dosya matrisi açık |
| API 26–36/OEM, gerçek picker/revoke, galeri UI restore, cloud/D2D | Önceki fazlardan aynen açık; owned provider restore ve shell-grant testi bunları kapatmaz |
| Default 2 GB LMK; önceki 2.000 consent callback timeout | Önceki başarısız kanıtlar korunur; 4 GB veya yeni başarılı koşu kök neden/low-memory kabulünü kapatmaz |
| Landscape snackbar, TalkBack/200% font/adaptive/state restoration/review ilerleme | Faz 5 tasarım ve accessibility kabulü; Phase4 lazy teknik düzeni çok grup arasında hızlı gezinme problemini çözmüş sayılmaz |
| sameFolder/useMetadata/useGPS/smartSelection ürün kontratı, kalite heuristiği | Önceki etkisiz seçenek/ürün borçları korunur; minimum size artık uygulanır; keeper quality ağırlıkları yeniden ürün doğrulaması ister |
| Signing, license/rights, Play/release QA | Önceki kapılar açık; unsigned AAB, minified cihazda bu pipeline'ın doğruluk/perf QA'sı yerine sayılmaz |

Bu fazın çıktısı kontrollü checkpoint adayıdır. Önceki riskleri ve Faz 5'te yeniden ekran/kullanım senaryosu incelemesi, alternatifler/nedenler, seçilen yaklaşım ve implementasyon kabul kapısını değiştirmez. Carousel/grid/master-detail/overview gibi bir ürün modeli seçilmedi. Teknik lazy iyileştirme, yanlış silme/keeper/frozen request kontratını zayıflatmaz.

## 9. Dosya/veri güvenliği ve kaynaklar

Generated JPEG/PNG, test DB/DataStore, query scripts, consent screenshots, log/metrics, APK/AAB/APKS ve emulator/local state Git dışındadır. Kanıtlar `.baseline/phase4/` veya emülatörde scoped test dizinleridir; fixture cleanup yalnız UUID/URI/name/owner sınırlarında yapılır. AVD wipe, app data clear, user-photo silme, signing/credential üretimi veya commit/push yoktur.

Final kontrol: son kaynakta 8/8 persistence/lifecycle ve trash-inclusive batch cleanup birlikte **9/9** geçti. Test dış-sahip fixture'ı exact name/owner doğrulamasıyla temizlendi; üç process-kill fixture record'u temizlendi. 20k dosya/isolated DB dizinleri kalmadı. Room'un bıraktığı 13 sıfır-byte UUID `.db.lck` dosyası, debug process stopped iken tam yol/boyut doğrulanarak kaldırıldı; yeni Phase4 test cleanup'ı kendi lock dosyasını da kapattıktan sonra siler. Bu test-only cleanup değişikliği APK build ve gerçek journal testiyle tekrar doğrulandı, **1/1** geçti; `cache`/`databases` Phase4 araması boş. Başlangıçtaki READ_MEDIA_IMAGES ve READ_MEDIA_VISUAL_USER_SELECTED **granted=false**, aynı permission flags ile geri yüklendi.

Final Git audit: 51 source/test/schema/docs path; yeni medya/binary, credential pattern, build/test çıktısı, local/IDE/emulator veya kullanıcı-verisi adayı yok. Index boş, branch `main`, HEAD başlangıç commit'iyle aynı. Önceki ana analiz ve Faz 1/2/3 risk raporları korunur; ana rapor yalnız ek bölümle genişletildi. Resource/manifest/platform/dependency yapılandırmaları değişmedi. Eski iki helper/wrapper source kaldırımı kontrollüdür; schema 1/2 değişmedi, 3 yalnız structural export. Generic secret taraması sıfır aday verdi; bu bütün olası secret türleri için matematiksel yokluk garantisi değildir. Actionlint ve diff whitespace kontrolü geçti. GitHub'a yayın yapılmadığından yeni remote CI kanıtı yoktur.

Tasarım: [PHASE4_SCALE_DESIGN.md](PHASE4_SCALE_DESIGN.md). Protokol: [BENCHMARK_PLAN.md](BENCHMARK_PLAN.md). Komut/kanıt sınırları: [androidTest README](../app/src/androidTest/README.md). Resmî kaynaklar: [BitmapFactory sample/pixel options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options), [EXIF flip/rotation](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface), [Room explicit migration](https://developer.android.com/training/data-storage/room/migrating-db-versions).

## 10. Faz 4 checkpoint yayın hazırlığı — 2026-10-04

Kullanıcı mevcut Faz 4 değişikliklerinin tek açıklayıcı commit olarak `main` branch'ine, ardından `origin` remote'una push edilmesini yetkilendirdi. Yukarıdaki commit/push yapılmadı, index boş ve uzak CI yok ifadeleri geliştirme oturumunun tarihsel kaydıdır. Checkpoint işlemi Faz 5'i başlatmaz ve production kabulü değildir.

Yayın öncesi yeniden kontrol edilen 51 path, son doğrulanan içeriklerle SHA-256 düzeyinde eşleşti: 49 metin/schema dosyası ve iki kontrollü source kaldırımı. Yeni benchmark/test medyası, kullanıcı verisi, secret/credential, binary, build/test çıktısı veya local/emulator/IDE dosyası commit adayı değildir. Test generator kodları medya dosyası içermez; Room JSON export'u yalnız structural şemadır. Önceki ana analiz ve Faz 1/2/3 risk kayıtları korunur; platform/dependency/manifest/resource ve Room 1/2 değişmedi. Yerel son raporlar **109/109 JVM**, debug/release lint **0 error / 41 warning**; actionlint ve whitespace kontrolü başarılı. Commit öncesinde yalnız denetlenmiş path listesi stage edilecek, index blob'ları yeniden taranacak; push sonrasında local HEAD, `origin/main`, GitHub `main` ve ilgili Actions sonucu ayrıca doğrulanacaktır. Bu bölüm, henüz alınmamış uzak CI sonucunu başarılı saymaz.

**Genel kabul PARTIAL olarak korunur.** Bölüm 8'in fiziksel cihaz/OEM/HEIC, mixed gerçek 10k/20k galeri, frame-budget/jank, native/provider iptali ve semantic doğruluk kapıları aynen açıktır. Önceki API/izin/picker/revoke, galeri restore, cloud/D2D, düşük bellek, consent timeout ve landscape snackbar bulguları kapanmadı. CI veya push başarısı bu doğrulamaların yerine geçmez.
