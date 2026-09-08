# SQLite JDBC driver
-keep class org.sqlite.JDBC { *; }
-keep class org.sqlite.** { *; }
-dontwarn org.sqlite.**

# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# JDBC
-keep class java.sql.** { *; }
