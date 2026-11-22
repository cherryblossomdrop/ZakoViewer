# ZakoViewer 鍏ㄥ眬浠ｇ爜绱㈠紩涓庢灦鏋勬槧灏?(Codebase Index)

> **鐗堟湰瀹氫綅**锛氭埅鑷虫渶鏂版瀯寤猴紙`v1.2.50`锛夛紝鍏ㄩ」鐩牳蹇冨伐绋嬫簮鐮佸叡璁?**120** 涓枃浠讹紝绱 **27,109** 琛屼唬鐮併€? 
> **鍗忎綔鍑嗗垯**锛氬湪鎺掓煡鏁呴殰銆佸垎鏋愰€昏緫閾捐矾鎴栬鍒掑姛鑳芥敼鍔ㄥ墠锛?*蹇呴』浼樺厛鏌ラ槄鏈储寮?*蹇€熷畾浣嶅叧閿枃浠躲€傛瘡娆℃柊澧炪€佸垹闄ゃ€侀噸鍛藉悕鎴栦慨鏀逛唬鐮佹枃浠跺悗锛屽繀椤诲悓姝ョ淮鎶ゅ彈褰卞搷鏉＄洰鐨勬枃浠惰矾寰勩€佺簿纭鏁颁笌鏍稿績瀹炵幇鏂瑰紡銆?

---

## 鐩綍绱㈠紩瀵艰埅
1. [鍏ㄥ眬搴旂敤涓灑涓庣郴缁熼厤缃甝(#1-鍏ㄥ眬搴旂敤涓灑涓庣郴缁熼厤缃?
2. [鏍稿績鏋舵瀯涓庡熀寤洪┍鍔?(core/)](#2-鏍稿績鏋舵瀯涓庡熀寤洪┍鍔?core)
3. [鍘熺敓 C/C++ 娓叉煋绠＄嚎 (cpp/)](#3-鍘熺敓-cc-娓叉煋绠＄嚎-cpp)
4. [鏁版嵁鎸佷箙鍖栥€佷粨鍌ㄤ笌缃戠粶鏁版嵁婧?(data/)](#4-鏁版嵁鎸佷箙鍖栦粨鍌ㄤ笌缃戠粶鏁版嵁婧?data)
5. [UI 璁捐浠ょ墝涓庝富棰樹綋绯?(ui/theme/)](#5-ui-璁捐浠ょ墝涓庝富棰樹綋绯?uitheme)
6. [UI 鍏叡澶嶇敤缁勪欢 (ui/components/)](#6-ui-鍏叡澶嶇敤缁勪欢-uicomponents)
7. [UI 瀵艰埅涓庤矾鐢辨嫇鎵?(ui/navigation/)](#7-ui-瀵艰埅涓庤矾鐢辨嫇鎵?uinavigation)
8. [UI 涓氬姟瑙嗙獥涓?ViewModel (ui/screens/)](#8-ui-涓氬姟瑙嗙獥涓?viewmodel-uiscreens)
9. [宸ョ▼鏋勫缓涓?CI/CD 鑷姩鍖栨祦姘寸嚎](#9-宸ョ▼鏋勫缓涓?cicd-鑷姩鍖栨祦姘寸嚎)

---

## 1. 鍏ㄥ眬搴旂敤涓灑涓庣郴缁熼厤缃?

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/AndroidManifest.xml` | 149 | 搴旂敤鍏ㄥ眬娓呭崟锛屾敞鍐?Activity銆佸墠鍙版湇鍔°€佹潈闄愪笌鏂囦欢 Provider | 澹版槑濯掍綋璇诲啓涓庡瓨鍌ㄦ潈闄愩€佹枃浠跺叡浜?FileProvider 鍙?DownloadService |
| `app/src/main/kotlin/moe/zako/viewer/ZakoApplication.kt` | 151 | 搴旂敤绾?Application 鍗曚緥锛屽垵濮嬪寲鍏ㄥ眬鏃ュ織銆佸穿婧冨厹搴曘€丆oil 鍥惧儚鍔犺浇鍙婃牳蹇冩暟鎹粨鍌?| 缁ф壙 `Application` 瀹炵幇 `ImageLoaderFactory`锛屾彁渚涙暟鎹簱涓庝粨鍌?lazy 闂ㄩ潰锛屽惎鍔ㄩ寤烘牴鏀惰棌澶逛笌灏侀潰杩佺Щ |
| `app/src/main/kotlin/moe/zako/viewer/MainActivity.kt` | 99 | 鍗?Activity 鎵胯浇瀹夸富锛屽鐞嗙獥鍙ｅ叏灞忚竟鍒拌竟锛圗dge-to-Edge锛変笌绯荤粺鏍忚瑙夋矇娴?| 鍩轰簬 `ComponentActivity`锛岄€氳繃 `enableEdgeToEdge()` 閰嶇疆鐘舵€佹爮/瀵艰埅鏍忛€忔槑搴︼紝瑁呰浇 `ZakoNavHost` 鏍圭粍鍚堥」 |

---

## 2. 鏍稿績鏋舵瀯涓庡熀寤洪┍鍔?(`core/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSmartRouter.kt` | 79 | 鏅鸿兘 CDN 绾胯矾璺敱涓庢祴閫熺粨鏋滆В鏋愪徊瑁?| 缁存姢澶氳妭鐐瑰厓鏁版嵁锛岀粨鍚?`CdnSpeedTester` 杩斿洖鐨?TTFB 鍜屽甫瀹藉姩鎬佽瘎鍒嗗苟浜у嚭鏈€浣崇嚎璺?|
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSpeedTester.kt` | 171 | 澶氳矾 CDN 鑺傜偣骞跺彂缃戠粶鎺㈡祴涓庢祴閫熷紩鎿?| 鍩轰簬 OkHttp 鍗忕▼骞跺彂鍙戣捣 Range 璇锋眰锛屾帰娴?TCP/TLS 鎻℃墜寤惰繜涓庣獊鍙戜笅琛岄€熺巼 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/PlaybackSourceCoordinator.kt` | 225 | 鍦ㄧ嚎瑙嗛璧锋挱閫夌嚎涓?CDN 娴嬮€熻皟搴﹀崗璋冨櫒 | 缁熺鑷姩娴嬮€熼€変紭銆侀粯璁よ妭鐐圭储寮曚紭闆呭洖閫€銆佹祴閫熼潰鏉夸細璇濇帶鍒朵笌娴佷唬鐞?URL 瑙ｆ瀽闂ㄩ潰 |
| `app/src/main/kotlin/moe/zako/viewer/core/cover/CoverCacheManager.kt` | 437 | 瑙嗛灏侀潰鏈湴鎸佷箙鍖栫紦瀛樸€佺绾胯嚜鎰堜笌鎸夐渶鍔犺浇涓灑 | 鏈湴鏂囦欢 Hash 瀵诲潃瀛樺偍锛屽皝瑁?Coil `ImageRequest` 鎷︽埅鍣紝鏀寔鏃х増绂荤嚎灏侀潰闈欓粯鑷剤 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadManager.kt` | 520 | 绂荤嚎涓嬭浇鍏ㄧ敓鍛藉懆鏈熺鐞嗕腑鏋㈤棬闈紝鍗忚皟浠诲姟璋冨害銆佹柇鐐圭画浼犱笌鍓嶅彴閫氱煡 | 鍗曚緥缁存姢涓嬭浇浠诲姟鐘舵€佹満锛團low 鍝嶅簲寮忛€氱煡锛夛紝閫忎紶瑙嗛鏃堕暱锛岀粺绛规湰鍦板畬鎴愭€佽棰戞娴嬶紝濮旀墭 DownloadMigrationHelper 鎵ц瀛樺偍鐩綍骞虫粦杩佺Щ |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadMigrationHelper.kt` | 237 | 绂荤嚎涓嬭浇鐗╃悊瀛樺偍鐩綍杩佺Щ涓庡巻鍙茬増鏈钩婊戝崌绾у姪鎵?| 鎵挎帴璺ㄧ洰褰曟壒閲忔枃浠惰縼绉汇€丼torageConstants 64KB 娴佸紡瀹夊叏缂撳啿澶嶅埗銆佹暟鎹簱璺緞鍘熷瓙鏇存柊涓庢棫鐗?NeoViewer 鐩綍骞虫粦鍏煎 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadService.kt` | 58 | 绂荤嚎涓嬭浇鍓嶅彴鏈嶅姟锛屼繚闅滃悗鍙颁笅杞界ǔ瀹氫笉琚郴缁熸潃杩涚▼ | 缁ф壙 Android `Service`锛岀粦瀹氬墠鍙伴€氱煡閫氶亾骞跺疄鏃舵樉绀轰笅杞借繘搴︿笌閫熺巼閫氱煡 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/MultiNodeDownloader.kt` | 397 | 澶氳妭鐐瑰苟鍙戝垎鐗囨柇鐐圭画浼犱笅杞藉簳灞傚疄鐜?| 鍩轰簬 OkHttp 娴佸紡鎷夊彇 HTTP Range 鍒嗙墖锛屽绾跨▼骞跺彂鏍￠獙骞跺悎骞跺啓鍏ョ鐩樼洰鏍囨枃浠?|
| `app/src/main/kotlin/moe/zako/viewer/core/external/AppPickerHelper.kt` | 128 | 澶栭儴搴旂敤涓庢挱鏀惧櫒鍖归厤鍔╂墜 | 閫氳繃 `PackageManager` 鏌ヨ鍖归厤鏀寔瑙嗛鎾斁 Intent 鐨勭郴缁熷強绗笁鏂瑰簲鐢ㄥ垪琛?|
| `app/src/main/kotlin/moe/zako/viewer/core/external/ExternalLauncher.kt` | 108 | 鍞よ捣澶栭儴绗笁鏂规挱鏀惧櫒锛堝 VLC銆丮X Player锛夋垨绯荤粺鐩稿唽搴旂敤 | 鏋勯€犲甫鎾斁 MIME 绫诲瀷涓庢巿鏉?URI锛團ileProvider锛夌殑 `ACTION_VIEW` 鏄惧紡/闅愬紡 Intent |
| `app/src/main/kotlin/moe/zako/viewer/core/icon/AppIconManager.kt` | 93 | 妗岄潰鍔ㄦ€佸浘鏍囧埆鍚嶇儹鍒囨崲鍣?| 鎿嶇旱 `PackageManager.setComponentEnabledSetting` 鍒囨崲 Manifest 涓殑 `<activity-alias>` |
| `app/src/main/kotlin/moe/zako/viewer/core/logger/ZakoLog.kt` | 521 | 缁撴瀯鍖栬瘖鏂棩蹇楁鏋讹紝鎻愪緵鍐呭瓨缂撳啿銆佺鐩樻粴鍔ㄨ惤鐩樹笌绯荤粺淇℃伅閲囬泦 | 鍗曚緥灏佽鍙岀鏃ュ織杈撳嚭锛屾敮鎸佹棩蹇楃骇鍒繃婊ゃ€佸叕鍏变笅杞界洰褰曞師瀛愬鍑轰笌宕╂簝璇婃柇澶寸粍瑁?|
| `app/src/main/kotlin/moe/zako/viewer/core/permission/StoragePermissionHelper.kt` | 54 | 澶栭儴瀛樺偍涓庢墍鏈夋枃浠惰闂潈闄愶紙MANAGE_EXTERNAL_STORAGE锛夋鏌ラ€傞厤鍣?| 鍖哄垎 Android 11+锛坄Environment.isExternalStorageManager`锛変笌浣庣増鏈繍琛屾椂鏉冮檺妫€鏌ヤ笌璁剧疆璺宠浆 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVLib.kt` | 179 | libmpv 鍘熺敓 JNI 璺ㄨ瑷€璋冪敤妗ユ | 澹版槑 `create`銆乣init`銆乣destroy`銆乣command`銆乣setOptionString` 绛?JNI 鍘熺敓鏂规硶锛岀簿纭檷绾х‖瑙ｆ帰娴嬪櫔澹?|
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVView.kt` | 146 | libmpv 鍘熺敓瑙嗛 SurfaceView 娓叉煋鎵胯浇缁勪欢 | 缁ф壙 `SurfaceView` 瀹炵幇 `SurfaceHolder.Callback`锛屽崗鍚屽師鐢?OpenGL/EGL 缁樺埗琛ㄩ潰鐢熷懡鍛ㄦ湡 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MultiNodeStreamBuffer.kt` | 690 | 娴佸獟浣撴湰鍦板井鍨嬩唬鐞嗙綉鍏筹紝鎻愪緵鍒嗙墖缂撳啿涓庤竟涓嬭竟鎾?| 鍩轰簬宓屽叆寮?Socket 寤虹珛鏈湴浠ｇ悊锛屽鐞?MPV 鍙戣捣鐨?Range 璇锋眰骞跺苟鍙戞媺鍙栬繙绔簮 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/PlayerEngine.kt` | 124 | 鍐呴儴鎾斁寮曟搸閫傞厤鍣ㄧ粺涓€鎺ュ彛涓庣姸鎬佸畾涔?| 瀹氫箟 `PlayerEngineAdapter` 鎺ュ彛銆乣EngineCallback` 鍥炶皟濂戠害涓庣姸鎬佹祦鏁版嵁妯″瀷 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MpvPlayerEngine.kt` | 537 | libmpv 鍘熺敓娓叉煋鎾斁寮曟搸閫傞厤鍣ㄥ疄鐜?| 鎵胯浇 `MPVLib` JNI 椹卞姩銆丱penGL/EGL 娓叉煋鏃跺簭銆丼eek 鐬€侀槻鎶栦笌闆跺€兼姂鍒躲€乤bsolute 绮剧‘璺宠浆鍙婃棩蹇楄繃婊?|
| `app/src/main/kotlin/moe/zako/viewer/core/player/SystemPlayerEngine.kt` | 414 | 绯荤粺 MediaPlayer 鎾斁寮曟搸閫傞厤鍣ㄥ疄鐜?| 鍚庡彴鐙珛鍗曠嚎绋嬪畨鍏ㄤ覆琛屾淳鍙?IPC銆佷富绾跨▼鍚屾瑙ｇ粦 Surface 鏉滅粷 EGL 浜夋姠銆丼eek 鐬€佷繚鎶や笌绾跨▼瀹夊叏閲婃斁 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/ZakoPlayer.kt` | 356 | 鎾斁鍣ㄦ牳蹇冨澶栬交閲忓皝瑁呴棬闈?| 閫傞厤鍣ㄦā寮忚皟搴?MPV 涓庣郴缁熷紩鎿庯紝鑱氬悎瀵瑰 `StateFlow`锛屽唴缃?`seekTo` 璇婃柇鏃ュ織锛岄殧绂昏法寮曟搸杩囨湡鍥炶皟 |
| `app/src/main/kotlin/moe/zako/viewer/core/search/SearchQueryParser.kt` | 104 | 澶嶅悎鎼滅储璇嶆硶瑙ｆ瀽寮曟搸锛屾敮鎸侀珮绾у竷灏旇娉曚笌鏆楀彿瑙ｆ瀽 | 璇嶆硶鎷嗚В鏌ヨ瀛椾覆涓?Tag/鍏抽敭璇嶈繃婊ゅ櫒锛岃嚜鍔ㄥ梾鎺㈠苟闅旂鎻愬彇 SFW 姹夊瓧闅愬啓鐗瑰緛鐮?|
| `app/src/main/kotlin/moe/zako/viewer/core/share/ShareCipherUtil.kt` | 91 | 璇箟绾?SFW 姹夊瓧闅愬啓缂栬В鐮佸垎浜崗璁伐鍏?| 寤虹珛 Base64/Hex 涓庤嚜鐒舵眽瀛楀瓧搴撶殑楂樼淮鍙屽悜鏄犲皠琛紝瀹炵幇闆跺閾炬晱鎰熸嫤鎴殑鏆楀彿缂栬В鐮?|
| `app/src/main/kotlin/moe/zako/viewer/core/storage/PublicStorageHelper.kt` | 107 | 鍒嗗尯瀛樺偍鍏叡绌洪棿鐩村嚭涓?MediaStore 鍐欏叆閫傞厤鍣?| 閬靛惊 Android 10+ Scoped Storage锛岄€氳繃 `MediaStore.Downloads` 鍐欏叆澶囦唤鍖呬笌瀵煎嚭鏃ュ織锛屽父閲忔敹鏁涘榻?StorageConstants |
| `app/src/main/kotlin/moe/zako/viewer/core/storage/StorageConstants.kt` | 33 | 鍏ㄥ眬瀛樺偍璺緞涓?I/O 缂撳啿鍖烘牳蹇冨父閲忓簳搴?| 闆嗕腑瀹氫箟搴旂敤鍏叡鐩綍锛圸akoViewer锛夈€佸鍑哄瓙鐩綍涓?64KB/32KB 娴佸紡澶勭悊缂撳啿鍖?|

---

## 3. 鍘熺敓 C/C++ 娓叉煋绠＄嚎 (`cpp/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/cpp/CMakeLists.txt` | 32 | CMake 鍘熺敓缂栬瘧鏋勫缓閰嶇疆鑴氭湰 | 澹版槑 `mpv_jni` 鍏变韩搴撶洰鏍囷紝閾炬帴棰勭紪璇?`libmpv`銆乣libandroid`銆乣liblog` 涓?EGL 娓叉煋搴?|
| `app/src/main/cpp/mpv_jni.cpp` | 634 | MPV JNI 妗ユ帴灞備笌浜掓枼閿佸畨鍏ㄥ悓姝ュ疄鐜?| C++17 瀹炵幇 JNI 鎺ュ彛锛岄噰鐢?`std::mutex` 涓庢潯浠跺彉閲忎繚闅?Surface 缁戝畾銆佹覆鏌撹皟鐢ㄤ笌閿€姣佺嚎绋嬪畨鍏?|

---

## 4. 鏁版嵁鎸佷箙鍖栥€佷粨鍌ㄤ笌缃戠粶鏁版嵁婧?(`data/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/data/backup/BackupRestoreManager.kt` | 591 | 璺ㄧ増鏈暟鎹簱鍏ㄩ噺瀵煎嚭涓庡鍏ユ仮澶嶇鐞嗗櫒 | 娴佸紡鍘嬬缉/瑙ｅ帇 ZIP 瀹瑰櫒锛屾牎楠岀増鏈厓鏁版嵁锛屾祦寮忕紦鍐插尯鏀舵暃鑷?StorageConstants锛屽師瀛愬悎骞?瑕嗗啓 Room 琛ㄥ苟淇濇寔灞忓箷甯镐寒鍞ら啋閿?|
| `app/src/main/kotlin/moe/zako/viewer/data/local/ZakoDatabase.kt` | 95 | Room 涓绘暟鎹簱鍏ュ彛涓庡崟渚嬬鐞?| 缁ф壙 `RoomDatabase`锛岃仛鍚?4 寮犳暟鎹〃锛屽崌绾ц嚦鐗堟湰 4 骞舵彁渚?`MIGRATION_3_4` 琛ラ綈瑙嗛鏃堕暱瀛楁 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/FavoriteDao.kt` | 118 | 鏍戝舰鏀惰棌澶逛笌鏀惰棌鏉＄洰鏁版嵁搴撴暟鎹闂璞?(DAO) | 瀹氫箟 Room 鏌ヨ銆佸眰绾ц矾寰勯€掑綊閬嶅巻銆佹壒閲忓鍒?绉诲姩鍙婂敮涓€鏍规敹钘忓す鐨?SQL 浜嬪姟鎿嶄綔 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/OtherDaos.kt` | 131 | 鍘嗗彶璁板綍銆佺◢鍚庡啀鐪嬨€佹悳绱㈣瘝鍘嗗彶涓庣绾夸笅杞?DAO 闆嗗悎 | 鍖呭惈 `WatchHistoryDao`銆乣WatchLaterDao`锛堟敮鎸佹壒閲忕Щ闄わ級銆乣SearchHistoryDao`銆乣DownloadDao` 鐨勫搷搴斿紡 SQL 鎺ュ彛 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/FavoriteEntities.kt` | 47 | 鏍戝舰鏀惰棌澶圭洰褰曞疄浣撲笌鏀惰棌鏉＄洰瀹炰綋瀹氫箟 | Room 瀹炰綋 `FavoriteFolderEntity`锛堣嚜鍏宠仈 `parentId`锛変笌 `FavoriteItemEntity` |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/LocalEntities.kt` | 84 | 鍘嗗彶璁板綍銆佺◢鍚庡啀鐪嬨€佹悳绱㈠巻鍙蹭笌涓嬭浇浠诲姟 Room 瀹炰綋闆嗗悎 | 瀹氫箟 `WatchHistoryEntity`銆乣WatchLaterEntity`銆乣SearchHistoryEntity`銆乣DownloadEntity`锛堝寘鍚?`duration` 瀛楁锛?|
| `app/src/main/kotlin/moe/zako/viewer/data/repository/FavoriteRepository.kt` | 174 | 鏀惰棌浣撶郴涓氬姟浠撳簱锛岀粺涓€缁存姢鏃犻檺绾ф敹钘忓す鎷撴墤涓庡皝闈繚娲?| 灏佽 `FavoriteDao` 寮傛鎿嶄綔锛屾彁渚涘垱寤?閲嶅懡鍚?鍒犻櫎鏂囦欢澶瑰強鏉＄洰澧炲垹涓庡皝闈㈡寔涔呭寲瑙﹀彂 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/HistoryRepository.kt` | 142 | 瑙傜湅鍘嗗彶銆佺◢鍚庡啀鐪嬩笌鎼滅储璁板綍涓氬姟浠撳偍 | 缁熺瑙傜湅璁板綍銆佺◢鍚庡啀鐪嬮槦鍒楀搷搴斿紡鏁版嵁娴併€佸閫夋壒閲忚浆瀛樻敹钘忓す鍙婃悳绱㈣瘝鍘嗗彶锛堥檺鍒?50 鏉★級 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/SettingsRepository.kt` | 276 | 鍏ㄥ眬鐢ㄦ埛鍋忓ソ涓庤缃粨鍌?| 鍩轰簬 Preferences DataStore 鍝嶅簲寮忕鐞嗘挱鏀惧櫒寮曟搸銆佽帿濂堥厤鑹层€佸竷灞€妯″紡 (LayoutMode) 鍙婂叏灞€鍋忓ソ |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosApiClient.kt` | 207 | 瑙嗛婧愭湇鍔＄缃戠粶璇锋眰瀹㈡埛绔?| 灏佽 OkHttp 璁块棶杩滅鍒楄〃銆佹绱㈠強璇︽儏 API锛屾敮鎸佹櫤鑳界嚎璺閫変笌 JSON 鍙嶅簭鍒楀寲 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosCrypto.kt` | 54 | 杩滅鏁版嵁婧愬搷搴旀姤鏂囧姞瑙ｅ瘑绠楁硶宸ュ叿 | 瀹炵幇瑙嗛娴佸湴鍧€涓庡姞瀵嗗厓鏁版嵁鎶ユ枃鐨勫绉拌В瀵嗙畻娉?|
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosModels.kt` | 297 | 杩滅鏈嶅姟涓氬姟鏁版嵁浼犺緭妯″瀷 (DTO) 闆嗗悎 | 瀹氫箟瑙嗛鏉＄洰 `CosVideoItem`銆佽鎯?`CosVideoDetail`銆佽瘎璁恒€佹爣绛惧強鍒嗛〉鍝嶅簲妯″瀷 |

---

## 5. UI 璁捐浠ょ墝涓庝富棰樹綋绯?(`ui/theme/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/AppIcons.kt` | 117 | 鍏ㄥ眬缁熶竴鍥炬爣 Design Token 闂ㄩ潰 | 闆嗕腑缁熶竴绠＄悊搴旂敤鍐呯殑鐭㈤噺鍥炬爣寮曠敤锛岃ˉ榻愰煶閲?鐢诲箙/鍏ㄥ睆/閿佸畾鍙婂父鐢ㄧ郴缁熶笌涓氬姟鍥炬爣骞舵竻鐞嗘湭寮曠敤 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Color.kt` | 440 | 鍏ㄥ眬鑹插僵鎷撴墤銆佽皟鑹叉澘棰勮涓庤帿濂堝姩鎬佸彇鑹叉柟妗?| 瀹氫箟澶氱鍐呯疆涓婚鑹茬洏锛堢粡鍏哥传銆佽嚜鐒剁豢銆佹殩闃抽粍绛夛級鍙婃殫鑹?浜壊 ColorScheme 璇箟 token |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Dimens.kt` | 150 | 鍏ㄥ眬鍩虹灏哄銆侀棿璺濄€佸渾瑙掋€佸浘鏍囧ぇ灏忎笌鏍囬珮璁捐浠ょ墝 | 闆嗕腑瑙勮寖璇箟鍖栭棿璺?Spacing銆佸渾瑙?CornerRadius銆佸浘鏍囧昂瀵?IconSize銆佽Е鎺ф渶灏忚竟鐣屼笌灏侀潰姣斾緥 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/FilterBarTokens.kt` | 14 | 棣栭〉绛涢€夋爮鎿嶄綔鎸夐挳 Design Token | 闆嗕腑瑙勮寖棣栭〉绛涢€夈€佹帓搴忋€佸竷灞€鍒囨崲绛夊渾褰㈡搷浣滄寜閽殑灏哄銆佸浘鏍囧強闂磋窛锛屽簳灞傚榻?Dimens 鍩虹浠ょ墝 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MenuTokens.kt` | 45 | 涓婁笅鏂囪彍鍗曚笌搴曢儴鎿嶄綔鏍忔牱寮?Design Token | 闆嗕腑瑙勮寖闀挎寜鑿滃崟瀹瑰櫒涓庨」鍦嗚銆佹爣楂樸€佸唴杈硅窛銆佸浘鏍囪鏍笺€佹枃瀛楁牱寮忓強鍔ㄦ€佸嵄闄╄壊锛屽簳灞傚榻?Dimens 鍩虹浠ょ墝 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MotionTokens.kt` | 86 | 鍏ㄥ眬寰姩鏁堟椂闀裤€佺紦鍔ㄦ洸绾夸笌杩囨浮瑙勬牸璁捐浠ょ墝 | 涓ユ牸瀵归綈 Material 3 Expressive 瑙勮寖鎸夊帇銆佹贰鍏ユ贰鍑恒€佹棆杞洖寮规椂闀夸笌缂撳姩鏇茬嚎 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/SearchBarTokens.kt` | 40 | 鍏ㄥ眬鎼滅储妗?Design Token | 闆嗕腑瑙勮寖鎼滅储妗嗚兌鍥婂渾瑙掋€佸浘鏍囪鏍笺€佷腑鎬ц壊绾︽潫涓庤Е鎰熷井鍔ㄦ晥鍙傛暟锛屽簳灞傚榻?Dimens 涓?MotionTokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Theme.kt` | 50 | Material 3 涓婚瀹瑰櫒缁勫悎椤逛笌 LocalComposition 娉ㄥ叆 | 鎻愪緵 `ZakoViewerTheme`锛屽皢鍔ㄦ€佽壊褰╂柟妗堛€乑akoTypography 鎺掔増鍙婂皝闈㈡ā绯婄姸鎬佹敞鍏?Compose 鐜鏍?|
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Typography.kt` | 131 | 鍏ㄥ眬鎺掔増鏂囧瓧涓庝笟鍔′笓鐢ㄦ牱寮忚璁′护鐗?| 缁熺 Material 3 鍏ㄥ眰绾ф枃瀛楁帓鐗堣鏍笺€佽棰戞椂闀胯鏍囧強鏃ュ織绛夊鏍峰紡 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/ZakoIcons.kt` | 263 | 鑷畾涔夌煝閲忓浘褰㈣祫浜х粯鍒朵腑鏋?| 鍩轰簬 Compose `ImageVector.Builder` 绾唬鐮佹瀯寤哄叏灞忋€侀€€鍑哄叏灞忋€佺◢鍚庡啀鐪嬪強鐢诲箙姣斾緥鍥炬爣 |

---

## 6. UI 鍏叡澶嶇敤缁勪欢 (`ui/components/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/AppPickerDialog.kt` | 268 | 澶栭儴璋冪敤搴旂敤閫夋嫨鍣ㄥ脊绐?| 灞曠幇绯荤粺鍙婃敮鎸佽棰戞挱鏀剧殑搴旂敤鍒楄〃锛岄『鎵嬫敹鏁?AppIcons.Apps 骞舵帴鍏ユ粦鍔ㄤ笌鐐瑰嚮澶栭儴澶辩劍淇グ绗?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/CustomColorDialog.kt` | 332 | 鏃犵骇鑷畾涔変富棰樿皟鑹茬洏寮圭獥 | 鍩轰簬 HSV/RGB 棰滆壊妯″瀷鎻愪緵鍙鍖栬皟鑹叉粦鍧楋紝鏀寔 Hex 杈撳叆骞跺疄鏃堕瑙堜富棰橀厤鑹?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/DurationBadge.kt` | 93 | 鍏ㄥ眬瑙嗛灏侀潰鏃堕暱瑙掓爣缁勪欢涓庤В鏋愬櫒 | 鍏煎娴偣绉掋€佹暣鏁扮銆佹绉掍笌宸叉牸寮忓寲瀛楃涓诧紝浜庡皝闈㈠彸涓嬭鍛堢幇缁熶竴鍗婇€忔槑鍦嗚瑙掓爣 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FilterBarCircleButton.kt` | 51 | 绛涢€夋爮缁熶竴鍦嗗舰鎿嶄綔鍥炬爣鎸夐挳 | 缁熺绛涢€夈€佹帓搴忎笌甯冨眬鍒囨崲鎸夐挳灏哄銆乀onal 榛樿鐫€鑹诧紙secondaryContainer/onSecondaryContainer锛変笌楂樺姣旀縺娲绘€侊紙primary/onPrimary锛?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderBrowserDialog.kt` | 348 | 鐗╃悊鏂囦欢绯荤粺鐩綍娴忚鍣ㄥ璇濇 | 閬嶅巻澶栭儴瀛樺偍鐩綍鏍戯紝鎻愪緵鏂板缓鐩綍銆佸瓨鍌ㄦ潈闄愬垽瀹氥€佸鐢?ObserveStoragePermissionOnResume 鏉冮檺鎭㈠鐩戝惉涓庝笅杞借矾寰勯€夋嫨 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderTreeDialog.kt` | 198 | 涓氬姟鏀惰棌澶瑰鍙夋爲灞傜骇閫夋嫨寮圭獥 | 閫掑綊灞曞紑鏀惰棌澶圭洰褰曞眰绾э紝鏀寔鐩存帴鏂板缓瀛愭枃浠跺す骞跺揩閫熸寚瀹氭敹钘忓綊灞炵洰鏍?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/LocalFileInfoDialog.kt` | 155 | 鍏ㄥ眬缁熶竴鏈湴瑙嗛鏂囦欢璇︾粏淇℃伅寮圭獥 | 璺?4 鍒楄〃椤靛鐢紝鍛堢幇瑙嗛鏍囬銆佹枃浠跺叏璺緞銆佺鐩樺ぇ灏忋€佸垎杈ㄧ巼鍙婃椂闀夸俊鎭?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/MigrationProgressDialog.kt` | 91 | 璺ㄧ洰褰曡縼绉荤‘璁や笌杩涘害缁熶竴瀵硅瘽妗?| 鏁村悎绂荤嚎涓嬭浇绠＄悊涓庡叏灞€瀛樺偍璁剧疆涓ゅ鐨勭洰褰曡縼绉荤‘璁ゅ強杩涘害灞曠ず锛屼弗鏍兼敹鏁涘昂瀵镐笌闂磋窛鍒?Design Tokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/MultiSelectTopBar.kt` | 59 | 鍏ㄥ眬缁熶竴澶氶€夋€侀《鏍忕粍浠?| 灏佽閫€鍑哄閫夊鑸浘鏍囥€佸凡閫夋暟閲忚鏁般€佸叏閫?鍙嶉€夋搷浣溿€佸姩鎬佽繑鍥為敭鎷︽埅涓?actions 鎵╁睍鎻掓Ы |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ObserveStoragePermissionOnResume.kt` | 50 | 鐩戝惉鐢熷懡鍛ㄦ湡 ON_RESUME 骞堕噸鏂版鏌ュ瓨鍌ㄦ潈闄愮殑缁熶竴鍓綔鐢ㄧ粍浠?| 璺ㄤ笅杞姐€佽缃€佸浠芥仮澶嶅強鐩綍閫夋嫨鍣ㄧ粺涓€澶嶇敤锛屽皝瑁?LifecycleEventObserver 骞朵互 rememberUpdatedState 淇濇姢闂寘 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoActionMenu.kt` | 131 | 鍏ㄥ眬缁熶竴瑙嗛闀挎寜涓婁笅鏂囨搷浣滆彍鍗曠粍浠?| 鍩轰簬 `ModalBottomSheet` 娑堣垂 `MenuTokens`锛屾帴鏀?`List<VideoMenuAction>` 椹卞姩閫夐」涓庡嵄闄╄壊娓叉煋 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoCard.kt` | 148 | 鐎戝竷娴佷笌鍒楄〃閫氱敤瑙嗛鍗＄墖缁勪欢 | 灞曠ず瑙嗛灏侀潰锛堟敮鎸佹ā绯婅劚鏁忥級銆乂IP 鏍囪瘑銆佸鐢?`DurationBadge` 鍛堢幇鏃堕暱鍙婄偣鍑讳簨浠跺垎鍙?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoListItemCard.kt` | 157 | 鍏ㄥ眬缁熶竴妯悜瑙嗛鍒楄〃鍗＄墖缁勪欢 | 缁熺鏀惰棌澶广€佺绾夸笅杞姐€佸巻鍙茶褰曞強绋嶅悗鍐嶇湅 4 澶у垪琛ㄧ殑鍗＄墖灞曠ず楠ㄦ灦锛屾敮鎸佸閫夈€佺粺涓€ 120x68 灏侀潰鑴辨晱銆佹椂闀胯鏍囧強鎻掓Ы鎵╁睍 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/WavyProgressSlider.kt` | 212 | Material 3 Expressive 鍔ㄦ€佸彲鍔ㄦ尝娴樆灏兼粦鍧?| 缁撳悎鍔ㄧ敾杩囨浮涓庢寮︽尝鎸箙锛宺ememberUpdatedState 瑙ｅ喅闂寘杩囨湡骞剁簿鍑嗕笅鍙戣惤鐐规瘮渚?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoBottomBar.kt` | 49 | 搴旂敤搴曢儴瀵艰埅鏍忕粍浠?| 灏佽 NavigationBar锛屾彁渚涢椤点€佹敹钘忥紙浜旇鏄?Token锛夈€佹垜鐨勪笁澶ф牳蹇冩爣绛惧垏鎹?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoSearchBar.kt` | 256 | 鍏ㄥ簲鐢ㄧ粺涓€鑳跺泭鎼滅储妗嗕笌鑷姩澶辩劍鍏叡淇グ绗?| 瑙勮寖鑳跺泭鍦嗚涓庝腑鎬ц壊锛岄泦鎴愯蒋閿洏灞曞紑鏀惰捣鐩戝惉璺冲彉鍒ゅ畾銆佹悳绱㈡彁浜よ嚜鍔ㄥけ鐒︺€佸彲澶嶇敤婊戝姩锛坈learFocusOnScroll锛変笌鐐瑰嚮澶栭儴澶辩劍锛坈learFocusOnTap锛変慨楗扮 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoToast.kt` | 222 | 鍏ㄥ眬闃叉姈鎮诞杞婚噺鎻愮ず缁勪欢 (Toast) | 鍗忕▼閫氶亾闃叉姈鎺掗槦璋冨害锛屾彁渚涘甫鍔ㄧ敾娣″叆娣″嚭鐨勮嚜瀹氫箟鐣岄潰鎮诞鎻愮ず鏉?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/CdnNodeSelectorDialog.kt` | 97 | CDN 鑺傜偣瀹炴椂娴嬮€熶笌绾胯矾鍒囨崲鎶藉眽 | 娑堣垂 `MenuTokens` 涓?`AppIcons`锛岃法鍏ㄥ睆涓庤鎯呴〉鍐呭祵鎾斁鍣ㄥ鐢ㄦ祴閫?鎵嬪姩閫夌嚎 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/GestureOverlays.kt` | 228 | 鎾斁鍣ㄦ墜鍔胯皟鑺傦紙闊抽噺/浜害/蹇繘锛夎瑙夊弽棣堟诞灞?| 鍦ㄦ挱鏀捐鍙ｄ腑澶脊鍑哄甫鍥炬爣涓庣櫨鍒嗘瘮杩涘害鐨勫崐閫忔槑鑳跺泭/鍗＄墖 HUD 鍙嶉 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlaybackSpeedDialog.kt` | 140 | 鎾斁鍊嶉€熻皟鑺傛诞鍔ㄥ脊绐?| 绾︽潫瑙嗗彛楂樺害骞堕樆鏂簳灞傛墜鍔跨┛閫忥紝鎻愪緵浠?0.5x 鍒?3.0x 澶氭。鍊嶉€熷钩婊戝垏鎹?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBottomControls.kt` | 157 | 鍏ㄥ眬鍏变韩鎾斁搴曢儴鎺у埗鏍忎笌娉㈡氮杩涘害鏉?| 缁熺鎾斁/鏆傚仠銆佹椂闂磋繘搴︺€佹尝娴粦鍧椼€佸叏灞忓垏鎹紝闆嗘垚瀵婚亾鐩爣淇濇寔鏉滅粷鐬€侀棯鍥炴棫杩涘害 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBrightnessVolumeHelper.kt` | 161 | 灞忓箷浜害涓庡獟浣撻煶閲忔墜鍔胯皟鎺ц緟鍔╄绠楀櫒 | 缁撳悎 WindowManager 涓?AudioManager锛屾彁渚涚嚎鎬у钩婊戝綊涓€鍖栦笌闃茶烦鍙樿皟鎺ф満鍒?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerControlDefaults.kt` | 31 | 鎾斁鍣ㄥ叏鍩熸帶鍒跺眰鍑犱綍灏哄涓庤瑙夎ˉ鍋胯鑼?| 鏍囧畾鎺т欢鍥炬爣锛圛conSize.Lg锛夈€佽Е鎺у鍣紙TouchTarget.Compact锛夊強寮傚舰鍥炬爣瑙嗚绛夐噸琛ュ伩甯搁噺锛屽榻?Dimens 鍩虹浠ょ墝 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerEngineAndHwDecDialogs.kt` | 114 | 鎾斁鍐呮牳涓庣‖浠惰В鐮佸垏鎹㈠脊绐楅泦 | 鎻愪緵 ExoPlayer/MPV 鎾斁寮曟搸鍒囨崲涓?MPV 纭欢瑙ｇ爜妯″紡閫夋嫨锛岃法鎾斁鍦烘櫙澶嶇敤 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerGestureDetector.kt` | 223 | 鎾斁鍣ㄥ睆骞曟墜鍔跨粺涓€渚︽祴涓庡垎鍙戝櫒 | 鐩戝惉鍙屽嚮銆佸瀭鐩存粦鍔紙宸︿寒鍙抽煶锛変笌姘村钩婊戝姩锛堣繘搴﹀閬擄級锛岄槻璇Е闃绘柇搴曞眰浜嬩欢 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerTopBar.kt` | 147 | 鍏ㄥ眬鍏变韩鎾斁鍣ㄩ《鏍忕粍浠?| 缁熺杩斿洖瀵艰埅銆佽棰戞爣棰樸€佺敾骞?鍐呮牳/瑙ｇ爜璁剧疆鎸夐挳涓庢彃妲芥墿灞?|
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/SpeedBadge.kt` | 58 | 鎾斁鎺у埗鏉″綋鍓嶅€嶉€熸寚绀哄窘鏍?| 绱у噾鍛堢幇褰撳墠鎾斁鍊嶉€熸爣绛撅紝鐐瑰嚮璋冨嚭 `PlaybackSpeedDialog` |

---

## 7. UI 瀵艰埅涓庤矾鐢辨嫇鎵?(`ui/navigation/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/Screen.kt` | 47 | 椤甸潰瀵艰埅璺敱涓庡弬鏁板畾涔夊瘑灏佺被 | 瀹氫箟 Home銆丏etail銆丳layer銆丗avorites銆丠istory銆丏ownloads 绛夐〉闈㈢殑 Route 涓庡弬鏁拌В鏋愭ā鏉匡紝缁熶竴鍘嗗彶璁板綍涓庝簲瑙掓槦鏀惰棌 |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/ZakoNavHost.kt` | 465 | 搴旂敤鏍圭骇澹版槑寮忓鑸浘瀹夸富 | 缁戝畾 `NavHostController`锛岀粺绛归〉闈㈣烦杞姩鏁堜笌 ViewModel 娉ㄥ叆锛岃鎯呴〉璺宠浆鎾斁鍣ㄥ绾﹂€忎紶 forceOnline 寮哄埗鍦ㄧ嚎 |

---

## 8. UI 涓氬姟瑙嗙獥涓?ViewModel (`ui/screens/`)

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailScreen.kt` | 305 | 瑙嗛璇︽儏楠ㄦ灦瑙嗙獥锛屾壙杞藉搷搴斿紡甯冨眬涓庡脊绐楀涓?| 鍝嶅簲寮忓弻鏍?鍗曟爮鎺掑竷锛岀鐞嗗師鍦版í灞忓叏灞忔矇娴革紙閿佸畾寮哄埗鍦ㄧ嚎鐗囨簮锛夈€佽繑鍥炴嫤鎴€佹敹钘忓す鏍戦€夋嫨涓庝笅杞藉脊绐楀涓?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInlinePlayerSection.kt` | 347 | 瑙嗛璇︽儏鍐呭祵鎾斁鍣ㄨ鍙ｅ鍣ㄤ笌鎺у埗灞?| 缁存寔 AndroidView 缁勫悎鏍戠ǔ瀹氾紝澶嶇敤 PlayerTopBar 涓?PlayerBottomControls锛岃惤瀹炲叏灞忓師鍦板睍寮€缁存寔鍦ㄧ嚎婧愬绾?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInfoAndActionsSection.kt` | 411 | 瑙嗛鏍囬銆佹牳蹇冩搷浣滄爮涓庡厓鏁版嵁灞曠ず鍗＄墖 | 鍛堢幇 VIP 寰芥爣銆佸彲澶嶅埗鏍囬锛岄泦鎴愪簲瑙掓槦鏀惰棌/涓嬭浇/绋嶅悗鍐嶇湅/鏆楀彿鍒嗕韩鎿嶄綔鏍忓強鐐瑰嚮妫€绱㈠厓鏁版嵁鑺墖 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailRelatedAndCommentsSection.kt` | 272 | 鐩稿叧鎺ㄨ崘瑙嗛鍒楄〃涓庣暀瑷€鍖轰氦浜掗潰鏉?| 鎶樺彔/灞曞紑鐣欒█璇勮鍖恒€佹í灞忕綉鏍间笌绔栧睆鍒楄〃鐩稿叧瑙嗛娓叉煋锛屽鐢ㄥ叡浜脊绐楃粍浠?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailViewModel.kt` | 713 | 瑙嗛璇︽儏椤电姸鎬佷腑鏋紝璐熻矗璇︽儏鎷夊彇銆佹敹钘忓す褰掑睘銆佺◢鍚庡啀鐪嬩笌涓嬭浇鍒涘缓 | 鑱氬悎鍚勪粨搴撻棬闈紝setFullscreen 濂戠害閿佸畾鍏ㄥ睆缁存寔褰撳墠鍦ㄧ嚎鐗囨簮涓庤繘搴︼紝缁熺閫夌嚎璧锋挱涓庢祴閫熶細璇?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesScreen.kt` | 361 | 鏀惰棌澶规爲鐘剁洰褰曠鐞嗚绐楋紝鏀寔澶氶€夋壒閲忔搷绾典笌闀挎寜鑿滃崟 | 缁熶竴澶氶€夊弽閫夐€昏緫锛岄暱鎸夊浐瀹氳烦杞鎯呴〉銆佹湰鍦拌棰戝脊鍑?`LocalFileInfoDialog`锛屾帴鍏?`MultiSelectTopBar`銆乣ZakoSearchBar` 骞舵寕杞芥粦鍔?鐐瑰嚮鑷姩澶辩劍淇グ绗?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoriteTreeItems.kt` | 250 | 鏀惰棌澶规爲鐘剁粍浠讹紙闈㈠寘灞戣矾寰勬潯銆佸瓙鏀惰棌澶瑰崱鐗囦笌瑙嗛鍗＄墖锛?| 鍛堢幇 `BreadcrumbBar` 璺緞鑺墖銆乣FavoriteFolderItem` 鏂囦欢澶瑰崱鐗囦笌澶嶇敤 `VideoListItemCard` 鐨勮棰戝崱鐗?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesDialogs.kt` | 264 | 鏀惰棌澶逛笟鍔″脊绐椾笌鎿嶄綔鎶藉眽瀹夸富 | 鎻愪緵鏂板缓/閲嶅懡鍚嶆枃浠跺す寮圭獥銆侀噸鍛藉悕瑙嗛寮圭獥鍙婂鐢?`VideoActionBottomSheet` 涓?`MenuTokens` 鐨勯暱鎸夋搷浣滄娊灞?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesViewModel.kt` | 415 | 鏀惰棌澶逛笟鍔＄姸鎬佷腑鏋紝澶勭悊灞傜骇鍒囨崲銆佹壒閲忕Щ鍔?鍒犻櫎涓庢悳绱?| 璋冨害 `FavoriteRepository`锛屽師瀛愭竻绌?`toggleSelectAll()`锛岀粺涓€ `LocalFileInfo` 鏄犲皠 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeScreen.kt` | 414 | 棣栭〉涓婚〉闈㈣绐楋紝璐熻矗鍒楄〃婊氬姩楠ㄦ灦銆佺€戝竷娴?缃戞牸鎺掑竷涓庣姸鎬佹満璋冨害 | 缁存姢涓嬫媺鍒锋柊銆佽Е搴曞垎椤甸鍔犺浇銆佺姸鎬佹満闅旂锛屾寕杞?clearFocusOnScroll 涓?clearFocusOnTap 褰诲簳瑙ｅ喅鎼滅储楂樹寒娈嬬暀 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeSearchBar.kt` | 214 | 棣栭〉鎼滅储杈撳叆妗嗐€佹殫鍙风洿杈炬彁绀轰笌鎼滅储鍘嗗彶浜や簰闈㈡澘 | 褰诲簳绉婚櫎鍚戝彸绠ご锛屽鐢?`ZakoSearchBar` 缁熶竴鑳跺泭鎼滅储妗嗕笌涓€ц壊涓€閿竻绌哄井鍔ㄦ晥锛岀粺绛规悳绱㈠巻鍙插睍寮€銆佹竻绌哄け鐒︿笌鎵归噺鍒犻櫎 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeFilterBar.kt` | 432 | 棣栭〉鍒嗙被鏍囩鏍忋€佹帓搴忔ā寮忓垏鎹€佽鍥惧垏鎹笌缁煎悎绛涢€夋娊灞?| 澶嶇敤 `FilterBarCircleButton` 缁熶竴绛涢€夈€佹帓搴忋€佸竷灞€鍒囨崲鎸夐挳鐫€鑹诧紙M3 Tonal 榛樿鎬?+ 楂樺姣旀縺娲绘€侊級锛屾秷闄よ瑙夊壊瑁備笌鏀舵暃闂磋窛 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeViewModel.kt` | 502 | 棣栭〉鐘舵€佹満涓灑锛岀鐞嗚棰戝垪琛ㄥ垎椤点€佹悳绱㈣瘝鍘嗗彶涓庢殫鍙风洿杈?| 鍗忓悓 `CosApiClient` 涓?`SearchHistoryRepository`锛屾祦寮忓搷搴旀帓搴忔ā寮忎笌鏌ヨ鍏抽敭璇嶆祦 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerScreen.kt` | 370 | 鐙珛鍏ㄥ睆鎾斁瑙嗙獥锛岄泦鎴愭帶鍒跺眰瑕嗙洊鐗┿€丆DN 娴嬮€熼潰鏉夸笌鐢诲箙璋冩暣 | 瑁呰浇 `MPVView` 绾師鐢熸覆鏌撹鍙ｏ紝鍏ㄩ潰澶嶇敤 `PlayerTopBar`銆乣PlayerBottomControls` 鍙婂叡浜脊绐楃粍浠?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerViewModel.kt` | 714 | 鎾斁鍣ㄧ姸鎬佹満涓灑锛岀粺绛硅捣鎾€夌嚎銆佸疄鏃舵祴閫熺儹鍒囥€佽繘搴︿繚瀛樹笌澶栭儴鎾斁 | 椹卞姩 `ZakoPlayer` 涓?`PlaybackSourceCoordinator`锛岀粺绛规湰鍦拌棰戜紭鍏堣捣鎾€佸湪绾胯矾鐢辫捣鎾笌娴嬮€熼潰鏉垮垏鎹?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/ProfileScreen.kt` | 365 | 涓汉涓績涓诲叆鍙ｈ绐?| 鍏ㄥ眬鏇村悕鍘嗗彶璁板綍锛岀粺涓€浜旇鏄熸敹钘忓浘鏍囷紝鎻愪緵绂荤嚎涓嬭浇銆佺◢鍚庡啀鐪嬨€佹暟鎹浠戒笌鍏ㄥ眬璁剧疆绛夊瓙妯″潡鑱氬悎瀵艰埅鍒楄〃 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/AboutScreen.kt` | 191 | 鍏充簬瑙嗙獥锛屽睍绀哄簲鐢ㄧ増鏈俊鎭€佸紑婧愬崗璁笌寮€鍙戣€呴福璋?| 鍖呭惈鐗堟湰淇℃伅銆佹簮鐮佷粨搴撹烦杞強褰╄泲杩炲嚮瑙ｉ攣鏈哄埗 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/LogViewerScreen.kt` | 319 | 瀹炴椂搴旂敤鏃ュ織鏌ョ湅鍣ㄨ绐?| 鎺ュ叆 `ZakoSearchBar` 缁熶竴鎼滅储妗嗗苟鎸傝浇婊戝姩涓庣偣鍑昏嚜鍔ㄥけ鐒︿慨楗扮锛屾彁渚涙棩蹇楀疄鏃舵祦鍒锋柊銆佺骇鍒瓫閫夈€佸叧閿瘝鎼滅储涓庡閮ㄥ鍑哄垎浜?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreDialogs.kt` | 356 | 澶囦唤涓庢仮澶嶅叏娴佺▼寮圭獥涓庤繘搴﹀弽棣堝涓?| 鎵胯浇瀵煎叆/瀵煎嚭鍒嗛樁娈靛疄鏃惰繘搴﹀脊绐椼€佹仮澶嶇粨鏋滄眹鎬诲脊绐椾笌瀵煎嚭鎴愬姛鍒嗕韩寮圭獥锛屽昂瀵镐笌鍥炬爣鍏ㄩ潰鏀舵暃鍒?Design Tokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreScreen.kt` | 323 | 鏁版嵁澶囦唤涓庢仮澶嶈绐?| 寮圭獥瑙ｈ€﹁嚦 BackupRestoreDialogs锛屾帴鍏?ObserveStoragePermissionOnResume锛屾彁渚涘叏閲?ZIP 澶囦唤瀵煎嚭銆佸鍏ユ仮澶嶅強甯镐寒淇濇姢 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreViewModel.kt` | 266 | 鏁版嵁澶囦唤涓庢仮澶嶄笟鍔￠€昏緫鎺у埗鍣?| 璋冨害 `BackupRestoreManager` 鎵ц娴佸紡搴忓垪鍖?鍙嶅簭鍒楀寲锛岀紦鍐插尯鏀舵暃鑷?StorageConstants锛屾毚闇茶繘搴︿笌瀹屾垚寮圭獥鐘舵€?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsScreen.kt` | 352 | 绂荤嚎涓嬭浇绠＄悊瑙嗙獥锛屽垎鏍忕鐞嗗凡涓嬭浇/涓嬭浇涓换鍔★紝鏀寔澶氶€変笌闀挎寜鑿滃崟 | 鎺ュ叆 LocalFileInfoDialog锛岄暱鎸夊浐瀹氭煡鐪嬭鎯咃紝澶嶇敤 MigrationProgressDialog 涓?ObserveStoragePermissionOnResume 鏉冮檺鐩戝惉 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadTaskItems.kt` | 201 | 绂荤嚎涓嬭浇浠诲姟鏉＄洰鍗＄墖缁勪欢锛堝凡涓嬭浇鍗＄墖涓庝笅杞戒腑鍗＄墖锛?| 鍏ㄩ潰澶嶇敤 `VideoListItemCard` 缁熶竴鍗＄墖涓庡皝闈㈡鏋讹紝閫氳繃 bottomContent 鎻掓Ы鎵胯浇涓嬭浇杩涘害鎸囩ず鍣?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsDialogs.kt` | 179 | 绂荤嚎涓嬭浇寮圭獥涓庨暱鎸夋娊灞夊涓?| 鎻愪緵 DownloadActionBottomSheet 鑿滃崟涓庣己澶辨枃浠舵彁绀猴紝杩佺Щ寮圭獥缁熶竴澶嶇敤 MigrationProgressDialog |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsViewModel.kt` | 264 | 绂荤嚎涓嬭浇涓氬姟鐘舵€佷腑鏋紝璐熻矗浠诲姟娴佺洃鍚€佹殏鍋?缁х画/閲嶄笅涓庤法鐩綍杩佺Щ | 鐩戝惉 `DownloadManager` 杩涘害娴侊紝淇 `toggleSelectAll()` 鍒ゅ畾涓庡鐞嗚矾寰勮縼绉?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryScreen.kt` | 330 | 鍘嗗彶璁板綍瑙嗙獥锛屾寜澶╅殧绂诲垎缁勫憟鐜板巻鍙茶褰曪紝鏀寔鎵归噺绠＄悊涓庨暱鎸夎彍鍗?| 澶嶇敤 `VideoListItemCard` 涓?`DurationBadge` 鏃堕暱瑙掓爣銆乣ZakoSearchBar` 鍏ㄥ眬缁熶竴鎼滅储妗嗭紝鎸傝浇婊戝姩涓庣偣鍑昏嚜鍔ㄥけ鐒︿慨楗扮 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryViewModel.kt` | 198 | 鍘嗗彶璁板綍涓氬姟鐘舵€佷腑鏋紝澶勭悊鍘嗗彶妫€绱€佹壒閲忓垹闄ゅ強鏀惰棌澶硅浆瀛?| 璋冨害 `HistoryRepository`锛屾彁渚?`toggleSelectAll()` 鍘熷瓙娓呯┖涓庡閫夋坊鍔犳敹钘忕姸鎬?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsScreen.kt` | 150 | 鍏ㄥ眬璁剧疆涓诲叆鍙ｈ绐楋紝璐熻矗椤舵爮瀵艰埅銆佸垎缁勫崱鐗囨嫾瑁呬笌缂撳瓨娓呯悊寮圭獥瀹夸富 | 浠呬繚鐣欓〉闈㈣剼鎵嬫灦銆佸垎绫诲鑸潯鐩嫾瑁呭強娓呯悊搴旂敤缂撳瓨纭寮圭獥 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsComponents.kt` | 305 | 璁剧疆鍚屽寘鍏变韩鍩虹缁勪欢涓庡脊绐楀鍣?| 鎻愪緵 `SettingsCategoryItem`銆乣SettingsCard`銆乣SettingsSwitchItem`銆乣ExternalAppCard`锛岀Щ闄ら噸澶嶈縼绉诲脊绐楀鐢ㄥ叕鍏辩粍浠?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/AppearanceSettingsSection.kt` | 340 | 澶栬涓庝富棰樿缃绐楋紝鏀寔鑾鍔ㄦ€佽壊銆佽嚜瀹氫箟璋冭壊鐩樹笌鍥炬爣涓婚璺熼殢 | 鏁村悎鑾鍙栬壊寮€鍏炽€佷富棰樿壊棰勮閫夋嫨鍒楄〃銆佽嚜瀹氫箟 HEX 璋冭壊鏉垮強鍥炬爣涓婚鑱斿姩纭 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/PlaybackSettingsSection.kt` | 198 | 鎾斁鏍稿績寮曟搸銆侀暱鎸夊€嶉€熶笌澶栭儴鎾斁鍣ㄩ厤缃绐?| 閰嶇疆闀挎寜鍊嶉€熷€笺€佹挱鏀惧唴鏍搁€夋嫨锛堢郴缁?MPV/鑷姩锛夈€丮PV 纭В妯″紡鍙婅嚜瀹氫箟澶栭儴鎾斁鍣ㄩ€夋嫨瀹夸富 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/DataSourceSettingsSection.kt` | 115 | 鏁版嵁婧愪笌鍦ㄧ嚎娴佸獟浣?CDN 绾胯矾閫変紭璋冨害閰嶇疆鍖哄潡 | 鎻愪緵鑷姩娴嬮€熶紭閫夎妭鐐瑰紑鍏冲強榛樿鍥哄畾 CDN 绾胯矾锛堢嚎璺?1~5锛夌瓫閫夊崟閫夎姱鐗囩粍 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/StorageAndSecuritySettingsSection.kt` | 395 | 瀛樺偍璺緞銆佹枃浠舵潈闄愩€佺绾胯縼绉汇€佸皝闈㈣劚鏁忓強闀挎寜鍒嗕韩閰嶇疆瑙嗙獥 | 鏁村悎闅愮灏侀潰妯＄硦寮€鍏炽€佸畨鍏ㄥ垎浜В閿佸紑鍏炽€佸叏鐩樻枃浠舵潈闄愬崱鐗囷紙澶嶇敤 ObserveStoragePermissionOnResume锛夈€佸鐢?MigrationProgressDialog 鐩綍杩佺Щ寮圭獥 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsViewModel.kt` | 354 | 鍏ㄥ眬閰嶇疆涓氬姟鐘舵€佹満鎺у埗鍣?| 璋冨害 `SettingsRepository`锛屽搷搴斿紡鏇存柊 DataStore 鍋忓ソ骞惰Е鍙戝浘鏍囧埆鍚嶅悓姝?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterScreen.kt` | 316 | 绋嶅悗鍐嶇湅瑙嗙獥锛屽睍鐜板緟鐪嬪垪琛ㄥ苟鏀寔澶氶€夋ā寮忋€佹壒閲忕鐞嗕笌闀挎寜涓婁笅鏂囪彍鍗?| 鎶界 WatchLaterItemCard 澶嶇敤 `VideoListItemCard` 涓?`DurationBadge`锛岀粺涓€闀挎寜鏌ョ湅璇︽儏涓庡閫夌鐞?|
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterViewModel.kt` | 193 | 绋嶅悗鍐嶇湅涓氬姟鎺у埗鍣ㄤ笌澶氶€夌姸鎬佷腑鏋?| 鐩戝惉寰呯湅娴侊紝缁熺澶氶€夋ā寮忓垏鎹€乣toggleSelectAll()` 鍏ㄩ€?鍙嶉€夈€佹壒閲忕Щ鍑哄緟鐪嬪強鑱斿姩 `FavoriteRepository` 鎵归噺鍔犲叆鏀惰棌澶?|

---

## 9. 宸ョ▼鏋勫缓涓?CI/CD 鑷姩鍖栨祦姘寸嚎

| 鏂囦欢璺緞 | 浠ｇ爜琛屾暟 | 鑱岃矗涓庡姛鑳斤紙鍋氫簡浠€涔堬級 | 鏍稿績瀹炵幇鏂瑰紡锛堟€庝箞鍋氾級 |
| :--- | :---: | :--- | :--- |
| `build.gradle.kts` | 5 | 鏍瑰伐绋?Gradle 鏋勫缓鎷撴墤鑴氭湰 | 閰嶇疆鏍圭骇鏋勫缓鑴氭湰涓庡熀纭€鎻掍欢鍒悕澹版槑 |
| `settings.gradle.kts` | 18 | 妯″潡渚濊禆鏍戜笌 Maven 鎻掍欢浠撳簱缂栨帓 | 瀹氫箟宸ョ▼鍚?`NeoViewer`锛岀紪鎺?Google銆丮avenCentral 鍙婃湰鍦版彃浠跺瓨鍌ㄥ簱 |
| `app/build.gradle.kts` | 148 | 鏍稿績搴旂敤妯″潡鏋勫缓鍙傛暟銆佷緷璧栧０鏄庝笌鍙戠増閰嶇疆 | 閰嶇疆 Android SDK 34銆丆Make 鍘熺敓閾炬帴銆丷8 娣锋穯銆佺鍚嶉厤缃強鍔ㄦ€佺増鏈彿閫掑瀛楁锛坴1.2.50 / versionCode 54锛?|
| `.github/workflows/release.yml` | 130 | GitHub Actions 鑷姩鍖栧彂鐗堟祦姘寸嚎宸ヤ綔娴?| 鐩戝惉鐗堟湰 Tag锛坄v*`锛夛紝鑷姩鍖栨瀯寤虹幆澧冿紝鐢熸垚鏇存柊鏃ュ織骞惰嚜鍔ㄥ綊妗ｅ彂甯冪函婧愮爜鍘嬬缉鍖?|
