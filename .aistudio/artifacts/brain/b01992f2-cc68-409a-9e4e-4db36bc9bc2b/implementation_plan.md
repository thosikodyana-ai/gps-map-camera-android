# Kalibrasi Presisi Stamp Overlay (Live Viewfinder & BitmapStampRenderer)

Pembaruan ini menyelaraskan **Single Source of Truth** antara tampilan *Live Camera Viewfinder* (Jetpack Compose) dan hasil render foto (*BitmapStampRenderer* & *MapTileFetcher*) agar 100% identik dengan acuan **GPS Map Camera** asli tanpa ruang kosong hitam di bagian bawah kartu maupun peta yang lonjong.

## User Review & Keputusan Kritis

> [!IMPORTANT]
> Seluruh parameter ukuran, warna, dan rasio di bawah ini dikunci sesuai spesifikasi presisi Anda:
> - **Mini-Map Bujur Sangkar 1:1 Murni:** `mapWidth = totalStampWidth * 0.28f`, `mapHeight = mapWidth`, dan `cardHeight = mapHeight`.
> - **Header Wilayah 2 Baris Eksplisit:** Baris 1 memuat `"[Kecamatan/Kota], [Provinsi],"` dan Baris 2 memuat `"Indonesia 🇮🇩"`.
> - **Distribusi Vertikal Seimbang:** Teks didistribusikan merata dari batas padding atas hingga batas padding bawah kartu (`paddingY` setara `8.dp`) sehingga tidak menumpuk di atas dan tidak menyisakan ruang kosong hitam di bawah.
> - **Logo Google Flat & Pin Proporsional:** Pin peta berukuran `mapHeight * 0.25f` tepat di titik tengah peta, serta teks `"Google"` putih bersih (*flat*, tanpa *outline/stroke* kasar) dengan margin bawah `4.dp`.

---

## 1. Overview & Konsep Utama

- **Live Camera Viewfinder (`LiveStampOverlayCard`):** Menggunakan `wrapContentHeight()` dengan padding dalam `6.dp`, ukuran font yang jelas (`13.sp` Header Bold, `10.sp` Alamat, `9.5.sp` Koordinat & Waktu, `8.5.sp` Note), serta kotak Mini-Map persegi `1:1` (`80.dp` / mengikuti tinggi kartu) dengan *corner radius* `8.dp`.
- **Bitmap Stamp Renderer (`BitmapStampRenderer`):** Mengunci proporsi kotak peta persegi `1:1` (`28%` dari lebar total stamp), tinggi kartu teks kanan sama persis dengan tinggi peta (`cardHeight = mapHeight`), skala font proporsional (`0.052f`, `0.034f`, `0.030f`, `0.028f`), dan distribusi jarak vertikal otomatis dari atas ke bawah kartu.
- **Mini-Map Generator (`MapTileFetcher`):** Memperbarui gambar pin lokasi (`25%` tinggi peta di titik tengah) dan teks `"Google"` putih bersih tanpa *stroke* tebal di bagian bawah peta.

---

## 2. Desain Visual & Tata Letak (Single Source of Truth)

### A. Live Camera Viewfinder (Jetpack Compose UI)
- **Dimensi Kartu & Padding:**
  - Kartu teks menggunakan `wrapContentHeight()` yang memeluk konten teks secara rapat dengan padding dalam `6.dp` (atas, bawah, kiri, kanan).
  - Sudut lengkung (*corner radius*) pada kotak peta dan kartu teks diseragamkan menjadi `8.dp`.
- **Skala Tipografi & Warna:**
  - **Judul Wilayah (Header 2 Baris):** `13.sp`, `FontWeight.Bold`, warna Putih `#FFFFFF`, `maxLines = 2`.
  - **Detail Alamat & Plus Code:** `10.sp`, `FontWeight.Normal`, warna Putih `#E5E5E5`, `lineHeight = 12.sp`.
  - **Koordinat Lat/Long:** `9.5.sp`, warna Putih `#E5E5E5`.
  - **Tanggal & Waktu:** `9.5.sp`, warna Putih `#E5E5E5`.
  - **Note Watermark:** `8.5.sp`, warna Abu Terang `#CCCCCC`.
- **Kotak Mini-Map Viewfinder:**
  - Persegi `1:1` dengan *Center-Crop* yang tingginya menyatu selaras dengan kartu teks (`80.dp` / `aspectRatio(1f)`).

### B. Hasil Foto Tersimpan (`BitmapStampRenderer` & `MapTileFetcher`)
- **Kunci Mini-Map Bujur Sangkar 1:1:**
  - `mapWidth = totalStampWidth * 0.28f` (pada orientasi *Landscape* disesuaikan proporsional agar tetap persegi `1:1` dan tidak memakan tinggi foto berlebihan).
  - `mapHeight = mapWidth` (wajib bujur sangkar `1:1`).
  - `cardHeight = mapHeight` (tinggi kartu kanan sama persis dengan tinggi peta).
- **Format Header 2 Baris & Skala Font (`infoCardWidth`):**
  - **Baris 1 Header:** `"[Kecamatan/Kota], [Provinsi],"`
  - **Baris 2 Header:** `"Indonesia 🇮🇩"` (bendera digambar tepat setelah kata `"Indonesia"`).
  - **Font Header:** `infoCardWidth * 0.052f` (Bold, `#FFFFFF`).
  - **Font Alamat & Plus Code:** `infoCardWidth * 0.034f` (Regular, `#E2E2E2`, *wrap* 2–3 baris rapat).
  - **Font Koordinat & Jam:** `infoCardWidth * 0.030f` (Regular, `#E2E2E2`).
  - **Font Note Watermark:** `infoCardWidth * 0.028f` (Regular, `#CCCCCC`).
- **Distribusi Vertikal Seimbang:**
  - Menghitung `totalTextHeight` dari seluruh blok teks (Header 2 baris + Alamat + Lat/Long + Waktu + Note).
  - Membagi sisa ruang vertikal (`availableHeight - totalTextHeight`) secara merata di antara setiap blok baris mulai dari `cardTop + paddingY` hingga `cardBottom - paddingY`, sehingga baris terakhir (*Note*) berada rapi di dekat batas bawah kartu tanpa menyisakan area kosong hitam.
- **Pin Peta & Logo Google Bersih:**
  - **Pin Lokasi:** Tinggi pin `mapHeight * 0.25f` diletakkan persis di tengah kotak peta.
  - **Logo Google:** Teks `"Google"` putih bersih (*flat*, tanpa *outline/stroke* kasar) di bagian bawah tengah peta dengan margin bawah setara `4.dp`.

---

## 3. Arsitektur Teknis & Alur Render

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        LocationMetadata (Model)                          │
│  • template1HeaderLine1: "[Kecamatan/Kota], [Provinsi],"                 │
│  • template1HeaderLine2: "[Negara]" + Bendera 🇮🇩                         │
│  • template1FullAddressWithPlusCode: "427p+2cv, Jl. ..., Jawa Tengah..." │
└───────────────────┬──────────────────────────────────┬───────────────────┘
                    │                                  │
                    ▼                                  ▼
┌───────────────────────────────────────┐  ┌───────────────────────────────┐
│   LiveStampOverlayCard (Compose UI)   │  │  BitmapStampRenderer (Canvas) │
│  • wrapContentHeight(), pad = 6.dp    │  │  • mapWidth = stampW * 0.28f  │
│  • Header 2 baris: 13.sp Bold #FFFFFF │  │  • mapHeight = mapWidth (1:1) │
│  • Alamat: 10.sp #E5E5E5 (lh 12.sp)   │  │  • cardHeight = mapHeight     │
│  • Lat/Long & Jam: 9.5.sp #E5E5E5     │  │  • Font: 0.052 / 0.034 /      │
│  • Note: 8.5.sp #CCCCCC               │  │          0.030 / 0.028        │
│  • Mini-Map 1:1, cornerRadius = 8.dp  │  │  • Balanced vertical spacing  │
└───────────────────────────────────────┘  └───────────────┬───────────────┘
                                                           │
                                                           ▼
                                           ┌───────────────────────────────┐
                                           │        MapTileFetcher         │
                                           │  • Pin: 0.25f * mapHeight     │
                                           │  • Logo "Google": Flat White, │
                                           │    margin bawah 4.dp          │
                                           └───────────────────────────────┘
```
