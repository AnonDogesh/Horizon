# Keep WebView JavaScript interface names if added in future
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
