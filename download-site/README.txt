DashAI Android 1.0.2

Fichiers :
- index.html : page de telechargement
- dashai-1.0.2.apk : application Android signee
- icon.svg : icone
- styles.css : styles de la page

Pour publier sans Play Store :
1. Ouvrir https://app.netlify.com/drop
2. Deposer ce dossier ou l'archive dashai-android-download-netlify.zip
3. Partager l'URL Netlify obtenue

Pour mettre a jour l'application :
1. Garder la meme cle android/keys/dashai-release.jks
2. Augmenter versionCode dans android/app/build.gradle.kts
3. Regenerer l'APK release
4. Remplacer dashai-1.0.2.apk par la nouvelle version
