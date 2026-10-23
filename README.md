# SoulLinkCo Muneem (Android)

`soullinkco_muneem.html` ko WebView me chalane wala Android app (`app/src/main/assets/index.html`).

## APK kaise banaye
1. Is folder ko ek naye GitHub repo me push karo (root me `app/`, `.github/` dikhna chahiye).
2. Repo > **Actions** > "Build Android APK" (push par apne aap chalta hai, ya **Run workflow**).
3. Run khatam hone par **Artifacts** se `SoulLinkCo-Muneem-APK` download karo, zip kholo, `app-debug.apk` phone me install karo.

HTML change karni ho to `app/src/main/assets/index.html` replace karke push karo.

## Auto-read (SMS / UPI / email)
- Pehli baar app khulne par SMS permission maangta hai. **Auto-capture** page par "Allow SMS and notification access" button se dono allow karo.
- Notification access: Settings me "SoulLinkCo Muneem" ko ON karo (UPI apps + Gmail/Outlook ke alerts isi se padhe jate hain).
- Android 13+ par sideloaded APK ke liye "Restricted setting" block aa sakta hai: Settings > Apps > SoulLinkCo Muneem > ⋮ (top-right) > **Allow restricted settings**, phir permission do.
