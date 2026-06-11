# 📸 PhotoClarity AI

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue?style=for-the-badge&logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-red?style=for-the-badge" />
</p>

> **PhotoClarity AI**, Android cihazınızdaki fotoğrafları yapay zeka ile analiz eden, kopya ve benzer fotoğrafları tespit eden, depolama alanı geri kazanımı sağlayan akıllı bir fotoğraf yönetim uygulamasıdır.

---

## 🚀 Özellikler

- 🔍 **Birebir Kopya Tespiti** — MD5 hash ile aynı dosyaları bulur
- 🎨 **Görsel Benzerlik Analizi** — Perceptual, Average ve Difference Hash algoritmaları
- 📷 **Seri Çekim Gruplandırması** — Burst shot fotoğrafları otomatik gruplar
- 🌫️ **Bulanık Fotoğraf Tespiti** — Kalite skoru ile düşük kaliteli görüntüler
- 💾 **Depolama Analizi** — Kullanılan/boş alan görselleştirmesi
- 🤖 **Akıllı Öneriler** — "Önerilen Sakla" fotoğrafını otomatik belirler
- 🔒 **Tamamen Cihaz Üzerinde** — İnternet bağlantısı gerektirmez, gizlilik korumalı

---

## 📱 Ekran Görüntüleri

| Dashboard | Tarama | Sonuçlar |
|-----------|--------|----------|
| Depolama analizi ve hızlı erişim | Gerçek zamanlı tarama | Bulunan gruplar ve öneriler |

---

## 🛠️ Kullanılan Teknolojiler

| Teknoloji | Açıklama |
|-----------|----------|
| **Kotlin** | Birincil programlama dili |
| **Jetpack Compose** | Modern declarative UI |
| **Material Design 3** | UI tasarım sistemi |
| **Hilt (Dagger)** | Dependency Injection |
| **Jetpack Navigation** | Ekranlar arası geçiş |
| **Room Database** | Hash önbellekleme |
| **DataStore** | Kullanıcı ayarları |
| **Coroutines + Flow** | Asenkron işlemler |
| **Coil** | Fotoğraf yükleme |
| **MediaStore API** | Galeri erişimi |

---

## 🏗️ Proje Mimarisi

```
MVVM + Clean Architecture
├── core/          → Hash algoritmaları, analiz motoru
├── data/          → Room DB, DataStore, Repository
├── domain/        → Modeller, interface'ler
├── di/            → Hilt modülleri
└── ui/            → Compose ekranlar, ViewModel'ler
```

---

## 📂 Kurulum

1. Projeyi klonlayın:
```bash
git clone https://github.com/mustafadusunuklu/PhotoClarityAI.git
```

2. Android Studio'da açın

3. `local.properties` dosyasına SDK yolunu ekleyin:
```
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
```

4. **Run ▶** butonuna basın

**Minimum Gereksinimler:**
- Android 8.0 (API 26) ve üzeri
- Android Studio Hedgehog veya üzeri

---

## 👥 Takım Üyeleri

| İsim | Görev | GitHub |
|------|-------|--------|
| **Mustafa Düşünüklü** | Navigasyon Mimarisi & Ana Ekranlar | [@mustafadusunuklu](https://github.com/mustafadusunuklu) |
| **Mehmet Arda Öztürk** | Veri Katmanı & Repository | [@ardaoztrk2](https://github.com/ardaoztrk2) |
| **Hakan Arslan** | Çekirdek Analiz Motoru & Hash Algoritmaları | [@Hakanars](https://github.com/Hakanars) |
| **Gülizar Yıldırım** | UI Bileşenleri & Ekranlar & Tasarım Sistemi | — |

---

## 📋 Ekran Listesi

- 🏠 Dashboard (Ana Panel)
- 📷 Fotoğraflar
- 🔍 Tarama
- 📊 Sonuçlar & Grup Detayı
- 👤 Profil
- ⚙️ Ayarlar
- ⭐ Favoriler
- 🕐 Tarama Geçmişi
- 💡 Akıllı Öneriler
- 🗑️ Geri Dönüşüm Kutusu
- ℹ️ Hakkında

---

## 📄 Lisans

Bu proje MIT lisansı ile lisanslanmıştır.

---

<p align="center">
  Tüm işlemler cihaz üzerinde gerçekleştirilir 🔒 &nbsp;|&nbsp; PhotoClarity AI © 2026
</p>
