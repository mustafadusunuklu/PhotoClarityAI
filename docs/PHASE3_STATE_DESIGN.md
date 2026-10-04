# Faz 3 — Session, lifecycle ve işlem sahipliği

Tarih: 2026-10-04. Faz 2 checkpoint: `68ea759ec2114eb351a66b7fd80cd6301c648c23`.

Bu faz algoritma kalibrasyonu veya sonuç ekranı yeniden tasarımı değildir. Faz 1 keeper/waste, explicit app/system consent, per-URI doğrulama ve trash != reclaimed bytes kontratı korunur. Faz 2 açık OS/OEM/picker/restore/D2D/landscape maddeleri kapatılmaz.

## Kararlar

- Varsayılan kontrat **görünür uygulamada kullanıcı başlatımlı tarama**. Singleton coordinator işi bir ekran ViewModel'inden bağımsız yönetir; rotation/back ve uygulama içi navigation işi çoğaltmaz. Uygulama görünürlüğünü kaybedince kooperatif cancellation; OS process kill sonrası RUNNING session INTERRUPTED olur. Otomatik restart veya background devam sözü verilmez. Yarım hash cache tekrar taramada kullanılabilir; algoritma içi pair-loop checkpoint/resume Faz 4'e aittir. Tamamlanan sonuç, seçim ve geçmiş kalıcıdır. Kullanıcı ayrıca background devam isterse bu kontrat implementation öncesi yeniden değerlendirilir.
- Room **v1→v2 açık migration**, export edilen şemalar ve migration testi. Eski hash cache ve DataStore tercih/istatistikleri korunur; destructive fallback kaldırılır. Session, typed group findings, URI tabanlı media kimliği, seçim ve frozen removal journal kalıcıdır. Bitmap/fotoğraf baytı, GPS ve IntentSender database/SavedState'e konmaz. Backup allowlist değişmez; session/URI journal başka cihaza taşınmaz.
- Application-scoped coordinator + repository StateFlow tek ortak kaynak. UI'nin yaşam döngüsü yalnız collection/presentation içindir. Clock/dispatcher/ID injection; immutable ayar snapshot; aynı anda bir scan ve bir removal, scan/removal birbirini dışlar. Progress collector işi bitince kapanır. Phase counters ile discovered/attempted/failed/matched counters birbirine karıştırılmaz.
- Başlangıçta erişim ve mevcut media metadata doğrulanmadan eski sonuçtan silme açılamaz. Farklı erişim/scope veya değişmiş medya sonuçları STALE yapar; seçim temizlenir. History sayısal session özeti olarak görünür, geçmiş thumbnail'ları başka session'a sızmaz. ID/session/request kimlikleri birbirinin yerine kullanılmaz.
- Silme coordinator'ı frozen snapshot ve request UUID'nin sahibi. Side effect'ten **önce** journal yazılır. IntentSender yalnız bellekte; process recreation'da tekrar launch/delete/remaining-batch otomatik yapılmaz. Sistem trash yalnız daha önce issued batch'in gerçek IS_TRASHED durumuyla reconcile edilir; yok/okunamayan URI başarı sayılmaz. Kalıcı silmede sadece önceden kaydedilmiş başarılar kredilenebilir; crash aralığındaki belirsiz sonuç kullanıcıya yeniden tarama gereksinimi olarak aktarılır.
- Kalıcı silme istatistiği request ID ile DataStore edit içinde atomik/idempotent kredilenir. Aynı request'in tekrar recovery'si çift sayım yapmaz. İşlem zamanındaki ay anahtarı kullanılır; eski aylara yeni ay kredisi yazılmaz. Trash için kredi yok. Journal→stats→terminal checkpoint sırası crash sonrası tekrar güvenlidir.
- Eski `ScanResultHolder` üretim yolundan kaldırılır. Eski testler ortak repository/coordinator kontratına taşınır; assertion gevşetilmez. Eski testler yalnız fake/global helper sayesinde yeşile döndürülmez.

## Kabul ve doğrulama

JVM: duplicate start, cancellation, terminal states, scope invalidation, stale completion, actual counters, selection/keeper, request/batch lock, repeated callback, journal recovery ve stats idempotence. Cihaz: gerçek v1 cache+ayar koruyan migration, repository reopen, completed/empty/error/cancel ayrımı, rotation/back/home/process-kill, selection ve pending-system-trash recovery. Önceki Faz 1–2 gerçek consent/keeper/batch testleri regresyon kapısıdır. Fiziksel cihaz/OEM matrisi ve uzun 20k performans ölçümü bu testlerle kapatılmaz.

Kaynaklar: [Room migration ve export/test gereksinimi](https://developer.android.com/training/data-storage/room/migrating-db-versions), [görünürlüğe bağlı async işin sınırı](https://developer.android.com/develop/background-work/background-tasks), [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).
