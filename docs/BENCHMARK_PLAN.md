# 10k/20k benchmark planı — Faz 0

Bu belge bir ölçüm sonucu değildir. Henüz test cihazı ve büyük fixture arşivi hazırlanmadı; aşağıdaki sayısal bütçeler ilk mühendislik hedefidir, bugünkü uygulamanın performansı olarak sunulamaz. Faz 4 başında cihaz/set kimliğiyle cold baseline toplanıp bütçeler gerekçeli olarak kesinleştirilmelidir. Faz 0'ın küçük JVM fixture'ları bu benchmark yerine geçmez.

## Cihaz ve koşullar

Birincil: fiziksel 4 GB RAM orta/alt sınıf ARM64, API 29 ve/veya 34, internal flash. İkincil: fiziksel 8 GB+ ARM64 API 34; Faz 2 sonrası güncel API 35/36. API 26 minimum uyumluluk ayrı smoke matrisi; 16 KB sayfa boyutu ayrı release testi. Destekleniyorsa SD/removable volume ayrı koşu. Üretici/model/SoC/RAM/Android build/sayfa boyutu/boş alan/depoma türü kaydedilmeli; emülatör süreleri gerçek cihaz budget'ının yerine geçmez.

USB/şarj, güç tasarrufu, uygulamanın foreground/background durumu, oda sıcaklığı ve termal durumu sabitlenmeli. Network kapalı cihaz içi analiz; kurulum için network ayrı. Her cold/warm koşulda en az 3 tekrar; median/p95 ile ölçüm örnek sayısı raporlanmalı. 3 koşudan anlamlı p95 çıkarılamaz: p95 stage/frame örnekleri için, koşu süresi için median/min/max kullanın.

## Veri seti kontratı

İzole test cihazında 1k smoke, 10k ve 20k dosya. JDK/sentetik generator veya kullanım hakkı belgeli anonim test fotoğrafları; kişisel galeri/kullanıcı hesabı yok. PNG-only benchmark gerçek JPEG decode/I/O iş yükü sayılmaz. JPEG ağırlıklı 2–12 MP, ~0.2–8 MiB dağılımı; PNG ~%5, cihaz destekliyorsa HEIC ~%5. Hak dosyası, generator sürümü/seed, her dosyanın SHA256/byte/dimensions/MIME/bucket/dateTaken ve ground truth aile kimliği manifestte tutulmalı; EXIF/GPS sentetik olmalı. Unsupported/truncated/deleted/revoked/null stream örnekleri ayrı doğruluk setidir, süre kıyasına gizlice katılmamalıdır.

Ana dağılım: %70 bağımsız içerik, %15 exact-copy üyeleri (2–10 kişilik aileler), %10 kontrollü near varyantlar, %5 sentetik burst üyeleri. Bu oranlar fotoğraf sayısına göredir; overlap varsa manifestte açıkça kaydedin. Tek büyük grup (1k üye), 1k+ küçük grup, long transitive chain ve unrelated low-quality stres setleri ayrıca hazırlanır; R02 kapanmadan silme önerisi doğruluğuna olumlu kabul verilmez. 20k set ve manifest hash'i bütün karşılaştırmalarda aynı kalmalı.

Cold: uygulama test cache'i boş (yalnız izole test cihazının uygulama verisi), medya indeksi hazır. Warm: URI/time/size sabit; aynı ayar ve cache. Ek koşular: p→a→d→SHA, exact/visual off→on, %1 medya değişimi, izin daralması, yarıda iptal, background/process kill/resume. Bunlar sonraki fazların correctness/state gereksinimleriyle birlikte ölçülür.

## Metrikler ve önerilen bütçe

| Metrik | Ölçüm / başlangıç hedefi | Uygulama kapısı |
|---|---|---|
| 20k tam tarama | OOM/ANR/crash olmadan tamamlanma; süre bütçesi sabit referans set/cihaz olmadan kesinleştirilemez | Faz 4 ilk ölçümünde median ve max dakika kaydı; cihaz/set bazlı süre limiti aynı raporda kararlaştırılmadan Faz 4 kapanmaz |
| Bellek | 4 GB cihazda app peak PSS ≤256 MiB **öneri**, bitmap/native/Java ayrı ve GC sıklığı | Faz 4 ölçüm sonrası onaylı regression limiti; bugün ölçülmedi |
| İptal | UI geri bildirimi ≤100 ms, worker/hash/compare durması ≤2 s hedef | Hash okuma, DCT ve compare sırasında ayrı ölçüm |
| Warm I/O | Değişmeyen yeterli cache'te full-file hash okuması 0 byte | Metadata/DB/thumbnail I/O ayrı sayılır; hash completeness doğruluğu önce |
| UI | 60 Hz referansta p95 frame ≤32 ms; jank < %5 **öneri** | 1k grup/1k üyeli grup scroll ve selection; Faz 5 tasarım kapısı |
| Doğruluk | Exact gruplar ground truth ile tam eşleşir; okunamayan medya başarılı hash sayılmaz | R03/R04 kapanınca doğrulama; near precision/recall threshold kalibrasyonu ayrı |
| State | İşlem/izin/process death sonrası kayıp veya yanlış oturum yok | Faz 3 kontratı; bugün garanti yok |

Stage süreleri: MediaStore query, metadata, stream read/hash, bitmap decode, DCT, pair/candidate comparison, grouping, DAO, ilk sonuç render. CPU time/usage, thread sayısı, bytes read, Java/native heap/PSS peak, GC, ANR, termal/batarya ve cancel latency kaydedin. Aynı state/ayarlarla, minified release ve debug farkını ayırın. Microbenchmark warm cache'i cold olarak raporlamayın.

Perfetto/system trace + Android Studio memory/CPU profiler, `adb shell dumpsys meminfo`, `dumpsys gfxinfo ... framestats`, izinli device metrics ve Android benchmark kütüphaneleri değerlendirilebilir. Ayrıntılı pipeline instrumentation Faz 4 işidir; Faz 0 production logger/profiler eklemez. Bellek dump'ı yalnız sentetik cihazda bile yerel/özel artifact sayılır, CI'ye veya Git'e gönderilmez.

Her rapor: kaynak revision/dirty diff hash, fixture manifest hash, cihaz kimliği, JDK/build/ABI, koşul/ayar, sample sayısı, stage süreleri, memory/I/O/frame/cancel, hatalar ve karşılaştırma bütçesi. Destructive delete testi fake veya izole sentetik galeriyle; uygulamanın mevcut geri dönüşüm ekranına güvenerek geri alınabilirlik varsaymayın.
