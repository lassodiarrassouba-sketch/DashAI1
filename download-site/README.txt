DIASCO Android 2.1.0

Fichiers :
- index.html : page de telechargement
- diasco-2.1.0.apk : application Android signee
- dashai-logo.png : logo
- styles.css : styles de la page

Cette APK est preconfiguree avec le backend HTTPS DIASCO. Les utilisateurs n'ont pas d'URL backend a saisir.

Pour publier sans Play Store :
1. Ouvrir https://app.netlify.com/drop
2. Deposer ce dossier ou l'archive diasco-android-download-netlify.zip
3. Partager l'URL Netlify obtenue

Pour mettre a jour l'application :
1. Garder la meme cle android/keys/dashai-release.jks
2. Augmenter versionCode dans android/app/build.gradle.kts
3. Regenerer l'APK release
4. Remplacer diasco-2.1.0.apk par la nouvelle version
