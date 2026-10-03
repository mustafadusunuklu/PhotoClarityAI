# Faz 0 baseline ve çalışma güvenliği — 3 Ekim 2026

Ana referans [PHOTOCLARITYAI_PROJECT_ANALYSIS_AND_ROADMAP.md](../PHOTOCLARITYAI_PROJECT_ANALYSIS_AND_ROADMAP.md) olarak korunur. Önceki audit, risk kayıtları ve Faz 0–7 sırası değiştirilmedi; Faz 5 sonuç UX/UI yeniden tasarım gereksinimi korunur. Bu belge yalnız Faz 0 uygulamasının karar ve kanıtlarını kaydeder. Faz 1 doğruluk/silme güvenliği, Faz 2 SDK migration, Faz 3 state, Faz 4 optimizasyon veya Faz 5 UI implementasyonu yapılmadı.

## Git/GitHub bulgusu ve koruma yöntemi

| Alan | Doğrulanan durum |
|---|---|
| Kullanıcının GitHub deposu | https://github.com/mustafadusunuklu/PhotoClarityAI |
| Gerçek eski checkout | `C:\Users\<USER>\OneDrive\Masaüstü\stitch_budget_management_system\PhotoClarityAI` |
| Güncel çalışma kökü | `C:\Users\<USER>\AndroidProjects2\stitch_budget_management_system\PhotoClarityAI` |
| Branch | `main`, tracking `origin/main` |
| Yerel HEAD / origin/main / remote HEAD | `8f7c344aec6735b539a783167a37664c388db6ad` |
| Geçmiş | `e91ec0c` initial commit; `8f7c344` README commit, ikisi de 11 Haziran 2026 |
| Remote refs | `main`; tag yok (inceleme anı) |
| Eski working tree | Temiz; işlem sonunda da temiz |
| Repository bütünlüğü | Kaynak ve kopyalanan metadata için `git fsck --full` sorun bildirmedi |

Bu belgede kişisel Windows kullanıcı dizini `<USER>` olarak maskelenmiştir. Gerçek path yerel snapshot kayıtlarında kalır.

Masaüstü klasörünün GitHub ile birebir HEAD ve temiz dosya ilişkisi, geçmişte yüklemenin bu kopyadan yapılmış olabileceğini destekler. Hangi UI/araç/klasörden push yapıldığı Git verisinden kesin kanıtlanamaz; yükleme işlemi kaydı incelenmedi.

80 kaynak/config karşılaştırmasında 75 dosya aynı, dört dosya farklı, `gradle.properties` yalnız güncel kopyada vardı. GitHub/desktop sürümünden farklı olan mevcut dosyalar **aynen korundu**:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/photoclarity/ai/MainActivity.kt`
- `app/src/main/java/com/photoclarity/ai/ui/components/AnimatedCheckbox.kt`
- `app/src/main/java/com/photoclarity/ai/ui/results/GroupDetailScreen.kt`

Standalone `.git` metadata'sı doğrulandı (alternates/core.worktree/aktif özel hook yok), kaynak snapshot'ından sonra **yalnız metadata** güncel köke kopyalandı. İlk Faz 0 baseline hazırlanırken Git init/clone/reset/checkout/clean/pull/merge/rebase, staging, commit veya push yapılmadı. Eski kaynak dosyalar yeni sürümle değiştirilmedi. OneDrive depo bağımsız ve değişmeden kaldı.

Yayın öncesi yalnız Faz 0 altyapısıyla hazırlanan commit adayı derlenemedi: eski GitHub manifesti depoda olmayan `@mipmap/ic_launcher_round` kaynağına işaret ediyor. Mevcut dört production farkı snapshot'ta zaten vardı; manifestte bu geçersiz satır kaldırılmış, `AnimatedCheckbox` ve `GroupDetailScreen` içinde eksik import'lar eklenmiş, `MainActivity` içinde yalnız yorum satırı boşaltılmıştır. Bu dört **önceden mevcut** fark kaynak baytları değiştirilmeden birlikte baseline commit'ine alınır. Böylece commit, incelenen ve yerelde derlenen uygulama durumunu korur; eski HEAD kaynaklarına dönüp davranışı değiştirmez. Bu kayıt Faz 1 düzeltmesi değildir.

Windows sandbox ownership uyarısı yalnız komut başına `git -c safe.directory=...` ile çözüldü; global Git config değiştirilmedi. Salt-okunur kontrollerde `GIT_OPTIONAL_LOCKS=0` kullanıldı. Normal kullanıcı terminalinde aynı ownership sorunu olmayabilir; sorunu çözmek için global `safe.directory=*` kullanmayın.

## Yerel güvenli checkpoint

İşlem öncesi snapshot: `.baseline/phase0-20261003-192625/`.

- `source-snapshot.zip`: 82 dosya, 130.856 byte; `app/src`, Gradle/config, mevcut roadmap ve yerel SDK config içerir.
- `source-manifest.json`: her dosyanın göreli yolu, byte boyutu ve SHA-256.
- ZIP entry'lerinin tamamı manifest ile SHA-256 düzeyinde doğrulandı.
- `history.bundle`: mevcut iki commit ve refs; `git bundle verify` complete history / okay. Yeni commit değildir.
- Snapshot kişisel SDK path'i/özel yerel config içerir; ignored, özel backup olarak kalır; Git/CI'ye yüklenmez.
- Beş `.hprof` **kopyalanmadı/silinmedi/taşınmadı**: toplam 3.871.950.659 byte (~3.61 GiB). Mevcut yerde korunur; `.gitignore` ile Git kaynak kümesinden ayrılır. Memory dump mahrem veri içerebilir.

Production `app/src/main` içindeki 72 dosyanın son SHA-256 kontrolü snapshot ile aynı. Source/config checkpoint mevcut `.idea`, build/cache ve heap dump'ların tam disk backup'ı değildir; bunlar kaynak baseline'ı dışında, mevcut yerlerinde korunmuştur. Snapshot üzerinden geri yükleme gerektiğinde önce dirty çalışma dosyaları ayrıca saklanmalı; otomatik overwrite/reset yapılmamalıdır.

## Oluşturulan/değiştirilen dosyalar

| Dosya/küme | İşlem ve sınır |
|---|---|
| `.git` | Doğrulanmış geçmişin bağımsız metadata kopyası; source checkout yok |
| `.gitignore` | GitHub'daki eski ignore yerine geniş root hijyen politikası; bu kökte başlangıçta dosya yoktu |
| `.gitattributes` | Yalnız Wrapper ve workflow için line ending; production source renormalization yok |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `.properties` | Gradle 8.7 ile izole wrapper generator'da üretilen eksiksiz Wrapper ve dağıtım SHA256 |
| `app/build.gradle.kts` | Yalnız iki yeni `testImplementation` alias'ı |
| `gradle/libs.versions.toml` | Mockito 5.14.2 ve coroutines-test alias'ları; mevcut coroutines 1.9.0 sürümü tekrar kullanılır; production dependency upgrade yok |
| `app/src/test/java/com/photoclarity/ai/baseline/` | Dört gerçek test suite, toplam 24 test; MainDispatcherRule/fake DAO/repository/settings/URI doubles |
| `app/src/test/resources/fixtures/` | Üç sentetik PNG, SHA256SUMS, hak/kapsam README'si |
| `tools/GenerateTestFixtures.java` | Deterministik, CREATE_NEW korumalı, network/Android bağımsız CC0 generator |
| `.github/workflows/android-baseline.yml` | Debug/unsigned release/unit/lint CI, test sayısı kapısı, kısıtlı artifact upload |
| `README.md` | Gerçek mevcut davranış, build/run/test, lisans belirsizliği; eski takım atıfları korunur |
| `docs/BENCHMARK_PLAN.md` | Cihaz/set/ölçüm/ön bütçe ve sonraki faz ölçüm kapısı |
| `docs/PHASE0_BASELINE_REPORT.md` | Bu rapor |
| Ana roadmap | Yalnız sonuna Faz 0 durum eki; mevcut analiz/risk/Faz 5 metni korunur |
| `gradle.properties` | Önceden var olan, makine yolu/credential içermeyen ortak AndroidX/Jetifier/JVM ayarları **değiştirilmedi**; temiz checkout build'inin aynı proje ayarlarını kullanması için Faz 0 commit kapsamına alındı |

Gradle/AGP/Kotlin/KSP/SDK/platform/runtime dependency sürümleri korunmuştur. Test dependency'leri APK/AAB runtime dependency'sine eklenmez. Root Wrapper dışında app runtime davranışını değiştiren source/resource/manifest düzenlemesi yapılmadı.

## Wrapper ve ortam doğrulaması

Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21, KSP 2.0.21-1.0.27, SDK compile/target 34, min 26, JVM bytecode 17. Local JBR 21.0.10, Windows 11; SDK build tools 34.0.0. CI referansı Temurin 17. Terminal varsayılan Oracle 25 kullanılmadı; local doğrulama JDK 17 ile yapılmış gibi raporlanmaz.

Wrapper JAR SHA256 `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`; dağıtım `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`. Değerler [resmî JAR checksum](https://services.gradle.org/distributions/gradle-8.7-wrapper.jar.sha256) ve [resmî dağıtım checksum](https://services.gradle.org/distributions/gradle-8.7-bin.zip.sha256) yanıtlarıyla karşılaştırıldı. Generator çıktısı JAR byte'ları doğrulandı; distribution checksum property olarak sabitlendi. Mevcut unpacked Gradle cache'in her byte'ı bağımsız yeniden indirilen ZIP ile karşılaştırılmadı; checksum Wrapper'ın yeni indirdiği dağıtımı korur.

Unix script / Windows bat tamam; Unix script Git index'inde executable mode (`100755`) ile saklanır. README ve CI `bash ./gradlew` kullanır. `.gitattributes` sonraki checkout'larda Unix script/properties için LF'i korur. SDK path veya bu makinenin JDK konumu repository build config'e gömülmedi.

İlk sandbox Wrapper çağrısı `user.home` nedeniyle `C:\.gradle` lock directory yaratamadı; kaynak/build hatası değildir. Açık `GRADLE_USER_HOME=C:\Users\<USER>\.gradle` ve normal profile/SDK erişimli çalışma ile giderildi. Runbook bu ortam ayarını açıkça içerir.

## Gerçek test baseline'ı

| Suite | Test | Gerçek davranış / sonraki faz riski |
|---|---:|---|
| CryptographicHasherCharacterizationTest | 6 | Null stream boş MD5/SHA döndürür; iki erişilemeyen URI çakışır **KNOWN BUG R03**. Open/read exception null ve close; 12.420-byte fixture stream hash/close |
| CacheTransitionCharacterizationTest | 5 | Gerçek PhotoAnalyzer'ın warm pHash sonucu; p→a/d, exact SHA/MD5 enable'da eksik cache hash tamamlanmaması **KNOWN BUG R04**; zero media I/O/cache write assertion |
| ResultsCharacterizationTest | 11 | Smart selection, deselection, keeper manuel seçim, low-quality grup seçimi; Success(0)/partial/error/permission ve changed-selection consent; stale keeper/waste/global update **R02/R07/R12/R13/R14** |
| FixtureIntegrityTest | 2 | Üç committed PNG'nin hash/boyut/signature manifesti, byte exact-copy / değişik brightness varyantı |
| **Toplam** | **24** | **0 failure / 0 error / 0 skipped** |

Testler real `CryptographicHasher`, `PhotoAnalyzer`, hash helpers ve `ResultsViewModel` çağırır. DB/settings/photo repository fake, Android Context/ContentResolver/Uri/IntentSender mock. Gerçek kullanıcı fotoğrafı, MediaStore query/delete veya Android sistem silme diyaloğu çalıştırılmaz. Null stream/0-row delete gibi hatalı durumlar bilerek mevcut sonuçlarına assert edilir; Faz 1'de bu testler güvenli yeni kontrata çevrilmeden yeşil baseline release güvenliği sayılmaz.

Özellikle `Success(0)` testinin yeşil olması, seçilen fotoğrafın UI'dan kaybolmasının doğru olduğu anlamına **gelmez**. `RequiresPermission` testi gerçek sistemin medya silip silmediğini test etmez; yalnız ViewModel state geçişidir. Cache fake'i Room query/migration kanıtı değildir. Android bitmap hash/decode, scanning permission, cancellation, lifecycle/process death, Compose/accessibility ve gerçek repository API 26–36 matrix kapsam dışıdır. Phase 0 için kapsamlı coverage yüzdesi iddia edilmedi.

JDK 21 Mockito self-attach/dynamic agent uyarısı test raporunda görülebilir; failure değildir. JVM test dependency'sini production değişikliğine veya Android stub'larını sessizce varsayılan döndürmeye dönüştüren `returnDefaultValues` ayarı kullanılmadı.

## Build/lint/CI sonuçları

Yerel ana kökte Wrapper ile:

```powershell
.\gradlew.bat --no-daemon clean :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :app:lintDebug --console=plain
```

`BUILD SUCCESSFUL in 2m 55s`; 109 actionable task (68 executed / 37 from cache / 4 up-to-date). Bu süre build süresidir, fotoğraf tarama benchmark'ı değildir. `:app:testDebugUnitTest` gerçekten çalıştı; shader/Java-only test compile görevlerindeki NO-SOURCE, test kaynağı yok anlamına gelmez.

Bu ilk run'da iki test dependency'si geçici olarak doğrudan Gradle string'iydi: lint 0 error / 57 warning (UseTomlInstead 2 dahil). Daha sonra bunlar version catalog alias'larına taşındı; production dependency'ler değişmedi. Final lint sonucu aşağıdaki son doğrulama kaydında yer alır; uyarılar suppress edilmedi.

Debug APK 19.122.368 byte; ilk release AAB 4.037.448 byte. `jarsigner -verify` **jar is unsigned**; Gradle `signReleaseBundle` task adını release signing kanıtı olarak yorumlamayın. Signing key, credential, secret oluşturulmadı. Native strip yapılamayan iki `.so` uyarısı mevcut audit'teki gibi devam ediyor. Minified AAB derlemek installed split/runtime/Play yayın kanıtı değildir.

**İzole kaynak tekrarı:** Git kaynak kümesi + mevcut untracked Faz 0 dosyaları `.baseline/.../clean-source-export/` içine kopyalandı; `.git`, `.gradle`, generated build, local.properties, heap dump ve IDE dosyaları dahil edilmedi. SDK yalnız `ANDROID_HOME` ile sağlandı. `--no-build-cache --max-workers=2 -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"` ile aynı clean/debug/release/unit/lint görevleri çalıştırıldı. Bu kontrol aynı makinenin dependency cache/SDK/JBR'sini kullanır; fresh Linux/JDK 17 GitHub checkout kanıtı değildir.

CI workflow oluşturuldu; Action commit'leri resmî repo v4 tag'larından salt-okunur doğrulanıp SHA ile sabitlendi. `contents:read`, persisted checkout credentials kapalı, signing secret yok, 14 gün yalnız test/lint raporları upload. Unit XML kapısı ≥24 gerçek test ve sıfır failure/error/skip gerektirir. Ubuntu 24.04 / Temurin 17 / SDK 34 referansı. `actionlint v1.7.12` resmî release SHA256 doğrulanarak ignored `.baseline/actionlint` içinde çalıştırıldı: **0 diagnostic / exit 0**. ShellCheck/Pyflakes bu makinede kurulmadığından bu iki ek lint kapalı; workflow structure/expression kontrolü yapılmıştır. Yerel Python/Node YAML parser paketlerinin bulunmaması ayrıca gizlenmedi; actionlint gerçek YAML/workflow kontrolünü sağlamıştır.

Bu rapordaki ilk doğrulama anında workflow henüz GitHub'a gönderilmemiş/dispatch edilmemişti. Branch protection/required checks veya GitHub Actions repo ayarlarına yazma yapılmadı. Remote CI'nin bu tarihteki baseline durumu **PARTIAL**; publish sonrası ilgili commit'in gerçek GitHub run sonucu ayrıca kontrol edilmelidir.

## Security / lisans / artifact kontrolü

Root ignore: `.gradle/.kotlin`, bütün build dizinleri, IDE, local.properties, .baseline, heap/profiling dumps/log, private signing/credential yaygın dosya adları. `git check-ignore` ile dump/snapshot/IDE/SDK path/build çıktılarının dışlandığı, Wrapper JAR'ın versionable kaldığı doğrulandı. Ignore, geçmişten silme veya mahremiyet garantisi değildir.

Her iki mevcut commit'te private-key/AWS access id/GitHub token/Google API-key yüksek güvenilirlikli desen taraması **0 dosya eşleşmesi**. Tarihçede key/keystore/dump/local SDK config track edilmiş dosya tespit edilmedi. Credential içeriği sohbet veya dokümana basılmadı. Bu sonuç kapsamlı entropi/secret scanner veya üçüncü taraf secret revocation denetimi değildir.

Mevcut README'nin MIT beyanı, depoda LICENSE bulunmadığından doğrulanmış kaynak hak sözleşmesi sayılmadı. Yeni lisans keyfi olarak atanmadı; eski ekip atıfları korundu. Generator/fixture CC0 bildirimi, mevcut app source lisansını değiştirmez. Owner/hak sahipleri source lisansını ve dependency notices politikasını netleştirmelidir.

## Faz 0 acceptance — tek tek

| Kriter | Durum | Kanıt / kalan iş |
|---|---|---|
| Gerçek repo/remote/branch/HEAD/geçmiş ilişkisinin doğrulanması | PASS | Eski checkout ve GitHub HEAD aynı; standalone metadata, fsck; dört yerel fark korunur |
| Mevcut kaynak snapshot/checkpoint | PASS | 82-entry SHA doğrulanmış ZIP + complete-history bundle; 72 main dosyası aynı |
| Eksiksiz Wrapper JAR/Unix/bat/checksum | PASS | Gradle 8.7 generator, official SHA, Wrapper build |
| Root ignore; dumps/IDE/generated/secret artifact kaynak dışı | PASS | check-ignore; beş dump yerinde, silme yok; CI upload allowlist |
| Desteklenen JDK/SDK/Gradle/AGP ve build/run/test/lint runbook | PASS | README; bytecode 17, local JBR21 / CI17 ayrımı açık |
| Temiz kaynak kopyasında Wrapper debug/release | PARTIAL | Ana kök ve SDK config'siz/build cache'siz izole kaynak başarılı; JDK17/Linux fresh CI henüz run yok |
| Null stream/cache transition/selection/deletion baseline | PASS | Real hasher/analyzer/VM + fake; dört suite 24 test çalıştı |
| Test görevi NO-SOURCE değil | PASS | XML: 24, 0 failure/error/skip; CI test sayısı kapısı |
| Hakları açık küçük fotoğraf fixture seti | PASS | 3 deterministic synthetic PNG + SHA manifest + CC0 generator/README |
| Kaynak/fixture lisansları belgeli | PARTIAL | Fixture hakları açık; source README MIT beyanı/LICENSE eksik, owner doğrulaması gerekli |
| Wrapper clean/debug build | PASS | İlk root run successful; final izole kayıt ayrıca |
| Release build | PASS | Minified unsigned AAB; signing/device install bu fazın kanıtı değil |
| Unit test | PASS | Gerçek 24 test, tümü geçti; bilinen hatalar açıkça karakterize edildi |
| Lint | PASS | Task successful, errors 0; warning'ler korunur, final sayı son kayıtta |
| Debug/unit/lint/release başlangıç CI | PARTIAL | Workflow + actionlint başarılı; commit/push yok, GitHub run/JDK17 doğrulanmadı |
| 10k/20k cihaz/veri/bütçe planı | PASS | BENCHMARK_PLAN; ölçüm olmayan öneri limitler ve süre bütçesi karar kapısı açık |
| En az bir cihaz / gerçek büyük galeri ölçümü | BLOCKED | `adb devices` boş; fiziksel cihaz/set yok. Ölçüm Faz 4'e bırakıldı, uydurma rakam yok |
| Gerçek kişisel galeride destructive QA yapılmaması | PASS | Sadece JVM mocks/fakes + synthetic fixture; adb install/delete yapılmadı |
| Faz sınırları, kullanıcı dosyalarının korunması | PASS | 72 main dosyası snapshot ile aynı; dört önceden farklı production dosyası, eski HEAD build engelini ve eksik import'ları gidermiş mevcut haliyle commit'e alınır; Faz 0 bunları değiştirmedi |

**Genel kapanış: PARTIAL.** Faz 0 yerel mühendislik altyapısı kurulmuştur; CI gerçek çalıştırma, kaynak lisansı doğrulama ve cihaz/veri hazırlığı tam kabul için açıktır. Bu durum production-ready veya Faz 1'e otomatik geçiş izni değildir.

## Owner tarafından sonraki manuel kararlar

1. Dört önceden mevcut production farkının commit'e neden girdiği yukarıda kayıtlıdır. Bu değişiklikler Faz 0 sırasında yazılmadı ve kaynak snapshot ile birebir aynı kaldı.
2. Kaynak kod lisansı/hak sahipleriyle MIT beyanı ve gerçek LICENSE konusu doğrulanmalı. Bu belirsizlik source/fixture lisansları tam kabulünü engeller; test/build altyapısını kullanmayı engellemez.
3. Faz 0 commit'i yayımlandığında ilk JDK17/Linux GitHub Actions run sonucunu kontrol edin. Gerekli branch protection kararı ayrıca owner işidir; bu rapor hazırlanırken CI'nin remote yeşil olduğuna dair kanıt yoktu.
4. Benchmark için izole fiziksel cihaz ve rights-safe 10k/20k arşiv ayırın; Faz 4'te ölçülmüş süre/bellek budget'ı kesinleştirilsin. Cihaz olmadan unit testlerle Faz 1 hazırlığı mümkün, cihaz doğrulaması tamamlanmış sayılamaz.
5. Önce Faz 0 sonuçlarını onaylayın; sonraki faz yalnız yeni talimatla başlayacak. Kritik veri kaybı riskleri aynen açıktır.

## Son doğrulama kaydı

İzole kaynak export'unda **BUILD SUCCESSFUL in 7m 21s**, 109 actionable task / 108 executed / 1 up-to-date; Gradle build cache kapalı. Debug APK 19.122.368 byte, unsigned release AAB 4.037.384 byte. İlk AAB ile byte boyutu farkı, yeniden derleme/minify çıktısıdır; byte-for-byte reproducible build garantisi verilmez. Üretim kaynakları ve runtime dependency sürümleri aynıdır.

İzole export'ta dört suite **24 test, 0 failure/error/skip**. Final catalog config lint: **0 error / 56 warning**: AndroidGradlePluginVersion 3, GradleDependency 48, ModifierParameter 2, MonochromeLauncherIcon 2, SelectedPhotoAccess 1. Önceki audit'teki toplam warning sayısıyla aynıdır; dependency update önerilerinin tam içeriği repo metadata/tarihine göre değişebilir. UseTomlInstead uyarıları test alias'ları catalog'a taşınınca kalmamıştır; suppression yapılmadı.

Fixture'ların PNG chunk CRC, zlib decode ve 64×64 boyutları ayrıca Python standart kütüphanesiyle doğrulandı. `actionlint` exit 0; Git index'te staged diff yok. Wrapper JAR ignored değil (`check-ignore` exit 1); private/local artifacts ignored. Desktop checkout işlem sonunda hâlâ temiz; mevcut root HEAD aynı commit'te. Snapshot'taki 72 production dosyası değişmedi.

Ana kökte catalog sonrası son `:app:testDebugUnitTest :app:lintDebug` kontrolü: **BUILD SUCCESSFUL in 2m 34s**, 40 actionable task / 39 executed / 1 from cache; **24 test, 0 failure/error/skip; lint 0 error / 56 warning**. Ana `app/build` raporları böylece final config ile uyumludur.

Kanıtlar yerel ignored snapshot klasöründe: `wrapper-build.log`, `clean-export-build.log`, final `final-root-check.log`; test/lint HTML/XML ana `app/build` ve izole export altında. Bu log/çıktıları version control'e almak gerekli değildir. Acceptance toplamı **15 PASS / 3 PARTIAL / 1 BLOCKED**; PARTIAL/cihaz eksikleri yukarıdaki tabloda açıklanmıştır.

**Yayın öncesi commit adayı:** İlk yalnız eski GitHub production kaynaklarını içeren index export'u, eksik `ic_launcher_round` yüzünden `:app:processDebugResources` aşamasında başarısız oldu. Dört önceden mevcut ve SHA ile korunan production farkı eklendikten sonra Git index'indeki **100 dosya** izole export ile blob düzeyinde eşleşti. Bu nihai adayda `--no-build-cache` ile `clean :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :app:lintDebug` **BUILD SUCCESSFUL in 12m 40s** (109/109 task executed); **24 test, 0 failure/error/skip; lint 0 error / 56 warning**. Bu kontrol eski GitHub sürümünü değil commit'e girecek gerçek uygulama kaynaklarını kapsar. Yerel ignored kanıt: `final-commit-candidate-build.log` ve aynı klasördeki `commit-candidate/app/build` raporları. Release signing ve gerçek cihaz doğrulaması hâlâ açık kalır.
