package com.remna.boost;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Basit çeviri katmanı. Türkçe metin anahtardır; tk / tr / ru / en desteklenir.
 * Dil: kullanıcı seçimi ya da sistem dili (Türkmence → tk, Rusça ve BDT dilleri → ru, Türkçe/Azerice → tr, diğerleri → en).
 */
final class L {
    private L() {}

    static volatile String lang = "tr";
    private static final Map<String, String[]> M = new HashMap<>();

    private static void put(String tr, String en, String ru, String tk) { M.put(tr, new String[]{en, ru, tk}); }

    static {
        put("Oyun Performans Merkezi", "Game Performance Center", "Центр игровой производительности", "Oýun öndürijilik merkezi");
        put("● HAZIR", "● READY", "● ГОТОВО", "● TAÝÝAR");
        put("● KURULUM ", "● SETUP ", "● НАСТРОЙКА ", "● GURNAMA ");
        put("● OYUNDA", "● IN GAME", "● В ИГРЕ", "● OÝUNDA");
        put("OYUNDA", "IN GAME", "В ИГРЕ", "OÝUNDA");
        put("SICAKLIK", "TEMP", "ТЕМП.", "GYZGYNLYK");
        put("OYUN SAĞLIĞI", "GAME HEALTH", "ЗДОРОВЬЕ ИГРЫ", "OÝUN SAGLYGY");
        put("ÖLÇÜLÜYOR", "MEASURING", "ИЗМЕРЕНИЕ", "ÖLÇELÝÄR");
        put("MÜKEMMEL", "EXCELLENT", "ОТЛИЧНО", "AJAÝYP");
        put("İYİ", "GOOD", "ХОРОШО", "GOWY");
        put("ORTA", "FAIR", "СРЕДНЕ", "ORTAÇA");
        put("ZAYIF", "POOR", "СЛАБО", "GOWŞAK");
        put("AĞ", "NETWORK", "СЕТЬ", "TOR");
        put("ISI", "HEAT", "ТЕПЛО", "ÝYLYLYK");
        put("PROFİL", "PROFILE", "ПРОФИЛЬ", "PROFIL");
        put("HIZLI ERİŞİM", "QUICK ACCESS", "БЫСТРЫЙ ДОСТУП", "ÇALT GIRIŞ");
        put("Performans", "Performance", "Производительность", "Öndürijilik");
        put("Ağ", "Network", "Сеть", "Tor");
        put("Panel", "Overlay", "Панель", "Panel");
        put("Dengeli", "Balanced", "Баланс", "Deňagramly");
        put("Serin", "Cool", "Охлаждение", "Salkyn");
        put("En yüksek FPS", "Max FPS", "Макс. FPS", "Iň ýokary FPS");
        put("Stabil 60 FPS", "Stable 60 FPS", "Стабильные 60 FPS", "Durnukly 60 FPS");
        put("Az ısınma", "Less heat", "Меньше нагрев", "Az gyzma");
        put("Özel", "Custom", "Свой", "Ýörite");
        put("Özel ayarlar kullanılıyor · Ayarlar sekmesinden düzenlendi", "Custom settings · edited in Settings", "Свои настройки · изменены в Настройках", "Ýörite sazlamalar · Sazlamalarda üýtgedildi");
        put(" profili uygulandı", " profile applied", " — профиль применён", " profili ulanyldy");
        put(" (Android oyun modu için Shizuku gerekli)", " (Shizuku needed for Android game mode)", " (для игрового режима Android нужен Shizuku)", " (Android oýun tertibi üçin Shizuku gerek)");
        put("FPS dalgalanıyor (%1 düşük ", "FPS unstable (1% low ", "FPS нестабилен (1% low ", "FPS durnuksyz (1% pes ");
        put("FPS kararlı", "FPS stable", "FPS стабилен", "FPS durnukly");
        put("Ping yüksek (", "High ping (", "Высокий пинг (", "Ping ýokary (");
        put("Bağlantı dalgalı (dalgalanma ", "Unstable connection (jitter ", "Нестабильное соединение (джиттер ", "Baglanyşyk durnuksyz (yrgyldy ");
        put("Paket kaybı %", "Packet loss %", "Потеря пакетов %", "Paket ýitgisi %");
        put("Ağ stabil", "Network stable", "Сеть стабильна", "Tor durnukly");
        put("Sıcaklık yüksek (%.0f°C)", "High temperature (%.0f°C)", "Высокая температура (%.0f°C)", "Gyzgynlyk ýokary (%.0f°C)");
        put("Sıcaklık normal", "Temperature normal", "Температура в норме", "Gyzgynlyk kadaly");
        put("Boş RAM az (%", "Low free RAM (%", "Мало свободной RAM (%", "Boş RAM az (%");
        put("Yenileme hızını 90 Hz'e düşür ya da Serin profili seç.", "Lower the refresh rate to 90 Hz or choose the Cool profile.", "Снизьте частоту до 90 Гц или выберите профиль «Охлаждение».", "Täzeleniş ýygylygyny 90 Hz-e düşür ýa-da Salkyn profilini saýla.");
        put("Dengeli profili dene: FPS 60'a sabitlenir, dalgalanma azalır.", "Try the Balanced profile: FPS locked to 60, less fluctuation.", "Попробуйте профиль «Баланс»: FPS фиксируется на 60, меньше скачков.", "Deňagramly profilini synap gör: FPS 60-a berkidilýär, yrgyldy azalýar.");
        put("Ağ sekmesinden en düşük ve en stabil bölgeyi bul.", "Find the lowest and most stable region in the Network tab.", "Найдите самый быстрый и стабильный регион во вкладке «Сеть».", "Tor bölüminden iň pes we iň durnukly sebiti tap.");
        put("Akıllı temizliği aç ya da arka plandaki uygulamaları kapat.", "Enable smart cleanup or close background apps.", "Включите умную очистку или закройте фоновые приложения.", "Akylly arassalamany aç ýa-da fondaky programmalary ýap.");
        put("FPS ölçülemiyor bu cihazda (Gelişmiş → Shizuku)\n", "FPS unavailable on this device (Advanced → Shizuku)\n", "FPS недоступен на этом устройстве (Дополнительно → Shizuku)\n", "Bu enjamda FPS ölçüp bolmaýar (Giňişleýin → Shizuku)\n");
        put("FPS oyun sırasında ölçülür\n", "FPS is measured during the game\n", "FPS измеряется во время игры\n", "FPS oýun wagtynda ölçelýär\n");
        put("\nÖneri: ", "\nTip: ", "\nСовет: ", "\nMaslahat: ");
        put("Kapalı", "Off", "Выкл.", "Öçük");
        put("Minimal · FPS • ping", "Minimal · FPS • ping", "Минимум · FPS • пинг", "Minimal · FPS • ping");
        put("Ağ · ping, dalgalanma, kayıp", "Network · ping, jitter, loss", "Сеть · пинг, джиттер, потери", "Tor · ping, yrgyldy, ýitgi");
        put("Performans · FPS, RAM, CPU, pil", "Performance · FPS, RAM, CPU, battery", "Производ. · FPS, RAM, CPU, батарея", "Öndürijilik · FPS, RAM, CPU, batareýa");
        put("Tam · hepsi + kare süresi", "Full · all + frame time", "Полный · всё + время кадра", "Doly · hemmesi + kadr wagty");
        put("Oyun üstü panel", "Game overlay", "Игровая панель", "Oýun paneli");
        put("Panel bir sonraki oyun modunda değişir", "Overlay changes in the next session", "Панель изменится в следующей сессии", "Panel indiki oýun tertibinde üýtgär");
        put("En düşük ve en stabil mevcut bölgeyi analiz eder. Fiziksel gecikmeyi değiştirmez; doğru sonuç için VPN kapalıyken ölç.", "Analyzes the lowest and most stable available region. It doesn't change physical latency; measure with VPN off for accurate results.", "Анализирует самый быстрый и стабильный доступный регион. Не меняет физическую задержку; для точности измеряйте без VPN.", "Elýeterli iň pes we iň durnukly sebiti seljerýär. Fiziki gijikmäni üýtgetmeýär; dogry netije üçin VPN öçükkä ölçe.");
        put("Tüm bölgeleri test et", "Test all regions", "Проверить все регионы", "Ähli sebitleri barla");
        put("Ortanca · dalgalanma · kayıp · kararlılık · skor", "Median · jitter · loss · stability · score", "Медиана · джиттер · потери · стабильность · оценка", "Mediana · yrgyldy · ýitgi · durnuklylyk · bal");
        put("Oyun sunucusu", "Game server", "Игровой сервер", "Oýun serweri");
        put("Maça girince PUBG'nin bağlandığı sunucu burada görünür", "The server PUBG connects to appears here once a match starts", "Сервер PUBG появится здесь после начала матча", "Oýun başlanda PUBG-iň birikýän serweri şu ýerde görüner");
        put("Ağ geçmişi", "Network history", "История сети", "Tor taryhy");
        put("Bugün · 7 gün · 30 gün · bölge istatistikleri", "Today · 7 days · 30 days · region stats", "Сегодня · 7 дней · 30 дней · статистика регионов", "Şu gün · 7 gün · 30 gün · sebit statistikasy");
        put("Sunucu geçmişi", "Server history", "История серверов", "Serwer taryhy");
        put("Oynadıkça bağlanılan sunucular bu cihazda saklanır", "Servers you connect to are stored on this device", "Серверы сохраняются только на этом устройстве", "Birikilen serwerler diňe şu enjamda saklanýar");
        put("Ping bölgesi", "Ping region", "Регион пинга", "Ping sebiti");
        put("SUNUCULAR", "SERVERS", "СЕРВЕРЫ", "SERWERLER");
        put("CANLI · oyun modu açık", "LIVE · game mode on", "ОНЛАЙН · игровой режим", "GÖNI · oýun tertibi açyk");
        put("Oyun modu kapalı · son oturum verileri", "Game mode off · last session data", "Игровой режим выкл. · данные прошлой сессии", "Oýun tertibi öçük · soňky sessiýa");
        put("%1 DÜŞÜK", "1% LOW", "1% LOW", "1% PES");
        put("ORT", "AVG", "СРЕД", "ORT");
        put("KARE", "FRAME", "КАДР", "KADR");
        put("Yok", "N/A", "Нет", "Ýok");
        put("FPS bu oturumda ölçülemedi.", "FPS was not measured this session.", "FPS не измерен в этой сессии.", "Bu sessiýada FPS ölçülmedi.");
        put("FPS ölçümü bu cihazda kullanılamıyor (Gelişmiş → Shizuku).", "FPS measurement unavailable on this device (Advanced → Shizuku).", "Измерение FPS недоступно (Дополнительно → Shizuku).", "Bu enjamda FPS ölçmek mümkin däl (Giňişleýin → Shizuku).");
        put("PİL", "BATTERY", "БАТАРЕЯ", "BATAREÝA");
        put("KARE SÜRESİ (en yavaş kare)", "FRAME TIME (slowest frame)", "ВРЕМЯ КАДРА (самый медленный)", "KADR WAGTY (iň haýal kadr)");
        put("Takılma (25 ms+): ", "Stutters (25 ms+): ", "Подтормаживания (25 мс+): ", "Säginme (25 ms+): ");
        put("   ·   Ağır kare (33 ms+): ", "   ·   Heavy frames (33 ms+): ", "   ·   Тяжёлые кадры (33 мс+): ", "   ·   Agyr kadr (33 ms+): ");
        put("   ✓ akıcı", "   ✓ smooth", "   ✓ плавно", "   ✓ endigan");
        put("Grafikler oyun modu açıkken oluşur ve yalnız bellekte tutulur.", "Graphs build while game mode is on and are kept only in memory.", "Графики строятся в игровом режиме и хранятся только в памяти.", "Grafikler oýun tertibi açykka döreýär we diňe ýatda saklanýar.");
        put("SON OTURUM RAPORU", "LAST SESSION REPORT", "ОТЧЁТ ПОСЛЕДНЕЙ СЕССИИ", "SOŇKY SESSIÝA HASABATY");
        put("PERFORMANS", "PERFORMANCE", "ПРОИЗВОДИТЕЛЬНОСТЬ", "ÖNDÜRIJILIK");
        put("Yenileme hızı", "Refresh rate", "Частота обновления", "Täzeleniş ýygylygy");
        put("Otomatik (sistem yönetsin)", "Auto (system)", "Авто (система)", "Awtomatik (ulgam)");
        put("En yüksek (", "Maximum (", "Максимум (", "Iň ýokary (");
        put("Arka plan temizliği", "Background cleanup", "Фоновая очистка", "Fon arassalaýyş");
        put("Akıllı (yalnız RAM azsa)", "Smart (only when RAM is low)", "Умная (только при нехватке RAM)", "Akylly (diňe RAM az bolsa)");
        put("Agresif (her zaman)", "Aggressive (always)", "Агрессивная (всегда)", "Agressiw (hemişe)");
        put("Termal koruma", "Thermal protection", "Термозащита", "Termal gorag");
        put("Standart · 40 / 42 / 44°C", "Standard · 40 / 42 / 44°C", "Стандарт · 40 / 42 / 44°C", "Standart · 40 / 42 / 44°C");
        put("Hassas · 38 / 40 / 42°C", "Sensitive · 38 / 40 / 42°C", "Чувствительная · 38 / 40 / 42°C", "Duýgur · 38 / 40 / 42°C");
        put("Dokunma optimizasyonu", "Touch optimization", "Оптимизация касаний", "Degiş optimizasiýasy");
        put("Ekran dokunuşlara daha hızlı tepki verir", "Screen responds faster to touch", "Экран быстрее реагирует на касания", "Ekran degişlere has çalt jogap berýär");
        put("Bu cihazda desteklenmiyor", "Not supported on this device", "Не поддерживается на этом устройстве", "Bu enjamda goldanmaýar");
        put("Otomatik parlaklığı kapat", "Disable auto brightness", "Отключить автояркость", "Awtomatik ýagtylygy öçür");
        put("Oyunda parlaklık zıplamaz", "Brightness stays steady in game", "Яркость не прыгает в игре", "Oýunda ýagtylyk üýtgemeýär");
        put("OYUN SIRASINDA", "DURING GAME", "ВО ВРЕМЯ ИГРЫ", "OÝUN WAGTYNDA");
        put("Rahatsız etme", "Do not disturb", "Не беспокоить", "Biynjalyk etme");
        put("Öncelikli", "Priority", "Приоритет", "Ileri tutulýan");
        put("Sadece alarmlar", "Alarms only", "Только будильники", "Diňe duýduryşlar");
        put("Tam sessiz", "Total silence", "Полная тишина", "Doly sessiz");
        put("Panel boyutu", "Overlay size", "Размер панели", "Panel ölçegi");
        put("Küçük", "Small", "Маленький", "Kiçi");
        put("Normal", "Normal", "Обычный", "Adaty");
        put("Büyük", "Large", "Большой", "Uly");
        put("Panel saydamlığı", "Overlay opacity", "Прозрачность панели", "Panel aýdyňlygy");
        put("Oyundan çıkınca kapat", "Stop after leaving game", "Выключать после выхода", "Oýundan çykanda ýap");
        put("Kapalı (elle kapat)", "Off (stop manually)", "Выкл. (вручную)", "Öçük (el bilen ýap)");
        put("15 sn sonra", "After 15 s", "Через 15 с", "15 sek soň");
        put("30 sn sonra", "After 30 s", "Через 30 с", "30 sek soň");
        put("60 sn sonra", "After 60 s", "Через 60 с", "60 sek soň");
        put("UYGULAMA", "APP", "ПРИЛОЖЕНИЕ", "PROGRAMMA");
        put("İzinler", "Permissions", "Разрешения", "Rugsatlar");
        put("Hangi izin ne için kullanılır", "What each permission is for", "Для чего нужно каждое разрешение", "Haýsy rugsat näme üçin");
        put("Gizlilik", "Privacy", "Конфиденциальность", "Gizlinlik");
        put("Sunucu geçmişi, konum sorgusu, veri silme", "Server history, geolocation, data deletion", "История серверов, геолокация, удаление данных", "Serwer taryhy, ýerleşiş, maglumat pozmak");
        put("Tanılama", "Diagnostics", "Диагностика", "Diagnostika");
        put("Sistem durumu ve kopyalanabilir rapor", "System status and copyable report", "Состояние системы и отчёт", "Ulgam ýagdaýy we göçürip bolýan hasabat");
        put("Ana ekrana \"Boost & Oyna\"", "Home screen \"Boost & Play\"", "Ярлык «Boost и играть»", "Baş ekrana \"Boost & Oýna\"");
        put("Tek dokunuşla boost edip oyunu açar", "Boost and launch the game in one tap", "Буст и запуск игры одним касанием", "Bir basyşda boost edip oýny açýar");
        put("GELİŞMİŞ  ▾", "ADVANCED  ▾", "ДОПОЛНИТЕЛЬНО  ▾", "GIŇIŞLEÝIN  ▾");
        put("GELİŞMİŞ  ▸", "ADVANCED  ▸", "ДОПОЛНИТЕЛЬНО  ▸", "GIŇIŞLEÝIN  ▸");
        put("Shizuku gerektiren sistem seviyesi ayarlar. Normal kullanım için gerekmez.", "System-level settings that need Shizuku. Not required for normal use.", "Системные настройки, требующие Shizuku. Для обычного использования не нужны.", "Shizuku talap edýän ulgam sazlamalary. Adaty ulanyş üçin gerek däl.");
        put("Android oyun modu", "Android game mode", "Игровой режим Android", "Android oýun tertibi");
        put("Standart", "Standard", "Стандарт", "Standart");
        put("Pil tasarrufu", "Battery saver", "Экономия батареи", "Batareýa tygşytlaýyş");
        put("Render çözünürlüğü", "Render resolution", "Разрешение рендера", "Render çözgüdi");
        put("Değiştirme", "Don't change", "Не менять", "Üýtgetme");
        put(" FPS'e sabitle", " FPS lock", " FPS (фикс.)", " FPS-e berkit");
        put("FPS sabitleme", "FPS lock", "Фиксация FPS", "FPS berkitmek");
        put("Animasyon hızı", "Animation speed", "Скорость анимации", "Animasiýa tizligi");
        put("Hızlı (0.5x)", "Fast (0.5x)", "Быстро (0.5x)", "Çalt (0.5x)");
        put("Kapalı (0x)", "Off (0x)", "Выкл. (0x)", "Öçük (0x)");
        put("FPS ölçümü", "FPS measurement", "Измерение FPS", "FPS ölçemek");
        put("SurfaceFlinger üzerinden · Shizuku", "via SurfaceFlinger · Shizuku", "через SurfaceFlinger · Shizuku", "SurfaceFlinger arkaly · Shizuku");
        put("Remna Boost oyun dosyalarına dokunmaz. FPS'i sihirli şekilde artırmaz; ölçer, kesintileri engeller, ısınmayı yönetir ve değiştirdiği her ayarı oyun bitince geri yükler.", "Remna Boost never touches game files. It doesn't magically raise FPS; it measures, blocks interruptions, manages heat and restores every setting it changes when the game ends.", "Remna Boost не трогает файлы игры и не повышает FPS «волшебно»: он измеряет, блокирует помехи, управляет нагревом и восстанавливает все изменённые настройки после игры.", "Remna Boost oýun faýllaryna degmeýär. FPS-i jady bilen ýokarlandyrmaýar; ölçeýär, päsgelçilikleri bökdeýär, gyzmany dolandyrýar we üýtgeden ähli sazlamalaryny oýundan soň dikeldýär.");
        put("Oyun üstü panel izni", "Overlay permission", "Разрешение на панель", "Panel rugsady");
        put("Kullanım erişimi (otomatik kapanma, rapor)", "Usage access (auto stop, report)", "Доступ к статистике (автостоп, отчёт)", "Ulanyş rugsady (awtomatik ýapmak, hasabat)");
        put("Rahatsız etme erişimi", "Do Not Disturb access", "Доступ к «Не беспокоить»", "Biynjalyk etme rugsady");
        put("Pro özellikler (isteğe bağlı)", "Pro features (optional)", "Pro-функции (необязательно)", "Pro mümkinçilikler (islege görä)");
        put("FPS göstergesi · sunucu kaydı · Android oyun modu · Shizuku ile", "FPS meter · server log · Android game mode · with Shizuku", "Счётчик FPS · журнал серверов · игровой режим · через Shizuku", "FPS görkeziji · serwer ýazgysy · Android oýun tertibi · Shizuku bilen");
        put("Nasıl? ›", "How? ›", "Как? ›", "Nädip? ›");
        put("Kurulumu tamamla", "Finish setup", "Завершите настройку", "Gurnamany tamamla");
        put("İzinlere git ›", "Go to permissions ›", "К разрешениям ›", "Rugsatlara geç ›");
        put("CİHAZ ANALİZİ", "ANALYZING DEVICE", "АНАЛИЗ УСТРОЙСТВА", "ENJAM SELJERMESI");
        put("RAM KONTROLÜ", "CHECKING RAM", "ПРОВЕРКА RAM", "RAM BARLAGY");
        put("AĞ KONTROLÜ", "CHECKING NETWORK", "ПРОВЕРКА СЕТИ", "TOR BARLAGY");
        put("SICAKLIK KONTROLÜ", "CHECKING THERMALS", "ПРОВЕРКА ТЕМПЕРАТУРЫ", "GYZGYNLYK BARLAGY");
        put("OPTİMİZASYON", "OPTIMIZING", "ОПТИМИЗАЦИЯ", "OPTIMIZASIÝA");
        put("Arka plan temizliği · %d uygulama · +%d MB", "Background cleanup · %d apps · +%d MB", "Фоновая очистка · %d прил. · +%d МБ", "Fon arassalaýyş · %d programma · +%d MB");
        put("Temizlik kapalı", "Cleanup off", "Очистка выключена", "Arassalaýyş öçük");
        put("Temizlik gerekmedi · RAM yeterli (%", "Cleanup not needed · enough RAM (%", "Очистка не нужна · RAM достаточно (%", "Arassalamak gerek däl · RAM ýeterlik (%");
        put(" boş)", " free)", " свободно)", " boş)");
        put("Termal koruma · şu an ", "Thermal protection · now ", "Термозащита · сейчас ", "Termal gorag · häzir ");
        put("Android oyun modu · ", "Android game mode · ", "Игровой режим Android · ", "Android oýun tertibi · ");
        put("Android oyun modu · Shizuku gerekli", "Android game mode · needs Shizuku", "Игровой режим Android · нужен Shizuku", "Android oýun tertibi · Shizuku gerek");
        put("Yenileme hızı · ", "Refresh rate · ", "Частота обновления · ", "Täzeleniş ýygylygy · ");
        put("Sabit yenileme hızı · ADB izni gerekli", "Fixed refresh rate · needs ADB permission", "Фикс. частота · нужно разрешение ADB", "Berk ýygylyk · ADB rugsady gerek");
        put("Rahatsız etme · izin gerekli", "Do not disturb · permission needed", "Не беспокоить · нужно разрешение", "Biynjalyk etme · rugsat gerek");
        put("Dokunma hassasiyeti", "Touch sensitivity", "Чувствительность касаний", "Degiş duýgurlygy");
        put("Dokunma hassasiyeti · ADB izni gerekli", "Touch sensitivity · needs ADB permission", "Чувствительность касаний · нужно разрешение ADB", "Degiş duýgurlygy · ADB rugsady gerek");
        put("RAM  %s boş   ·   PING  %s   ·   SICAKLIK  %.0f°C%n%n", "RAM  %s free   ·   PING  %s   ·   TEMP  %.0f°C%n%n", "RAM  %s своб.   ·   ПИНГ  %s   ·   ТЕМП.  %.0f°C%n%n", "RAM  %s boş   ·   PING  %s   ·   GYZGYNLYK  %.0f°C%n%n");
        put("Uygulananlar:\n", "Applied:\n", "Применено:\n", "Ulanyldy:\n");
        put("\nUygulanmayanlar:\n", "\nNot applied:\n", "\nНе применено:\n", "\nUlanylmady:\n");
        put("✓ BOOST TAMAMLANDI", "✓ BOOST COMPLETE", "✓ БУСТ ЗАВЕРШЁН", "✓ BOOST TAMAMLANDY");
        put("OYUNU BAŞLAT", "START GAME", "ЗАПУСТИТЬ ИГРУ", "OÝNY BAŞLAT");
        put("Kapat", "Close", "Закрыть", "Ýap");
        put("PUBG Mobile yüklü değil", "PUBG Mobile not installed", "PUBG Mobile не установлен", "PUBG Mobile gurnalmadyk");
        put("PUBG bulunamadı", "PUBG not found", "PUBG не найден", "PUBG tapylmady");
        put("Ping ve diğer özellikler çalışıyor. FPS göstergesi için isteğe bağlı Shizuku gerekir.", "Ping and other features work. The FPS meter needs optional Shizuku.", "Пинг и другие функции работают. Для FPS нужен Shizuku (необязательно).", "Ping we beýleki mümkinçilikler işleýär. FPS üçin islege görä Shizuku gerek.");
        put("Oyun seç", "Choose game", "Выберите игру", "Oýun saýla");
        put(" profili yüklendi", " profile loaded", " — профиль загружен", " profili ýüklendi");
        put(" için yeni profil (mevcut ayarlar kopyalandı)", ": new profile (current settings copied)", ": новый профиль (скопированы текущие)", " üçin täze profil (häzirki sazlamalar göçürildi)");
        put("  •  değiştirmek için dokun", "  •  tap to change", "  •  нажмите, чтобы сменить", "  •  üýtgetmek üçin bas");
        put("İZİNLER", "PERMISSIONS", "РАЗРЕШЕНИЯ", "RUGSATLAR");
        put("✓ AÇIK", "✓ ON", "✓ ВКЛ.", "✓ AÇYK");
        put("✕ KAPALI", "✕ OFF", "✕ ВЫКЛ.", "✕ ÖÇÜK");
        put("○ İSTEĞE BAĞLI", "○ OPTIONAL", "○ НЕОБЯЗ.", "○ ISLEGE GÖRÄ");
        put("GEREKLİ", "REQUIRED", "ОБЯЗАТЕЛЬНО", "HÖKMANY");
        put("İSTEĞE BAĞLI", "OPTIONAL", "НЕОБЯЗАТЕЛЬНО", "ISLEGE GÖRÄ");
        put("ETKİNLEŞTİR", "ENABLE", "ВКЛЮЧИТЬ", "IŞJEŇLEŞDIR");
        put("Oyun sırasında FPS, ping ve sıcaklık panelini göstermek için.", "To show the FPS, ping and temperature overlay during games.", "Для показа панели FPS, пинга и температуры в игре.", "Oýun wagtynda FPS, ping we gyzgynlyk panelini görkezmek üçin.");
        put("Kullanım erişimi", "Usage access", "Доступ к статистике", "Ulanyş rugsady");
        put("Oyunun kapandığını algılayıp ayarları geri yüklemek için.", "To detect when the game closes and restore settings.", "Чтобы определить закрытие игры и восстановить настройки.", "Oýnuň ýapylanyny bilip, sazlamalary dikeltmek üçin.");
        put("Oyun sırasında bildirim ve aramaları susturmak için.", "To silence notifications and calls during games.", "Чтобы заглушить уведомления и звонки в игре.", "Oýun wagtynda bildirişleri we jaňlary sessizlemek üçin.");
        put("Sistem ayarları", "System settings", "Системные настройки", "Ulgam sazlamalary");
        put("Otomatik parlaklığı oyun boyunca kapatmak için.", "To turn off auto brightness during games.", "Чтобы отключать автояркость во время игры.", "Oýun wagtynda awtomatik ýagtylygy öçürmek üçin.");
        put("Bağlı değil. ", "Not connected. ", "Не подключён. ", "Birikmedik. ");
        put("Kurulu değil ya da çalışmıyor. ", "Not installed or not running. ", "Не установлен или не запущен. ", "Gurnalmadyk ýa-da işlemeýär. ");
        put("FPS ölçümü, Android oyun modu ve sunucu tespiti gibi gelişmiş özellikler için.", "For advanced features like FPS measurement, Android game mode and server detection.", "Для FPS, игрового режима Android и определения серверов.", "FPS ölçemek, Android oýun tertibi we serwer kesgitlemek ýaly giňişleýin mümkinçilikler üçin.");
        put("Güvenli ayarlar (ADB)", "Secure settings (ADB)", "Защищённые настройки (ADB)", "Goragly sazlamalar (ADB)");
        put("Sabit yenileme hızı, animasyon ve dokunma optimizasyonu için. Shizuku bağlıysa otomatik verilir.", "For fixed refresh rate, animations and touch optimization. Granted automatically if Shizuku is connected.", "Для фикс. частоты, анимаций и касаний. Выдаётся автоматически при подключённом Shizuku.", "Berk ýygylyk, animasiýa we degiş optimizasiýasy üçin. Shizuku birikse awtomatik berilýär.");
        put("GİZLİLİK", "PRIVACY", "КОНФИДЕНЦИАЛЬНОСТЬ", "GIZLINLIK");
        put("Sunucu geçmişi kaydı", "Server history logging", "Запись истории серверов", "Serwer taryhyny ýazmak");
        put("PUBG'nin bağlandığı sunucu IP'leri yalnız bu cihazda saklanır", "Server IPs PUBG connects to are stored only on this device", "IP серверов PUBG хранятся только на этом устройстве", "PUBG serwer IP-leri diňe şu enjamda saklanýar");
        put("Bölge bazında ping özetleri (IP yok) · bu cihazda", "Per-region ping summaries (no IPs) · on this device", "Сводки пинга по регионам (без IP) · на устройстве", "Sebit boýunça ping jemleri (IP ýok) · şu enjamda");
        put("Çevrimiçi konum sorgusu", "Online geolocation", "Онлайн-геолокация", "Onlaýn ýerleşiş soragy");
        put("Bilinmeyen IP'ler ipwho.is'e (HTTPS) gönderilir", "Unknown IPs are sent to ipwho.is (HTTPS)", "Неизвестные IP отправляются на ipwho.is (HTTPS)", "Näbelli IP-ler ipwho.is-e (HTTPS) iberilýär");
        put("Hata raporları", "Crash reports", "Отчёты об ошибках", "Ýalňyşlyk hasabatlary");
        put("Kapalı · hiçbir veri gönderilmez", "Off · no data is sent", "Выкл. · данные не отправляются", "Öçük · hiç hili maglumat iberilmeýär");
        put("Analitik", "Analytics", "Аналитика", "Analitika");
        put("Kapalı · uygulamada analitik yok", "Off · the app has no analytics", "Выкл. · аналитики нет", "Öçük · programmada analitika ýok");
        put("Sunucu geçmişini temizle", "Clear server history", "Очистить историю серверов", "Serwer taryhyny arassala");
        put("Kayıtlı IP'ler, konumlar ve ölçümler", "Saved IPs, locations and measurements", "Сохранённые IP, места и замеры", "Ýazylan IP-ler, ýerler we ölçegler");
        put("Sunucu geçmişi silinsin mi?", "Clear server history?", "Очистить историю серверов?", "Serwer taryhy pozulsynmy?");
        put("Sil", "Delete", "Удалить", "Poz");
        put("Vazgeç", "Cancel", "Отмена", "Ýatyr");
        put("Sunucu geçmişi silindi", "Server history cleared", "История серверов очищена", "Serwer taryhy pozuldy");
        put("Tüm uygulama verilerini sil", "Delete all app data", "Удалить все данные", "Ähli maglumatlary poz");
        put("Ayarlar, profiller, raporlar ve geçmiş", "Settings, profiles, reports and history", "Настройки, профили, отчёты и история", "Sazlamalar, profiller, hasabatlar we taryh");
        put("Tüm veriler silinsin mi?", "Delete all data?", "Удалить все данные?", "Ähli maglumatlar pozulsynmy?");
        put("Önce değiştirilmiş sistem ayarları geri yüklenir.", "Changed system settings are restored first.", "Сначала будут восстановлены системные настройки.", "Ilki üýtgedilen ulgam sazlamalary dikeldilýär.");
        put("Tüm veriler silindi", "All data deleted", "Все данные удалены", "Ähli maglumatlar pozuldy");
        put("REMNA BOOST'A\nHOŞ GELDİN", "WELCOME TO\nREMNA BOOST", "ДОБРО ПОЖАЛОВАТЬ\nВ REMNA BOOST", "REMNA BOOST-A\nHOŞ GELDIŇ");
        put("Oyun Performans Merkezi\n\n✓  FPS ve kare süresi izleme\n✓  Ağ ve bölge analizi\n✓  Termal koruma\n✓  Akıllı optimizasyon", "Game Performance Center\n\n✓  FPS and frame-time monitoring\n✓  Network and region analysis\n✓  Thermal protection\n✓  Smart optimization", "Центр игровой производительности\n\n✓  Мониторинг FPS и времени кадра\n✓  Анализ сети и регионов\n✓  Термозащита\n✓  Умная оптимизация", "Oýun öndürijilik merkezi\n\n✓  FPS we kadr wagtyna gözegçilik\n✓  Tor we sebit seljermesi\n✓  Termal gorag\n✓  Akylly optimizasiýa");
        put("Oyununu seç", "Choose your game", "Выберите игру", "Oýnuňy saýla");
        put("Oyun üstü paneli aç", "Enable the game overlay", "Включите игровую панель", "Oýun panelini aç");
        put("Oyun sırasında FPS, ping ve sıcaklığı küçük bir panelde gösterir. Paneli istediğin yere sürükleyebilirsin.", "Shows FPS, ping and temperature in a small panel during games. You can drag it anywhere.", "Показывает FPS, пинг и температуру в маленькой панели. Её можно перетаскивать.", "Oýun wagtynda FPS, ping we gyzgynlygy kiçi panelde görkezýär. Paneli islän ýeriňe süýşürip bilersiň.");
        put("Gelişmiş özellikler\n(isteğe bağlı)", "Advanced features\n(optional)", "Дополнительные функции\n(необязательно)", "Giňişleýin mümkinçilikler\n(islege görä)");
        put("Shizuku ile FPS ölçümü, Android oyun modu ve sunucu tespiti açılır. Kurulum birkaç dakika sürer; şimdi atlayıp sonra Ayarlar → İzinler'den açabilirsin.", "Shizuku unlocks FPS measurement, Android game mode and server detection. Setup takes a few minutes; you can skip now and enable it later in Settings → Permissions.", "Shizuku открывает измерение FPS, игровой режим Android и определение серверов. Настройка займёт несколько минут; можно пропустить и включить позже в Настройки → Разрешения.", "Shizuku bilen FPS ölçemek, Android oýun tertibi we serwer kesgitlemek açylýar. Gurnamak birnäçe minut alýar; häzir geçip, soň Sazlamalar → Rugsatlar-dan açyp bilersiň.");
        put("DEVAM", "CONTINUE", "ДАЛЕЕ", "DOWAM");
        put("ATLA", "SKIP", "ПРОПУСТИТЬ", "GEÇ");
        put("ADIM ", "STEP ", "ШАГ ", "ÄDIM ");
        put("Desteklenen bir PUBG Mobile sürümü bulunamadı. Uygulamayı yine de kullanabilir, oyunu yükledikten sonra ana ekrandan seçebilirsin.", "No supported PUBG Mobile version found. You can still use the app and select the game on the home screen after installing it.", "Поддерживаемая версия PUBG Mobile не найдена. Приложением можно пользоваться; выберите игру на главном экране после установки.", "Goldanýan PUBG Mobile wersiýasy tapylmady. Programmany ulanyp bilersiň; oýny gurnanyňdan soň baş ekrandan saýla.");
        put("Bulunan oyunlar:\n\n", "Games found:\n\n", "Найденные игры:\n\n", "Tapylan oýunlar:\n\n");
        put("\nDevam'a basınca hangisini kullanacağını seçebilirsin.", "\nTap Continue to choose which one to use.", "\nНажмите «Далее», чтобы выбрать.", "\nHaýsyny ulanjakdygyňy saýlamak üçin Dowam-a bas.");
        put("\nBu oyun otomatik seçildi.", "\nThis game was selected automatically.", "\nЭта игра выбрана автоматически.", "\nBu oýun awtomatik saýlandy.");
        put("Oyun modu", "Game mode", "Игровой режим", "Oýun tertibi");
        put("Oyun modu açık", "Game mode on", "Игровой режим включён", "Oýun tertibi açyk");
        put("Ping, FPS ve sunucular izleniyor", "Monitoring ping, FPS and servers", "Мониторинг пинга, FPS и серверов", "Ping, FPS we serwerler gözegçilikde");
        put("Ping, RAM ve sıcaklık izleniyor", "Monitoring ping, RAM and temperature", "Мониторинг пинга, RAM и температуры", "Ping, RAM we gyzgynlyk gözegçilikde");
        put("Soğutma", "Cooling", "Охлаждение", "Sowatmak");
        put("Ekran 60 Hz'e düşürüldü; soğuyunca kademeli olarak geri yükselecek", "Screen lowered to 60 Hz; it will step back up as it cools", "Экран снижен до 60 Гц; частота повысится после остывания", "Ekran 60 Hz-e düşürildi; sowansoň tapgyrma-tapgyr ýokarlanar");
        put("dalg. ", "jit. ", "джит. ", "yrg. ");
        put("kayıp %", "loss %", "потери %", "ýitgi %");
        put("Pil %.0f°", "Bat %.0f°", "Бат %.0f°", "Bat %.0f°");
        put("Ilık", "Warm", "Тепло", "Ýyly");
        put("Sıcak", "Hot", "Горячо", "Gyzgyn");
        put("Sensör", "Sensor", "Датчик", "Datçik");
        put("Kopyalandı", "Copied", "Скопировано", "Göçürildi");
        put("Shizuku bağlandı", "Shizuku connected", "Shizuku подключён", "Shizuku birikdi");
        put("Dil", "Language", "Язык", "Dil");
        put("Otomatik (sistem)", "Automatic (system)", "Автоматически (системный)", "Awtomatik (ulgam)");
    }

    static final String[] CODES = {"", "tk", "ru", "en", "tr"};
    static final String[] NAMES = {"Otomatik (sistem)", "Türkmençe", "Русский", "English", "Türkçe"};

    static void init(String pref) {
        if (pref != null && !pref.isEmpty()) { lang = pref; return; }
        String l = Locale.getDefault().getLanguage();
        switch (l) {
            case "tk": lang = "tk"; break;
            case "tr": case "az": lang = "tr"; break;
            case "ru": case "uk": case "be": case "kk": case "ky": case "uz": case "tg": lang = "ru"; break;
            default: lang = "en";
        }
    }

    static String t(String tr) {
        if ("tr".equals(lang)) return tr;
        String[] v = M.get(tr);
        if (v == null) return tr;
        return "en".equals(lang) ? v[0] : "ru".equals(lang) ? v[1] : v[2];
    }

    /** Dil seçici için görünen ad. */
    static String name(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i == 0 ? t(NAMES[0]) : NAMES[i];
        return code;
    }

    /** Alt gezinme sekmeleri (kısa etiketler; dar alana sığsın diye ayrı). */
    static String tab(int i) {
        String[][] T = {
                {"ANA", "AĞ", "PERFORMANS", "AYARLAR"},
                {"HOME", "NETWORK", "PERFORMANCE", "SETTINGS"},
                {"ГЛАВНАЯ", "СЕТЬ", "МЕТРИКИ", "НАСТРОЙКИ"},
                {"BAŞ", "TOR", "ÖNÜMLILIK", "SAZLAMALAR"}};
        int k = "en".equals(lang) ? 1 : "ru".equals(lang) ? 2 : "tk".equals(lang) ? 3 : 0;
        return T[k][i];
    }
}
