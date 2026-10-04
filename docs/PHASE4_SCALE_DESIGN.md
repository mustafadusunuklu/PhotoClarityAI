# Faz 4 — Ölçek ve doğruluk kontratı

Başlangıç checkpoint: `691f9a9386b3d51d82076c7ab29dbf4a09e08ef2`, 2026-10-04. Faz 3 CI başarılıdır; önceki PARTIAL kabul ve açık cihaz/izin/restore/UX maddeleri korunur.

## Uygulama kararları

- Exact adayları aynı pozitif byte boyutuyla daraltılır; hash eşitliği boyutla birlikte değerlendirilir. Tekil boyutlu dosyada full-file hash gerekli değildir; sonraki taramada aynı boyutlu yeni dosya gelirse eksik crypto alanı tamamlanır.
- Analiz kimliği URI'dir; farklı volume'larda aynı Long ID ayrı fotoğraflardır. Eski UI Long ID belirsizliği güvenli seçimde kapalı kalır.
- Cache version, generation/version ve metadata ile doğrulanır. Eski cache satırları migration'da korunur ancak yeni pipeline'da hit sayılmaz. Ayar değişimi sadece gereken eksik özellikleri tamamlar.
- Metadata cursor'ı IO üzerinde 256'lık sayfalar üretir ve gerçek `minFileSizeBytes` ayarını uygular. Nihai katalog/index hâlâ O(n) bellektedir; persisted incremental generation-delta veya tüm sonuçlar için Room Paging uygulanmış değildir.
- Restored/pre-delete metadata doğrulaması ve issued trash doğrulaması collection/volume kapsamında 256 URI/ID batch'leriyle yapılır. Eksik/null/exception durumları başarı sayılmaz; frozen seçim dışında URI doğrulanmaz. Sistem onay batch sınırı 2.000 olarak korunur.
- Frozen journal fotoğraf payload'ı ilk istekte yazılır. Aynı request'in sonraki checkpoint'leri transaction içinde sadece monoton removed/failed ve geçerli issued işaretlerini günceller. Request değişimi, immutable metadata ve durable-before-effect kontratı korunur.
- Dört worker, 256 satırlık cache read/write batch'leri; fotoğraf başına tek bounded decode, borrowed bitmap üzerinden visual hash ve sharpness. Decode uzun kenarı 256, bitmap pixel bütçesi 65.536; EXIF orientation normalize edilir.
- pHash separable truncated DCT ile yalnız kullanılan frekansları hesaplar. DC çıkarıldığından **63 aktif bit** açıkça kullanılır; aHash/dHash 64 bit. Decode/DCT değişimi cache ve persisted findings version'ına bağlıdır.
- Dört disjoint bit block ile exact Hamming radius search: radius-r eşleşmenin en az bir block'u floor(r/4) içinde bulunur. Wide radius'ta exhaustive fallback vardır; yaklaşık aday kaybı yoktur. Exhaustive oracle karşılaştırması ayrı kabul testidir.
- Görsel gruplar kalite sıralı keeper yıldızlarıdır: her üye keeper ile eşik koşulunu sağlar. A-B/B-C zinciri A-C'yi zorunlu grup yapmaz. Grup skoru tüm çiftlerin matematiksel ortalamasıdır; tüm çiftlerin eşik üstü olduğu vaadi değildir. Ortalama O(bits*n) hesaplanır. Sabit/boş bilgi hash'leri görsel silme adayı oluşturmaz.
- Büyük grup sonuçları mevcut dikey akışta üyeler bazında lazy üretilir. Faz 5'te seçenekleri tekrar inceleyerek yapılacak UX/UI tasarım kararı korunur.

## Ölçüm ve kabul sınırları

Pure index/DCT CPU ve 20k synthetic metadata stress sonuçları; gerçek Android decoder/Room/analyzer ölçümleri; gerçek 20k JPEG/HEIC galeri ve fiziksel cihaz bütçeleri ayrı kanıtlardır. Emülatör veya cached-hash stress, fiziksel cihaz cold gallery sonucu yerine sayılmaz. Bütün setler sentetik ve scoped cleanup ile çalışır; kişisel galeride toplu işlem yapılmaz. CPU iptal hedefi ≤2 s; native decode/provider bloklaması ayrıca ölçülür, varsayılarak kapatılmaz. Süre/PSS/frame/precision-recall sonuçları final raporda kaydedilir; fiziksel cihaz yoksa bu kapılar açık kalır.

Kaynaklar: [BitmapFactory sample semantics](https://developer.android.com/reference/android/graphics/BitmapFactory.Options), [EXIF orientation](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface), [Room migration](https://developer.android.com/training/data-storage/room/migrating-db-versions).
