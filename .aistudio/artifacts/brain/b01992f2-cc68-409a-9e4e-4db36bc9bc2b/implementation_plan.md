# GPS Map Camera — Perbaikan Proporsi Stamp (Lingkaran Hijau), Optimasi Performa UI Smooth 60 FPS & Fitur Pencarian Tempat di Peta

Pembaruan ini mencakup tiga peningkatan utama sesuai permintaan Anda:
1. **Perbaikan Proporsi Stamp 100% Sesuai Contoh Lingkaran Hijau**: Kotak mini-map dikunci persegi `1:1` dengan *Center-Crop* anti-gepeng, serta ukuran huruf (font) pada kartu kanan diperbesar proporsional terhadap lebar kartu agar memenuhi ruang tanpa area kosong berlebih.
2. **Optimasi Performa agar UI Sangat Ringan & Smooth (60 FPS)**: Menghilangkan *lag/stutter* pada layar kamera, pratinjau foto, dan saat menggeser peta interaktif.
3. **Fitur Pencarian Lokasi/Tempat (`Search Bar`) pada Peta Manual**: Memungkinkan pengguna mengetik nama tempat, jalan, kelurahan, kecamatan, kota, atau koordinat langsung di halaman peta untuk langsung melompat ke titik lokasi yang dicari.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Ringkasan keputusan desain dan teknis yang akan diimplementasikan:

- **Keputusan 1 — Kotak Mini-Map Persegi 1:1 Anti-Gepeng & Font Memenuhi Kartu (Lingkaran Hijau)**:
  - Lebar kartu mini-map kiri dikunci `1:1` mengikuti tinggi kartu kanan (`mapCardWidth = cardHeight`) dengan pemotongan *Center-Crop* agar peta, pin merah, dan tulisan `Google` tidak pernah gepeng baik pada foto Portrait maupun Landscape.
  - Ukuran font judul lokasi, alamat + Plus Code (tanpa duplikasi kode), koordinat, jam, catatan, dan bar sensor dihitung dari lebar kartu kanan (`infoCardWidth`) sehingga memenuhi ruang kartu secara padat dan seimbang.
- **Keputusan 2 — Optimasi UI Ringan & Smooth (Eliminasi Rekomposisi Berlebih)**:
  - **Throttling Sensor Kompas & Magnetometer**: Membatasi pembaruan UI dari sensor maksimal setiap `400ms` (dan hanya saat nilai berubah signifikan) sehingga layar kamera tidak melakukan *re-render* 60 kali per detik.
  - **Optimasi Drag Peta Interaktif**: Mengubah pemicu pengunduhan tile peta agar hanya berjalan ketika indeks kotak tile (`tileX`, `tileY`, `zoom`) berubah—bukan pada setiap piksel geseran jari—serta menyimpan `ImageBitmap` di memori agar kanvas peta bergerak sangat mulus 60 FPS.
  - **Coalescing Render Pratinjau**: Membatalkan *job* render sebelumnya saat pengguna menggeser slider transparansi atau mengganti opsi dengan cepat, serta membatasi dimensi bitmap kerja agar proses *baking* stamp selesai instan (`< 25ms`).
- **Keputusan 3 — Fitur Search Tempat & Alamat pada Peta Interaktif**:
  - Menambahkan kolom pencarian (**"Cari nama jalan, tempat, desa, kota, atau koordinat..."**) di bagian atas modal Peta Interaktif.
  - Mendukung pencarian hibrida menggunakan **Android Forward Geocoder** dan **OpenStreetMap Nominatim Search API** (serta deteksi input koordinat `lat, lon` langsung), menampilkan daftar hasil pencarian yang dapat diketuk untuk langsung memindahkan peta dan penanda lokasi ke tempat tersebut.

---

## 1. Overview & Core Concept

- **Masalah yang Diselesaikan**:
  1. **Visual Stamp**: Kotak mini-map sebelumnya tertarik mengikuti rasio gambar dan ukuran font terlalu kecil pada foto Landscape/4:3.
  2. **Performa UI**: Sensor magnetometer/akselerometer yang memancarkan data puluhan kali per detik dan pengunduhan tile peta pada setiap gerakan jari membuat *main thread* sibuk.
  3. **Navigasi Peta Manual**: Menggeser peta secara manual ke kota atau jalan yang jauh membutuhkan waktu jika tidak ada fitur pencarian nama tempat.
- **Nilai Utama Pembaruan**:
  - Hasil foto ber-stamp tampil presisi seperti contoh referensi lingkaran hijau, aplikasi terasa ringan dan responsif di semua layar, dan pemilihan lokasi manual di peta menjadi jauh lebih cepat berkat kolom pencarian lokasi.

---

## 2. User Experience & Visual Design

### A. Tata Letak Stamp Proporsional (Contoh Lingkaran Hijau)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                  TATA LETAK PROPORSIONAL (LINGKARAN HIJAU)                 │
│                                                           ┌──────────────┐ │
│                                                           │📷 GPS Map Cam│ │
│  ┌────────────────────┐  ┌────────────────────────────────┴──────────────┤ │
│  │                    │  │ Kecamatan Slawi, Jawa Tengah,                 │ │
│  │                    │  │ Indonesia 🇮🇩                                  │ │
│  │        (📍)        │  │ 24cr+m95, Procot, Kec. Slawi, Kabupaten       │ │
│  │                    │  │ Tegal, Jawa Tengah 52411, Indonesia           │ │
│  │                    │  │ Lat -6.978291° Long 109.140948°               │ │
│  │      Google        │  │ Jumat, 30/10/2026 10:18 PM GMT +07:00         │ │
│  │                    │  │ Note : Captured by GPS Map Camera             │ │
│  └────────────────────┘  └───────────────────────────────────────────────┘ │
│   ▲ Persegi 1:1 Presisi   ▲ Font diskalakan dari lebar kartu kanan         │
│     (Center-Crop,           sehingga teks memenuhi ruang horizontal &      │
│      Pin & Google tidak     vertikal tanpa area kosong berlebih            │
│      pernah gepeng)                                                        │
└────────────────────────────────────────────────────────────────────────────┘
```

### B. Alur Pencarian Lokasi pada Peta Interaktif (`Interactive Map Search`)

```
┌────────────────────────────────────────────────────────────────────────────┐
│  📍 Pilih Lokasi Manual di Peta                                       [✕]  │
├────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────┬────────┐ │
│  │ 🔍 Cari tempat, jalan, kecamatan, kota, atau -6.88, 109.03  │ [Cari] │ │
│  └──────────────────────────────────────────────────────────────┴────────┘ │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │ 📌 Procot, Kecamatan Slawi, Kabupaten Tegal, Jawa Tengah              │ │
│  │ 📌 Jl. Sapphire Regency, Pulosari, Kec. Brebes, Jawa Tengah           │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│  [Brebes (Ref 1)]  [Moskva (Ref 2)]  [Jakarta Monas]  [Surabaya]           │
├────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────┐                                       ( layers )
│  │ Zoom 16 • Ketuk / Geser  │                                       (  +  ) │
│  └──────────────────────────┘                                       (  -  ) │
│                                   (📍)                              ( 🎯 ) │
│                            [ Peta Interaktif ]                             │
├────────────────────────────────────────────────────────────────────────────┤
│  TITIK LOKASI YANG DIPILIH                             Plus Code: 24cr+m95 │
│  Lat -6.978291° • Long 109.140948°                                         │
│  Procot, Kecamatan Slawi, Kabupaten Tegal, Jawa Tengah 52411, Indonesia    │
│  ┌──────────────────────────┐   ┌────────────────────────────────────────┐ │
│  │          Batal           │   │        ✓ Pilih Lokasi (OK)             │ │
│  └──────────────────────────┘   └────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Pencarian Lokasi Hibrida (Android Geocoder + Nominatim + Koordinat Langsung)**
  - *Pendekatan Terpilih*: Mendukung pencarian teks bebas melalui `Geocoder` bawaan Android dan API pencarian OpenStreetMap Nominatim (`nominatim.openstreetmap.org/search`) dengan detail alamat Bahasa Indonesia, ditambah parser koordinat langsung apabila pengguna mengetik angka `lat, lon`.
  - *Alasan*: Memastikan pencarian jalan/desa/tempat selalu berhasil di semua perangkat Android bahkan tanpa Google Play Services penuh.
- **Keputusan 2: Optimasi Rekomposisi Layar Kamera & Kanvas Peta**
  - *Pendekatan Terpilih*: Menggunakan `distinctUntilChanged` + *time throttling* `400ms` pada aliran sensor kompas/magnetometer, memisahkan state detak jam agar hanya memperbarui teks jam, serta meng-cache `ImageBitmap` tile peta berdasarkan indeks `tileX/tileY` bulat.
  - *Alasan*: Menurunkan beban CPU/GPU secara drastis sehingga animasi buka/tutup dialog, geseran peta, dan *viewfinder* kamera berjalan sangat mulus.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Diagram Arsitektur Optimasi UI & Pencarian Peta

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     UI Performance & Search Pipeline                    │
│                                                                         │
│  [Sensor Magnetometer & Kompas]                                         │
│       │ (Throttle >= 400ms & Delta Threshold)                           │
│       ▼                                                                 │
│  [CameraStampViewModel] ◄── Debounced Preview Baking (Max 1920px)       │
│       │                                                                 │
│       ├──► [BitmapStampRenderer]                                        │
│       │      • Mini-Map Square 1:1 Lock (mapWidth == cardHeight)        │
│       │      • Center-Crop Tile & Sharp Vector Pin / Google Logo        │
│       │      • InfoCardWidth-Proportional Font Scaling (Full Space)     │
│       │                                                                 │
│       └──► [InteractiveMapPickerDialog]                                 │
│              • Search Input + Debounced Forward Geocoding Dropdown      │
│              • Integer Tile-Index LaunchedEffect (Zero Drag Lag)        │
│              • Pre-cached ImageBitmap Tile Map (60 FPS Canvas Draw)     │
└─────────────────────────────────────────────────────────────────────────┘
```

### Rincian Implementasi Teknis

1. **Perbaikan Renderer Stamp (`BitmapStampRenderer` & `LiveStampOverlayCard` & `StampModels`)**:
   - Menghitung ukuran font `titlePaint` dan `bodyPaint` pada Template 1 serta `headerPaint` dan `rowPaint` pada Template 2 sebagai persentase dari lebar kartu informasi (`infoCardWidth`) sehingga teks selalu proporsional dan memenuhi kartu kanan.
   - Mengunci ukuran kartu mini-map menjadi persegi `1:1` (`mapCardWidth = cardHeight`) dan menggambar peta dengan *Center-Crop* sehingga peta tidak pernah gepeng.
   - Membersihkan duplikasi Plus Code pada alamat jika hasil geocoding sudah menyertakan Plus Code di awal kalimat.
2. **Pencarian Lokasi/Tempat (`LocationAndCompassEngine` & `InteractiveMapPickerModal`)**:
   - Menambahkan model `PlaceSearchResult(displayName, latitude, longitude, addressParts)` dan fungsi `searchPlaces(query: String): List<PlaceSearchResult>`.
   - Menambahkan bilah pencarian interaktif di bagian atas `InteractiveMapPickerDialog` dengan tombol Cari, indikator loading, dan daftar hasil pencarian yang ketika dipilih langsung mengarahkan peta & pin ke lokasi tersebut.
3. **Optimasi Performa UI (`LocationAndCompassEngine`, `CameraStampViewModel`, `InteractiveMapPickerModal`)**:
   - Menambahkan pembatas waktu (*throttle* `400ms`) pada `observeCompassAndMagnetic()`.
   - Mengoptimalkan `InteractiveMapPickerDialog` agar penyimpanan tile menggunakan `ImageBitmap` siap pakai dan `LaunchedEffect` hanya berjalan saat koordinat tile bulat (`baseTileX`, `baseTileY`, `zoomLevel`, `isSatelliteLayer`) berubah.
   - Membatalkan *coroutine job* `bakePreviewBitmap` sebelumnya saat ada perubahan berturut-turut dan mengoptimalkan ukuran bitmap kerja agar render pratinjau berjalan instan.
4. **Kompilasi, Pengujian & Pembaruan Rilis GitHub**:
   - Memverifikasi aplikasi dengan `compile_applet` dan unit test, lalu melakukan *commit*, *push* ke GitHub, serta memperbarui APK Release di GitHub Releases.
