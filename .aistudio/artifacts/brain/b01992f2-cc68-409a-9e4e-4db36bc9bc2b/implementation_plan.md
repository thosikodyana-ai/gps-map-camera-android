# GPS Map Camera — Rencana Implementasi: 2 Template Presisi Referensi + Pemilihan Lokasi Manual via Peta Interaktif

Rencana ini menggabungkan dua pembaruan utama:
1. **Penyamaan 100% Template Stamp dengan 2 Gambar Referensi** (*Template 1: Gaya Satelit Brebes* & *Template 2: Gaya Peta Jalan Terang + Sensor µT*).
2. **Fitur Pemilihan Lokasi Manual Menggunakan Peta Interaktif (*Interactive Map Location Picker*)** yang terintegrasi penuh dengan formulir lokasi, *reverse geocoding* (Nominatim/Android Geocoder), perlindungan dari penimpaan otomatis GPS perangkat, dan penyimpanan persisten di database **Room** (termasuk atribut `location_source`).

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Rangkuman keputusan produk & aturan teknis yang diterapkan dalam rencana ini:

- **Pemilihan Lokasi Manual via Peta Interaktif (*Interactive Map Picker*)**:
  - Dapat diakses langsung dari formulir **Edit Lokasi** (baik dari layar kamera utama maupun dari *Preview Modal*).
  - Mendukung interaksi penuh: **Geser peta (*pan/drag*)**, **Perbesar/Perkecil (*zoom in/out* via tombol `+`/`-` & *pinch gesture*)**, serta **Ketuk peta (*tap-to-select*) atau geser penanda (*marker*)** untuk memilih titik koordinat secara akurat.
  - **Transaksional (`Pilih Lokasi / OK` vs `Batal`)**: Menggeser atau mengetuk peta hanya memperbarui *draft state* di dalam dialog peta. Koordinat dan alamat hasil *reverse geocoding* hanya diterapkan ke formulir ketika pengguna menekan tombol **"Pilih Lokasi (OK)"**. Jika pengguna menekan **"Batal"**, perubahan dibatalkan sepenuhnya.
  - **Perlindungan dari Overwrite GPS & Berfungsi Tanpa Izin GPS**: Begitu pengguna memilih lokasi dari peta (`location_source = "manual_map"`), sistem mengunci lokasi tersebut agar **tidak pernah diganti otomatis oleh sensor GPS perangkat**. Tombol **"Lokasi Saya"** hanya tersedia sebagai alat bantu opsional pada peta, dan seluruh fitur peta interaktif tetap berjalan 100% walaupun izin GPS tidak diberikan.
- **Dua Template Utama Presisi 100% Sesuai Gambar Referensi**:
  - **Template 1 (Gambar 1 - Brebes Satellite)**: Kartu kiri *Mini-Map Satelit* dengan pin merah & teks `"Google"` putih; kartu kanan gelap *translucent* dengan tab kanan atas `[📷] GPS Map Camera`, judul Kecamatan/Provinsi/Negara + bendera 🇮🇩, *Plus Code* (`427p+2cv`) + alamat lengkap, `Lat ...° Long ...°`, hari/tanggal/jam `GMT +07:00`, dan `Note : ...`.
  - **Template 2 (Gambar 2 - Street Map & Sensor µT)**: Kartu kiri *Mini-Map Peta Jalan Terang* dengan pin lokasi merah besar & logo `"Google"` warna-warni; kartu kanan gelap *translucent* dengan judul alamat 2 baris, baris `⌖` koordinat koma, baris `🕒` tanggal/jam `GMT +7:00`, baris elevasi `m di atas permukaan laut`, serta baris bawah 3 kolom sensor: `±m akurasi`, kekuatan medan magnet `µT` (*real-time magnetometer*), dan kompas `° N`.

---

### 1. Overview & Core Concept

- **What It Does**: Memberikan kendali penuh kepada pengguna atas informasi waktu dan lokasi pada foto dokumentasi. Pengguna dapat memilih titik lokasi secara presisi di atas peta interaktif (dengan *tile* peta *multi-zoom* yang dapat digeser dan diketuk), memeriksa koordinat serta alamat hasil *reverse geocoding*, menyimpannya ke formulir dan database lokal, serta mencetak *stamp* yang 100% identik dengan standar visual referensi.

---

### 2. User Experience & Visual Design

- **Alur Pemilihan Lokasi Manual Menggunakan Peta (`InteractiveMapPickerDialog`)**:
  1. Pengguna membuka formulir **Edit Lokasi** lalu menekan tombol **"Pilih Lokasi lewat Peta Interaktif"**.
  2. Tampil modal layar penuh **Peta Interaktif**:
     - **Kanvas Peta Multi-Tile Interaktif**: Merender *slippy map tiles* (OpenStreetMap / Satelit dengan *fallback* grid koordinat saat *offline*) yang mendukung *drag/pan* mulus, *pinch-to-zoom*, tombol **Zoom In (`+`) / Zoom Out (`-`)**, serta **ketuk titik mana saja pada peta** untuk memindahkan *marker* pin merah.
     - **Tombol Bantu "Lokasi Saya"**: Memusatkan tampilan peta ke titik GPS terakhir perangkat (jika izin GPS tersedia) tanpa langsung menyimpan sebelum dikonfirmasi.
     - **Panel Info Titik Terpilih (Live Draft Preview)**: Menampilkan koordinat `Latitude` & `Longitude` dari posisi *marker* saat ini, *Plus Code* otomatis, serta status pengambilan alamat *reverse geocoding* (tetap valid dan bisa dikonfirmasi meskipun alamat tidak ditemukan atau perangkat sedang *offline*).
     - **Tombol Aksi Bawah**:
       - **"Batal"**: Menutup peta tanpa mengubah data lokasi pada formulir.
       - **"Pilih Lokasi (OK)"**: Mengambil `latitude`, `longitude`, `address` (jika tersedia), menetapkan `location_source = "manual_map"`, menutup peta, dan mengisi formulir **Edit Lokasi**.
  3. Saat pengguna menekan **"Simpan Lokasi"** pada formulir, konfigurasi lokasi disimpan ke database **Room** (`SavedLocationEntity` & `StampedPhotoEntity`) sehingga tetap tersimpan saat aplikasi atau data dibuka kembali.

- **Tata Letak Presisi Kedua Template Stamp**:
```
[TEMPLATE 1: GPS MAP CAMERA CLASSIC (GAMBAR 1)]
                                                  ┌────────────────────┐
┌─────────────────┐  ┌────────────────────────────┴────────────────────┤
│                 │  │ Kecamatan Brebes, Jawa Tengah,                  │
│   [SATELLITE]   │  │ Indonesia 🇮🇩                                    │
│       📍        │  │ 427p+2cv, Jl. Sapphire Regency, Pulosari, ...   │
│                 │  │ Lat -6.887405° Long 109.035938°                 │
│     Google      │  │ Jumat, 30/10/2026 01:47 PM GMT +07:00           │
│   (Putih/Bold)  │  │ Note : Captured by GPS Map Camera               │
└─────────────────┘  └─────────────────────────────────────────────────┘

[TEMPLATE 2: TELEMETRY STREET MAP (GAMBAR 2)]
┌─────────────────┐  ┌─────────────────────────────────────────────────┐
│  [STREET MAP]   │  │ 1-Y Zachat'yevskiy Pereulok, 15, Moskva,        │
│                 │  │ Rusia, 119034                                   │
│       📍        │  │ ⌖  55,7399°, 37,6016°                           │
│    (Pin Besar)  │  │ 🕒 Jum, Okt 30, 2026, 13:53, GMT +7:00          │
│                 │  │ ▲  44m di atas permukaan laut                   │
│  Google (Warna) │  │ ⌖ ±2m akurasi    ((•)) 45,72 µT     🧭 12 N     │
└─────────────────┘  └─────────────────────────────────────────────────┘
```

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Peta Interaktif Berbasis Compose Multi-Tile Canvas (Zero API Key & Works Offline)**
  - *Chosen Approach*: Membangun komponen `InteractiveMapLocationPicker` menggunakan kanvas interaktif Jetpack Compose (`pointerInput` untuk *pan/drag*, *pinch-to-zoom*, dan *tap gesture* untuk memilih titik) yang memuat *tile* peta OpenStreetMap / Esri Satellite secara asinkron sesuai *viewport* dan *zoom level* (`zoom 4..18`), dilengkapi *grid* koordinat *fallback* saat *offline*.
  - *Why*: Tidak memerlukan Google Maps API Key yang berisiko gagal otorisasi, mendukung ketuk peta maupun geser peta dengan presisi matematis Web Mercator (`lat/lon <-> pixel/tile`), dan tetap bekerja tanpa izin GPS maupun tanpa koneksi internet.

- **Decision 2: Reverse Geocoding Hibrida (Android Geocoder + Nominatim Fallback) & Toleransi Offline**
  - *Chosen Approach*: Saat pengguna memilih titik pada peta interaktif, sistem memanggil Android `Geocoder` (dan *fallback* HTTP Nominatim OSM jika tersedia) di *background* untuk mengisi nama jalan, kelurahan, kecamatan, kota, provinsi, dan kode pos. Jika tidak ditemukan atau sedang *offline*, pengguna tetap dapat menekan **"Pilih Lokasi (OK)"** dengan koordinat yang dipilih sebagai referensi utama.

- **Decision 3: Penyimpanan Persisten Konfigurasi Lokasi & Riwayat di Room Database**
  - *Chosen Approach*: Menyimpan lokasi aktif pilihan pengguna beserta `location_source` (`"manual_map"`, `"manual_input"`, atau `"gps"`) ke dalam tabel Room (`SavedLocationProfileEntity`) serta menyertakannya pada setiap record foto (`StampedPhotoEntity`). Saat aplikasi dibuka kembali atau pengguna mengedit lokasi lagi, titik dan alamat yang sebelumnya disimpan langsung dimuat kembali tanpa tertimpa GPS.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Data Flow Diagram**:
```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LocationEditModalSheet (Formulir)                    │
│  • Menampilkan koordinat, alamat, Plus Code, Note & badge sumber lokasi │
│  • Tombol: "Pilih Titik di Peta Interaktif"                             │
└───────────────────┬───────────────────────────────────▲─────────────────┘
                    │ Buka Peta                         │ Kembalikan
                    ▼                                   │ (Jika Klik "OK")
┌───────────────────────────────────────────────────────┴─────────────────┐
│              InteractiveMapPickerDialog (Peta Interaktif)               │
│  • Draft State: draftLat, draftLon, draftZoom, draftAddress             │
│  • Gestur: Pan/Drag Peta, Zoom (+/- & Pinch), Tap Peta / Geser Marker   │
│  • Tombol "Lokasi Saya" (Opsional) | "Batal" (Discard) | "Pilih Lokasi" │
└───────────────────────────────────────────────────────┬─────────────────┘
                                                        │ Simpan Formulir
                                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             CameraStampViewModel & Room Database Persistence            │
│  • Menyimpan: latitude, longitude, address, location_source             │
│    ("manual_map" | "gps") ke SavedLocationProfileEntity & StampedPhoto  │
│  • Aturan: Jika location_source != "gps", abaikan update GPS otomatis   │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & Schema Updates**:
  - `LocationMetadata`:
    - `latitude: Double`
    - `longitude: Double`
    - `address / fullAddress: String` (beserta rincian `street`, `subLocality`, `locality`, `city`, `province`, `postalCode`, `country`)
    - `locationSource: String` (`"gps"`, `"manual_map"`, atau `"manual_form"`)
    - `plusCode: String` (otomatis dihitung dengan algoritma Open Location Code dari `latitude` & `longitude`, atau diedit manual)
    - `magneticFieldUt: Float` (kekuatan medan magnet dalam `µT` dari sensor magnetometer)
  - `SavedLocationProfileEntity` & `StampedPhotoEntity` (Room Database v2):
    - Menyimpan `latitude`, `longitude`, `address`, `locationSource`, `plusCode`, dan komponen alamat agar saat aplikasi dibuka ulang atau data diinspeksi kembali, lokasi yang dipilih pengguna tetap terjaga.
