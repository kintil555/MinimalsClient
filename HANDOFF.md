# HANDOFF: MinimalsClient x Flashback Post Effect (checkpoint 2, setengah jadi)

Baca file ini sebelum menyentuh apa pun. Bahasa user: Indonesia. Gaya jawab: singkat, teknis.

## Tujuan user
- MinimalsClient (Fabric, MC 26.2, mojmap, tanpa yarn) = **mod terpisah** yang menambah fitur ke
  mod **Flashback** (replay mod, Moulberry) lewat mixin. Bukan fork/bundle Flashback.
- Fitur replay bawaan MinimalsClient dihapus (SUDAH).
- Fitur baru: elemen timeline **"Post Effect"** di Flashback: Blur, Invert, Pixelated, Custom
  (resource-pack id). Bisa dipasang ke **blok spesifik** dengan radius yang bisa diatur: tambah
  elemen Post Effect dulu, di sidebar ada opsi blok spesifik, pilih blok dengan **Ctrl+H + klik kiri**.
- Tombol **Record** dipindah ke menu RSHIFT (MenuChoiceScreen) dan memanggil API Flashback (SUDAH).
- `PostEffectModule` lama (modul ClickGUI, efek layar penuh) **tidak disentuh**, terpisah dari ini.
- User TIDAK minta compile penuh di sandbox. Sandbox tidak bisa Gradle (Maven/Fabric/Mojang
  diblokir). Tulis kode saja; user yang build lewat CI. Boleh cek sintaks dengan javac + stub jika perlu.

## Fakta terverifikasi
- Flashback **LICENSE: all rights reserved, do not redistribute**. Jangan salin kode Flashback ke repo
  ini, jangan commit jar Flashback / imgui binding. `libs/*.jar` di-gitignore.
- Flashback master = MC 26.3 (RenderPearl, tidak cocok). Versi untuk 26.2 = commit `f030879`
  (mod_version 0.43.4). Clone ada di `/home/claude/Flashback` (detached HEAD di `6212613~1`,
  mungkin sudah hilang di sesi baru; clone ulang dan `git checkout f030879`).
- JAR MC 26.2 upload user: `/mnt/user-data/uploads/26_2-0_19_5.jar`. JDK 25 harus dipasang lagi tiap
  sesi baru: `apt-get update; apt-get install -y openjdk-25-jdk-headless` (javap di
  `/usr/lib/jvm/java-25-openjdk-amd64/bin`).
- API Flashback publik yang dipakai: `KeyframeRegistry.register(KeyframeType)`,
  `Flashback.RECORDER`, `Flashback.startRecordingReplay()`, `Flashback.finishRecordingReplay()`,
  `Flashback.isInReplay()`, `ReplayUI.getMouseLookVector()`, `ReplayUI.isCtrlOrCmdDown()`.
- Pola keyframe (dari FOVKeyframe / CameraShake / AudioKeyframeType): `Keyframe` + `KeyframeType` +
  `KeyframeChange` (record) + Gson `TypeAdapter`. `EditorState.applyKeyframes` otomatis memakai tipe
  yang terdaftar, termasuk interpolasi.
- `supportsHandler` override seperti AudioKeyframeType: hanya `MinecraftKeyframeHandler` (viewport,
  scrubbing, dan export video memakainya). `ReplayServerKeyframeHandler` diabaikan.
- Icon font Flashback dibangun dari daftar glyph eksplisit (`ReplayUI.buildMaterialIconRanges`).
  Hanya glyph di daftar itu yang render. Dipakai `\ue3c9`.
- **Serialisasi Gson Flashback tertutup**: `Keyframe.TypeAdapter` (nested class di `Keyframe`) memakai
  `switch` tipe string di `deserialize` dan `switch` pattern di `serialize`, `default` melempar
  `IllegalStateException`. `FlashbackGson.build()` mendaftarkan adapter per kelas. Perlu mixin.
- Pause/edit keyframe: sidebar memanggil `keyframe.renderEditKeyframe(update -> ...)`; popup
  pembuatan lewat `KeyframeType.createPopup()` dan `##CreateKeyframe` di TimelineWindow.
- `ReplayUI.handleBasicInputs()` (private static) menangani klik kiri/kanan di viewport; klik kiri
  memanggil `imguiGlfw.setGrabbed(...)` untuk kamera. Kita cancel di HEAD saat pick block.
- Hook render vanilla 26.2 (terverifikasi di jar): `GameRenderer.render(DeltaTracker, boolean)`,
  `GameRenderer.mainRenderTarget`, `resourcePool`, `LevelRenderer.doEntityOutline()`,
  `PostChain.process(RenderTarget, GraphicsResourceAllocator)`,
  `ShaderManager.getPostChain(id, LevelTargetBundle.MAIN_TARGETS)`. `PostEffectMixin` milik user
  sudah memakai pola ini (inject setelah `doEntityOutline()`).
- Post chain vanilla 26.2: `assets/minecraft/post_effect/{blur,invert,...}.json`; uniform di-hardcode
  di JSON (mis. `BlurConfig.Radius`, `InvertConfig.InverseAmount`), jadi radius/intensitas dinamis
  butuh `PostPass`/`RenderPipeline` sendiri atau chain yang dibuat lewat kode. Tidak ada pixelate
  vanilla. Shader ada di `assets/minecraft/shaders/post/*.fsh` (`box_blur`, `invert`, `blit`).

## Yang SUDAH dikerjakan (tahap 1 dan sebagian 2)
Tahap 1 (selesai):
- Hapus `replay/*` (seluruh paket), 20 `Replay*Mixin`, plus `ConnectionMixin`, `ConnectionAccessor`,
  `MinecraftAccessor`, `MinecraftMixin`, `TitleScreenMixin`, `SkyRendererAccessor`,
  `ClientPacketListenerAccessor`, `GuiGraphicsExtractorInvoker`. `minimals.mixins.json` dibersihkan.
- `MinimalClientMod`: hapus import/tick/hud/keybind replay + field `replayTimelineKey`. `en_us.json`
  hapus `key.minimals.replay_timeline`.
- `flashback/FlashbackBridge` + `FlashbackApi`: fasad aman (Flashback opsional). `MenuChoiceScreen`
  Record kini memakai `FlashbackBridge` (start/finish).
- `build.gradle`: repo Modrinth maven + `compileOnly "maven.modrinth:flashback:${flashback_version}"`
  + `compileOnly files('libs/imgui-binding-1.90.0.jar')`. `gradle.properties`:
  `flashback_version=0.43.4`. `fabric.mod.json`: depends `flashback >=0.43.0`, mixins tambah
  `minimals.flashback.mixins.json`. `libs/` + `.gitignore`.

Tahap 2 (keyframe, hampir selesai), semua di `src/main/java/com/minimals/client/flashback/postfx/`:
- `PostFxKind` (BLUR/INVERT/PIXELATE/CUSTOM), `PostFxScope` (SCREEN/BLOCKS), `PostFxBlock` (record x,y,z,radius)
- `KeyframeChangePostEffect` (record, `apply` -> `PostFxState.submit`, `interpolate`)
- `PostFxState` (PENDING/ACTIVE per frame; `beginFrame()`, `active()`, `clear()`)
- `PostEffectKeyframe` (+ `TypeAdapter` JSON, key `"type":"minimals_post_effect"`)
- `PostEffectKeyframeType` (id `MINIMALS_POST_EFFECT`, `allowApplyingDuplicateKeyframeChanges=true`)
- `ui/PostEffectKeyframeEditor` (popup + sidebar ImGui, daftar blok, radius per blok)
- `BlockPickMode` (arm/disarm, Ctrl+H + klik kiri -> raycast -> `addBlock`)
- `flashback/mixin/PostEffectPickMixin` (@Mixin ReplayUI, inject HEAD `handleBasicInputs`)
- `flashback/mixin/FlashbackMixinPlugin` (mixin hanya aktif jika mod `flashback` ada)
- `resources/minimals.flashback.mixins.json` (mendaftarkan `PostEffectPickMixin`, `PostEffectRenderMixin`,
  `KeyframeGsonMixin`, `FlashbackGsonMixin`; **tiga terakhir BELUM ADA filenya**)

## UPDATE checkpoint 2 (langkah 1 dan 2 di bawah SUDAH SELESAI)
- `flashback/FlashbackBootstrap` (register `PostEffectKeyframeType`), `FlashbackBridge.bootstrap()`,
  dipanggil dari `MinimalClientMod.onInitializeClient` sebelum `ConfigManager.load`.
- `flashback/mixin/FlashbackGsonMixin` (inject `build` RETURN, daftar `PostEffectKeyframe.TypeAdapter`)
  dan `flashback/mixin/KeyframeGsonMixin` (@Mixin `Keyframe.TypeAdapter`, HEAD `deserialize`/`serialize`).
  Target nama method sudah dicek di sumber Flashback f030879.
- `minimals.flashback.mixins.json` masih mendaftarkan `PostEffectRenderMixin` yang **belum ada filenya**;
  buat, atau hapus entrinya, sebelum build.

## TEMUAN RENDER 26.2 (menentukan desain tahap 3, sudah diverifikasi lewat javap pada jar)
- `PostPass` membuat uniform `GpuBuffer` sekali di konstruktor dengan usage `128` (USAGE_UNIFORM) saja,
  TANPA `USAGE_COPY_DST`. Jadi `CommandEncoder.writeToBuffer` tidak bisa memperbarui uniform per frame.
- `PostChain.load(PostChainConfig, TextureManager, Set<Identifier>, Identifier, Projection,
  ProjectionMatrixBuffer)` membuat pipeline lewat `RenderPipeline.builder(POST_PROCESSING_SNIPPET)`
  + `GpuDevice.precompilePipeline(...)` tiap dipanggil (mahal). `Projection` dan `ProjectionMatrixBuffer`
  adalah field private `postChainProjection` / `postChainProjectionMatrixBuffer` di `ShaderManager`
  (butuh @Accessor mixin).
- `ShaderManager.getPostChain(id, targets)` hanya membaca chain dari JSON resource dan meng-cache-nya.
  Uniform ada di JSON, jadi statis. Vanilla tidak punya pixelate.
- Rekomendasi desain: bangun `PostPass` sendiri per kombinasi nilai TERKUANTISASI (mis. 24 langkah),
  bagi-pakai `RenderPipeline` per jenis shader (uniform tidak mengubah pipeline), cache LRU ~32 entri.
  Alternatif lebih sederhana: chain JSON statis untuk Invert dan preset Blur/Pixelate bertingkat
  (mis. blur_1..blur_5) lalu pilih tingkat terdekat dari intensity; radius blok memakai mask shader
  dengan uniform yang dibangun ulang (kuantisasi posisi layar tidak cocok, jadi untuk mode BLOCKS
  pertimbangkan render mask ke render target sendiri).
- Shader vanilla acuan: `assets/minecraft/shaders/post/{box_blur,invert,blit}.fsh`, vertex
  `minecraft:core/screenquad`. Uniform block memakai `layout(std140)`; `SamplerInfo {vec2 OutSize; vec2 InSize;}`
  selalu ada. Sampler dinamai `<name>Sampler`.
- `UniformValue` di 26.2: `FloatUniform(float)`, `Vec2Uniform(Vector2fc)`, `IntUniform`, `Vec3Uniform`,
  `Vec4Uniform`, `IVec3Uniform`, `Matrix4x4Uniform`; `PostChainConfig.Pass(vertexId, fragmentId, inputs,
  outputTarget, uniforms)`, `TargetInput(samplerName, targetId, useDepthBuffer, bilinear)`,
  `InternalTarget(Optional<Integer> w, Optional<Integer> h, boolean persistent, int clearColor)`.

## KEPUTUSAN DESAIN RENDERER (tahap 3), diusulkan ke user, belum dijawab
User ditanya memilih A atau B. Jika user belum menjawab, gunakan usulan hibrida ini (bukan A murni):
- **Mode SCREEN (layar penuh) = pendekatan B.** Chain JSON statis di
  `src/main/resources/assets/minimals/post_effect/*.json`, dijalankan lewat
  `ShaderManager.getPostChain(id, LevelTargetBundle.MAIN_TARGETS)` + `PostChain.process(...)`,
  persis pola `PostEffectMixin` milik user. Preset bertingkat: `blur_1..blur_5` (nilai Radius berbeda),
  `pixelate_1..pixelate_5` (butuh shader `minimals:post/pixelate.fsh` baru + uniform PixelSize),
  `invert` (InverseAmount = intensity terkuantisasi, mis. 4 langkah). Tingkat dipilih dari
  `blurRadius`/`pixelSize`/`intensity`. Radius TIDAK kontinu (dikuantisasi), itu trade-off yang diterima.
  CUSTOM: `Identifier.tryParse(customId)` lalu `getPostChain`, id gagal dicatat di set `failed`
  (seperti `PostEffectMixin`) agar log tidak banjir.
- **Mode BLOCKS (blok spesifik) = pendekatan A.** Butuh `PostPass`/`RenderPipeline` sendiri karena
  mask radial + posisi layar berubah tiap frame: uniform tidak bisa ditulis ulang di 26.2 (lihat TEMUAN
  RENDER). Bangun pass per nilai terkuantisasi, bagi-pakai `RenderPipeline` per jenis shader, cache LRU
  ~32 entri, dan HINDARI `PostChain.load` per frame. Alternatif yang perlu dinilai: render mask ke
  `RenderTarget` sendiri lalu chain statis membaca mask itu sebagai input sampler (uniform tetap statis,
  data dinamis lewat tekstur), ini menghindari masalah uniform sama sekali.
- Mengapa hibrida: A murni untuk layar penuh berlebihan (blur sudah cukup dengan preset), sementara
  mode blok memang butuh data per frame. Sandbox tidak bisa uji visual, jadi minimalkan permukaan A.
- Semua efek hanya dijalankan saat `Flashback.isInReplay()` atau saat export (`Flashback.isExporting()`),
  dan `PostFxState.clear()` dipanggil di luar itu.
- Alur data sudah siap: `PostFxState.beginFrame()` mengembalikan daftar `KeyframeChangePostEffect`
  (kind, customId, scope, intensity, pixelSize, blurRadius, blocks) untuk frame ini, urut sesuai track.

## Yang BELUM dikerjakan (urutan kerja)
1. (SELESAI) **Registrasi tipe ke Flashback.** Dulu belum ada yang memanggil
   `KeyframeRegistry.register(PostEffectKeyframeType.INSTANCE)`. Tambah di entrypoint client
   (`MinimalClientMod.onInitializeClient`), dibungkus `if (FlashbackBridge.isLoaded())` dan lewat kelas
   terpisah (mis. `FlashbackBootstrap`) agar tidak memuat kelas Flashback saat tidak terpasang.
   Pastikan urutan: Flashback register tipenya di `Flashback.onInitializeClient`; kita register setelahnya
   tidak masalah (`KeyframeRegistry.register` idempoten per kelas).
2. (SELESAI) **Mixin Gson (wajib, jika tidak file editor tidak bisa disimpan/dimuat):**
   - `KeyframeGsonMixin` -> `@Mixin(Keyframe.TypeAdapter.class)`; inject di HEAD `deserialize` dan
     `serialize` (cancellable) untuk menangani `"type":"minimals_post_effect"` / `PostEffectKeyframe`.
     Deserialize harus set `keyframe.interpolationType(...)` (adapter asli melakukannya setelah switch).
   - `FlashbackGsonMixin` -> `@Mixin(FlashbackGson.class)`; inject di `build()` (private static
     `GsonBuilder`) TAIL untuk `registerTypeAdapter(PostEffectKeyframe.class, new PostEffectKeyframe.TypeAdapter())`.
   - Verifikasi nama method/deskriptor di sumber Flashback sebelum menulis.
3. **Tahap 3: render.** `PostEffectRenderMixin` (@Mixin GameRenderer) inject setelah
   `doEntityOutline()` (seperti `PostEffectMixin`), ambil `PostFxState.beginFrame()`, jalankan tiap efek.
   Perlu pipeline dinamis (blur radius, invert intensity, pixelate size, custom id) dan mode BLOCKS:
   proyeksikan pusat blok ke layar (matriks proyeksi/view; `ReplayUI.lastProjectionMatrix`/
   `lastViewQuaternion` ada tapi private, bisa pakai `Camera`/`GameRenderer` sendiri), lalu mask radial
   dari radius dunia ke piksel. Harus jalan juga saat export video (`Flashback.isExporting()`).
   Pastikan efek tidak tersisa di luar replay (`PostFxState.clear()` saat `!Flashback.isInReplay()`).
4. **Tahap 4 (opsional, polish):** lang/teks, `PostEffectPickMixin` `remap=false` cek (target Flashback,
   bukan MC), disarm `BlockPickMode` saat replay ditutup, indikator kursor saat armed.
5. Update memori/README bila perlu; commit + push ke `main` (user memberi PAT baru tiap push; default
   branch `main`). Jangan commit `libs/*.jar` atau jar Flashback.

## Risiko / belum terverifikasi
- Belum ada compile sama sekali (sandbox tidak bisa). Kemungkinan salah: nama kelas ImGui
  (`imgui.moulberry90.*`, `flag.ImGuiKey`), signature `ImGui.combo/sliderFloat/textColored/textDisabled`
  di binding 1.90, dan `ImGuiKey.H` (di Flashback ada `ImGuiKey.H` di `CustomImGuiImplGlfw`, jadi ada).
- `@Mixin(value = ReplayUI.class, remap = false)` + `remap=false` di inject: benar untuk kelas non-MC.
- `Keyframe.TypeAdapter` adalah nested static class: target mixin `Keyframe.TypeAdapter.class`.
- `imgui-binding-1.90.0.jar` tidak ada di Maven; user harus salin dari jar Flashback terpasang
  (`META-INF/jars/`) ke `libs/`. Tanpa itu build gagal.
- Modrinth Maven mungkin tidak menyediakan artifact dengan version "0.43.4" persis; format sebenarnya
  `maven.modrinth:flashback:<version_id>`. Kalau gagal resolve, ganti dengan version id dari halaman
  versi Modrinth, atau gunakan `compileOnly files('libs/flashback-0.43.4.jar')`.
- Vulkan/RenderPearl hanya ada di 26.3; 26.2 memakai `com.mojang.blaze3d.*` (GL). Jangan pakai
  `com.mojang.renderpearl`.

## Aturan user
- Skill di `/mnt/skills/user/`: `efficient-response-rules` (respons < 100 kata, langsung),
  `minecraft-java-modding-expert`, `smart-coder`. Sertakan **path file lengkap** saat menyebut file.
- Edit minimal (`str_replace`), jangan ubah kode di luar scope.
