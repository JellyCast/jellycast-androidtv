# Playback test matrix

Compiled on 2026-10-11 from the Jellyfin library (`192.168.1.4:8096`, Jellyfin 10.11), read-only via `/Users/{userId}/Items?Recursive=true&IncludeItemTypes=Movie,Episode&Fields=MediaSources,MediaStreams,Path,Chapters`. Data from `MediaSources[0].MediaStreams`. Builds on "Codec test set" and "Library profile" in `NOTES.md`; items already tested there are listed here too, with the reference.

## Library in numbers (2073 items: 49 movies, 2024 episodes)

| Dimension | Distribution (per item, unless stated otherwise) |
|---|---|
| Container | mkv 1829 · mp4 242 · mov 1 · ts (m2ts) 1 |
| Video codec / profile | HEVC Main 10 1064 · HEVC Main (8-bit) 47 · H.264 High 652 · H.264 Main 308 · VC-1 Advanced 1 · no video 1 (Friends 10x8) |
| Pixel format | only 4:2:0 (`yuv420p` / `yuv420p10le`); no H.264 High10, no 4:2:2 or 4:4:4 |
| Level | HEVC L4 (120) 625 · L4.1 (123) 1 · L5 (150) 287 · L5.1 (153) 198 · H.264 L3.1 236 · L4.0 386 · L4.1 338 |
| Resolution | 4K 484 · 1080p 1352 · 720p 236 · SD 0 |
| Range (`VideoRangeType`) | SDR 1773 · DOVIWithHDR10 (p8.1) 115 · HDR10 70 · DOVIWithHDR10Plus (p8.1) 50 · DOVIWithEL (p7.6) 21 · DOVIInvalid (p8.6) 20 · HDR10Plus 17 · DOVI (p5) 5 · HLG 1 |
| Frame rate | 23.976 1920 · 24 68 · 25 41 · 23.81 19 (Mythic Quest) · 29.97 24 · 50/59.94 0 · interlaced 0 |
| Audio (tracks / items) | AC3 878/542 · EAC3 705/516 · EAC3-JOC Atmos 373/296 · AAC-LC 2.0 252/252 · AAC-LC 5.1 221/221 · DTS core/ES 210/157 · DTS-HD MA 175/172 · TrueHD Atmos 85/85 · HE-AAC 5.1 48/48 · FLAC 2.0 8/8 · DTS:X 3/3 · HE-AAC 7.1 3/3 · PCM 24-bit 3/2 · DTS-HD HRA 2/1 · TrueHD (without Atmos) 1/1 · AAC-LC 7.1 1/1 · PCM 16-bit 1/1 |
| Subtitles (tracks / items) | SRT 13615/1219 · PGS 2979/278 · mov_text 1035/57 · ASS 239/238 · VobSub (DVDSUB) 98/47 · external files 0 · without subtitles 353 items |
| Many tracks | ≥ 20 subtitles: 451 items (≥ 40: 138; max. 75) · max. 18 audio tracks |
| Chapters | 1004 items with chapters (max. 121) |
| Bitrate / length | > 80 Mbps: 20 items (max. 116.8) · > 3 hours: 7 items (max. 263 min) |

Note: *Anyone but You (2023)* (`568fffd2…`, codec test set #8) is no longer in the library; for DV p7 now use Game of Thrones 2x/8x or Ready Player One.

## Test items

DP = direct play. "NOTES #n" = already tested in the codec test set in `NOTES.md`. "ch" = chapters.

| # | Category | Item id | Name | Stream summary | Why interesting |
|---|---|---|---|---|---|
| 1 | H.264 1080p SDR | `3c89fee3fd3d5c549e161d9ef519e281` | Alone 0x18 | mkv · H.264 High L4.0 1920×1080 29.97 8-bit · AAC 2.0 · 2× SRT · 6 ch · 7.5 Mbps · 2.3 GB | Baseline (NOTES #1), also 29.97 fps |
| 2 | H.264 1080p SDR | `e6f566128952e14ab45b8971ee004f44` | Shameless 8x9 | mkv · H.264 High L4.0 1080p 23.976 · EAC3 5.1 · 2× SRT · 48 ch · 10 Mbps · 4.1 GB | Many chapters; ordinary series episode |
| 3 | H.264 1080p SDR | `333d6c04e935712054f4b8e5a50e7225` | Legend (2015) | mkv · H.264 High L4.1 1080p 24 · DTS-HD MA 7.1, TrueHD Atmos, DTS-HD MA 5.1, DTS, PCM 16-bit 2.0, 3× AC3 · PGS · 12 ch · 41.5 Mbps · 37.3 GB | Remux movie: 8 audio tracks, default choice and PCM |
| 4 | H.264 High10 / 4:2:2 | — | missing | — | Not in the library |
| 5 | HEVC 1080p SDR | `69bc494c74fccc4adb5563b4141ec60c` | Band of Brothers 1x1 | mkv · HEVC Main 10 L4 1080p 23.976 · EAC3 5.1 · 20× PGS · 11 ch · 12.3 Mbps | NOTES #2; 10-bit SDR, many PGS tracks |
| 6 | HEVC 1080p SDR | `0e402566ee32d9ec6fdee22724ccf57b` | Star City 1x8 | mkv · HEVC Main 10 L4.1 **1918×872** 23.976 · AC3 5.1 + EAC3 Atmos · 45× SRT · 3 ch · 7.3 Mbps | Odd width, height not a multiple of 16; only HEVC L4.1 |
| 7 | HEVC 8-bit (Main) | `d4c56071a7f8324ec027d7a31921d8f1` | Landman 1x1 | mkv · HEVC **Main (8-bit)** L5 3840×1920 SDR 23.976 · EAC3 5.1 · 2× SRT · 16 Mbps | 4K SDR 8-bit HEVC: different decoder profile than Main 10 |
| 8 | HEVC 4K SDR 10-bit | `77ecf7d97017f4bfe1a7ed81ed572472` | Stranger Things 1x1 | mkv · HEVC Main 10 L5.1 3840×1920 **SDR** · AC3 5.1 · PGS · 5 ch · 27.8 Mbps | 4K without HDR: TV must not switch to HDR mode |
| 9 | HEVC 4K HDR10 | `96a397e25a8867165db7a64c886b89ac` | The Queen's Gambit 1x1 | mkv · HEVC Main 10 L5.1 3840×2160 **24.000** HDR10 · EAC3 5.1 · SRT · 14.7 Mbps | Ordinary HDR10 episode; 24 fps instead of 23.976 (frame rate) |
| 10 | HEVC 4K HDR10 | `ef1eefa7355ba446e7c8bb26003310fa` | GoodFellas (1990) | mkv · HEVC Main 10 L5.1 2160p HDR10 · DTS-HD MA 5.1 + 16× AC3 (incl. 2× mono) · 2× SRT + 32× PGS · 47 ch · **90.8 Mbps** · 90.5 GB | High bitrate, 17 audio tracks, AC3 mono |
| 11 | HEVC 4K HDR10 | `11b4ce484aae38676b689f7643e648b5` | Jurassic World Dominion (2022) | mkv · HEVC Main 10 3840×1920 HDR10 · AAC-LC 7.1 · 3× VobSub · 20 ch · 16 Mbps | NOTES #14; AAC 7.1 → PCM 8ch, VobSub only |
| 12 | HDR10+ | `2ca07f18f0e16ce8dd776e5924ca941a` | Dark Matter 1x1 | mkv · HEVC Main 10 3840×1920 HDR10+ · EAC3 Atmos · 42× SRT · 25.8 Mbps | NOTES #4 |
| 13 | HDR10+ | `5cc754f553e10f167697738c7f472435` | The End of Oak Street (2026) | mkv · HEVC Main 10 3840×1600 HDR10+ · EAC3 5.1 · 29× SRT · 16.2 Mbps · 11.6 GB | Green blocks on first start (NOTES, Open items); mode switch test |
| 14 | DV p5 | `ced7811a650204267628372bf2268a70` | Star City 1x4 | mkv · HEVC DV 5.0 3840×1746 · EAC3 Atmos · 43× SRT · 3 ch · 26.1 Mbps | NOTES #5; no HDR10 fallback, height not a multiple of 8 |
| 15 | DV p5 | `3b2dcc04612a45ec862790f6b95b02ef` | The Pitt 2x6 | mkv · HEVC DV 5.0 3840×2160 · EAC3 Atmos · 38× SRT · 8.4 Mbps | DV p5 at full 2160 and low bitrate |
| 16 | DV p5 | `31ed3e7d7fc3d8188fdc60bd48296d36` | Silo 3x4 | mkv · HEVC DV 5.0 **3840×1606** · EAC3 Atmos · 43× SRT · 25.9 Mbps | DV p5 with height not a multiple of 8 |
| 17 | DV p7 (FEL) | `2bc7b1d116a8224bc89d0b33c8775222` | Game of Thrones 8x6 | mkv · HEVC DV 7.6 EL 2160p · TrueHD Atmos, DTS-HD MA 7.1, DTS + 14× AC3 · 2× SRT + 34× PGS · 8 ch · **116.8 Mbps** · 60.3 GB | NOTES #9; highest bitrate, 17 audio tracks |
| 18 | DV p7 (FEL) | `8538b91761d78debe2c372b608d0fa76` | Ready Player One (2018) | mkv · HEVC DV 7.6 EL 2160p · TrueHD Atmos, DTS-HD MA 7.1/5.1 + 12× AC3 · 32 subtitles (PGS+SRT) · 14 ch · 92.1 Mbps · 81.5 GB | Movie with p7, replaces Anyone but You |
| 19 | DV p7 (FEL) | `d2f8969618634fd3307364ec35667400` | The Godfather Part II (1974) | mkv · HEVC DV 7.6 EL 2160p · AC3 2.0 (first), EAC3 5.1, AC3 · 4× SRT + VobSub · 55.4 Mbps · 81.1 GB · **202 min** | p7 + long + VobSub + first audio track stereo |
| 20 | DV p8.1 + HDR10 | `d0d04ded2fb3eecde79ad9bff506b2a1` | The White Lotus 1x1 | mkv · HEVC DV 8.1 2160p · EAC3 Atmos · 3× SRT · 22.8 Mbps | Stuttering picture (NOTES, open item) |
| 21 | DV p8.1 + HDR10 (mp4) | `efdf03f9e8111bc3fc5b010b4a1be4e0` | Jurassic World Rebirth (2025) | **mp4** · HEVC DV 8.1 2160p · 10 EAC3/AC3 tracks (incl. EAC3 7.1) · 22× mov_text · 20 ch · 75.4 Mbps · 76.5 GB | Large mp4 file (moov atom, seeking), many mov_text |
| 22 | DV p8.1 + HDR10+ | `e954c12dc0a712e3a6b18368481c32cb` | Disclosure Day (2026) | mp4 · HEVC DV 8.1 3840×1606 · EAC3 Atmos + EAC3 · 4× mov_text · 11 ch · 25.4 Mbps | NOTES #6 |
| 23 | DV p8.1 + HDR10+ | `3a5e1d27a22c1a697151f0d6be3bfc5d` | Interstellar (2014) | mkv · HEVC DV 8.1 2160p · TrueHD Atmos, EAC3 Atmos 7.1, EAC3 Atmos 5.1, PCM 24-bit 7.1, 2× DTS-HD MA, 9× AC3 · **74× PGS** + SRT · 20 ch · **109.2 Mbps** · 102.7 GB | NOTES #12; most subtitles, PCM 7.1 |
| 24 | DV p8.6 (`DOVIInvalid`) | `3e9edff3142d06572d434b09223f73f2` | Game of Thrones 1x1 | mkv · HEVC DV 8.6 2160p · TrueHD Atmos + 2× AC3 · PGS + 2× SRT · 9 ch · 42.2 Mbps | NOTES #7 |
| 25 | DV p8.6 (`DOVIInvalid`) | `d225d973349f819a372b96c70df2f282` | Game of Thrones 3x5 | mkv · HEVC DV 8.6 2160p · TrueHD Atmos + 2× AC3 · 2× PGS + 3× SRT · 52.8 Mbps | Highest bitrate within p8.6 |
| 26 | HLG | `7eeacbbffdf4f94e67752f3a0b1876e1` | The White Lotus 3x4 | mkv · HEVC Main 10 2160p **25 fps** HLG · EAC3 Atmos · 2× SRT · 16.3 Mbps | NOTES #10; only HLG, 25 fps |
| 27 | AV1 | — | missing | — | Not in the library |
| 28 | VP9 | — | missing | — | Not in the library |
| 29 | VC-1 | `31fb27dbf9b42264cae147220e3e32ee` | Blow (2001) | mkv · VC-1 Advanced 1080p · TrueHD 5.1 (without Atmos), DTS-HD MA, AC3 · PGS · 25 ch · 30.8 Mbps | NOTES #11; only old codec → transcode; only TrueHD without Atmos |
| 30 | MPEG-2 / MPEG-4 ASP | — | missing | — | Not in the library |
| 31 | Interlaced | — | missing | — | Not a single item with `IsInterlaced=true` |
| 32 | 25 fps | `94936ca50ddcad87fa7606a33a359fb3` | The White Lotus 3x1 | mkv · HEVC DV 8.1 2160p **25** · 2× EAC3 5.1 · 5× SRT · 8 ch · 22.7 Mbps | DV + 25 Hz mode switch |
| 33 | 25 fps | `bd7e5d76fa729d6f5031201c1870dbf5` | Gomorrah: The Origins 1x2 | mkv · HEVC Main (8-bit) 2160p 25 SDR · EAC3 5.1 · 18× SRT · 4 ch · 15.8 Mbps | 25 fps SDR, 8-bit 4K |
| 34 | 25 fps | `23010a61dcc493e367c1f046e9a5f490` | Lucifer 3x1 | mkv · H.264 High L4.1 1080p 25 · AC3 2.0 + AC3 5.1 · 2× SRT + VobSub · 10.7 Mbps | 25 fps H.264, VobSub next to SRT, first audio track stereo |
| 35 | 29.97 fps | `b759a726476cec4e6cf928dabeed1e33` | The Pacific 1x1 | mkv · H.264 High 1080p **29.969** · AC3 2.0 + AC3 5.1 · SRT · 3.5 Mbps | Just-not-29.97 (rounding in the frame rate choice) |
| 36 | 23.81 fps | `cbe5a823fa701b547ba9f5455de431e7` | Mythic Quest 1x1 | mkv · HEVC Main 10 1080p **23.81** SDR · EAC3 Atmos · 41× SRT · 5.2 Mbps | Odd frame rate (probably VFR): which refresh rate does the app choose? |
| 37 | 50 / 59.94 fps | — | missing | — | Not in the library |
| 38 | Height ≠ ×8 | `394098389c08ac1239e113202a709a51` | The Ministry of Ungentlemanly Warfare (2024) | mkv · HEVC Main 10 **3840×1604** HDR10 · TrueHD Atmos + AC3 · 5× PGS · 16 ch · 36.9 Mbps | Cropped 4K HDR10 (1604) |
| 39 | Height ≠ ×8 | `c8620346a2f3b7557a01ba035fe3403e` | Silo 2x5 | mkv · HEVC DV 8.1 **3840×1606** · 7× EAC3 Atmos · 48× SRT · 35.1 Mbps | 7 Atmos tracks (languages), 1606 |
| 40 | Width ≠ ×8 | `b6d1915ec18921eb0330dcf73f60f291` | The Pacific 1x3 | mkv · H.264 High **1918×1080** 29.969 · AC3 2.0 + 5.1 · SRT · 3.5 Mbps | Odd width (also 1x9: 1914) |
| 41 | Container mp4 | `d75c2042f49ff66e461220003a220cd2` | The Office 1x1 | mp4 · HEVC Main 10 1080p · AAC-LC 5.1 · no subtitles · 2.2 Mbps | Small mp4, AAC 5.1, no subtitles |
| 42 | Container mp4 + DV | `99dc7bdb6fabf0ef8c76874c17bd5220` | The Gentlemen 2x1 | mp4 · HEVC DV 8.1 2160p 24 · EAC3 Atmos (not default) + EAC3 5.1 (**default**) · 2× mov_text | Default track is not the first: does the app choose the Atmos track or the default track? |
| 43 | Container ts (m2ts) | `3acaf014d1d24c5058c90f8e5c4ab372` | The Usual Suspects (1995) | **ts** · H.264 High L4.1 1080p · DTS-HD MA + 4× AC3 2.0 · 9× PGS · 44 Mbps · 34.4 GB | NOTES #15; only ts, Media3 → server remux |
| 44 | Container mov | `c04141bdfc1518d2bf321bcb11b0fb29` | How I Met Your Mother 6x24 | **mov** · HEVC Main 8-bit 1080p · HE-AAC 5.1 · 1 Mbps | NOTES #16; only mov |
| 45 | AC3 | `e74d52d1eacff07fbcae772cfb0ee46d` | The Good Place 1x1 | mkv · HEVC Main 10 1080p · AC3 5.1 (only track) · SRT · 4 ch · 4.1 Mbps | AC3 only |
| 46 | EAC3 2.0 + 5.1 | `c7326c4ad20ce50f0dee1371c090643b` | Shameless 1x1 | mkv · H.264 High 1080p · EAC3 2.0 + EAC3 5.1 · 4× SRT · 5.9 Mbps | First track stereo; which track does the app choose? |
| 47 | DTS (core only) | `07bc3ca90e6937c4716ddacb83bbf646` | Sons of Anarchy 1x1 | mkv · H.264 High L4.1 1080p · **DTS 5.1 only track** · SRT · 12.5 Mbps | No AC3 alternative: DTS passthrough or server transcode |
| 48 | DTS-HD MA (only track) | `039b45f58bfe4dcfffcf879f429c4e46` | Oppenheimer (2023) | mkv · HEVC HDR10 2160p · **DTS-HD MA 5.1 only track** · 14× PGS · 20 ch · 68.8 Mbps · 88.3 GB · 180 min | Server must transcode audio at high bitrate |
| 49 | DTS:X + DTS-HD HRA | `59bdfeb4a227cda25910ebf48a41fc50` | Jurassic World: Fallen Kingdom (2018) | mkv · HEVC HDR10 2160p · DTS:X 7.1 + 2× DTS-HD HRA 7.1 · 5× PGS · 60.9 Mbps | Only HRA, no AC3 |
| 50 | DTS-ES 6.1 | `f668d990608687998647ff668a073d53` | The Lord of the Rings: The Return of the King (2003) | mkv · HEVC HDR10 3840×1610 · **DTS-ES 6.1** + AC3 5.1 · SRT + 2× PGS · 28.6 Mbps · **263 min** | Longest item; 7 channels |
| 51 | TrueHD Atmos + ASS | `57b7d5f2a4e9d07e9a69c3b2f53c1d59` | The Lord of the Rings: The Two Towers (2002) | mkv · HEVC HDR10 3840×1600 · EAC3 5.1, TrueHD Atmos, 4× AC3 2.0 · ASS + SRT · 68 ch · 235 min | Long, many chapters, ASS in a 4K movie |
| 52 | AAC HE 7.1 | `99b041d273a222685ce34bd385cd480f` | The Hobbit: An Unexpected Journey (2012) | mkv · HEVC HDR10 3840×1608 · **HE-AAC 7.1** · SRT · 35 ch · 5.2 Mbps · 182 min | HE-AAC 8 channels, low-bitrate 4K |
| 53 | FLAC 2.0 + PGS | `18ab46581c58e8b0ce41a195ec0a19e5` | The Sopranos 3x9 | mkv · H.264 High L4.1 1080p · DTS-HD MA 5.1 + **FLAC 2.0** · 9× PGS · 8 ch · 26 Mbps | FLAC (only stereo in the library) |
| 54 | PCM 24-bit 2.0 | `0be484661b8bd82623ddb2346173ebba` | The Imitation Game (2014) | mkv · H.264 High L4.1 1080p · DTS-HD MA, DTS, 2× **PCM 24-bit 2.0** · PGS · 12 ch · 44.2 Mbps | Choosing a PCM track in the menu |
| 55 | ASS | `b99e1b11d558f14b8669b80e441b98a0` | Friends 1x1 | mkv · H.264 Main L3.1 720p · AAC 2.0 · ASS · 2.1 Mbps | NOTES #13; damaged file (16 KiB gaps) |
| 56 | ASS 1080p | `3e7ede5292a2457282a421fecce68561` | Ted Lasso 4x2 | mkv · H.264 High **1920×960** · EAC3 Atmos · ASS · 4 ch · 11.5 Mbps | ASS outside Friends, unusual height |
| 57 | Many subtitles (SRT) | `8069b55892643cfcc8e65afda4c7f4ee` | Wednesday 2x5 | mkv · HEVC Main 10 2160p SDR · EAC3 Atmos · **66× SRT** · 12.5 Mbps | Long subtitle list in the menu (scrolling, focus) |
| 58 | Chapters (many) | `54acb32106b2e9ef78149b011a663d95` | The Lord of the Rings: The Rings of Power 2x7 | mkv · HEVC Main 10 3840×1600 24 SDR · EAC3 Atmos · 3× SRT · **121 ch** · 6.8 Mbps | Most chapters; nested chapters are not visible via the API |
| 59 | Without video/audio | `c35c7484b8f9c360ddb8a7fb1114829c` | Friends 10x8 | mkv · no media info, no audio · 0.3 GB | NOTES Edge cases: must not crash |
| 60 | Small / old (720p) | `7dc737654b34c73dcbd647cfd9a9e21d` | Friends 1x6 | mkv · H.264 Main **L3.1** 1280×720 · AAC 2.0 · ASS · 1.8 Mbps · 0.3 GB | Smallest/lowest profile; SD (480p/576p) is missing |

## Missing from the library

H.264 High10 and 4:2:2, AV1, VP9, MPEG-2, MPEG-4 ASP (DivX/Xvid), interlaced material, 50 and 59.94 fps, SD (480p/576p), containers avi and webm, DV p8.4 (HLG), multichannel FLAC, Opus, MP3, external subtitle files. Nested chapters (MKV editions/subchapters) cannot be recognized via the Jellyfin API: the server returns one flat list.
