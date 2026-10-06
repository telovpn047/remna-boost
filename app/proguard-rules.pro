# Shizuku: Sh.exec() gizli "newProcess" metoduna yansıma ile erişir
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
# Uygulama sınıfları (servis, aktivite, sağlayıcı manifestten zaten korunur)
-keep class com.remna.boost.** { *; }
