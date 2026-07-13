# Distribution directe APK - DashAI

Cette option permet de rendre DashAI telechargeable sans Play Store. Les utilisateurs Android ouvrent une page web, telechargent l'APK, puis l'installent manuellement.

## Fichiers produits

- APK signee : `android/app/build/outputs/apk/release/app-release.apk`
- Copie publique : `download-site/dashai-1.0.5.apk`
- Site de telechargement : `download-site/`
- Archive Netlify Drop : `dashai-android-download-netlify.zip`

## Publier sur Netlify Drop

1. Ouvrir `https://app.netlify.com/drop`.
2. Deposer `dashai-android-download-netlify.zip`.
3. Copier le lien public Netlify.
4. Envoyer ce lien aux utilisateurs Android.

## Publier sur GitHub Releases

1. Creer un depot GitHub.
2. Aller dans `Releases`.
3. Creer une release `DashAI 1.0.5`.
4. Ajouter le fichier `android/app/build/outputs/apk/release/app-release.apk`.
5. Publier la release et partager le lien.

## Mise a jour future

Android accepte une mise a jour seulement si la nouvelle APK est signee avec la meme cle.

Sauvegarde absolument :

- `android/keys/dashai-release.jks`
- `android/keystore.properties`

Pour chaque nouvelle version :

1. Augmenter `versionCode` dans `android/app/build.gradle.kts`.
2. Mettre a jour `versionName`.
3. Regenerer :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt\android
.\gradlew.bat :app:assembleRelease
```

4. Remplacer l'APK dans `download-site/`.
5. Refaire l'archive :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt
Compress-Archive -Path download-site\* -DestinationPath dashai-android-download-netlify.zip -Force
```

## Backend obligatoire

La release Android refuse HTTP local. Pour les utilisateurs publics, il faut un backend HTTPS :

```text
https://votre-backend.com/api/ask
```

Sans backend HTTPS public, l'application s'installe mais les fonctions IA en ligne ne seront pas utilisables par tous.

Apres creation du backend Render, injecte l'URL dans l'APK pour que les utilisateurs n'aient rien a configurer :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt
.\scripts\Configure-ProductionBackend.ps1 -BackendUrl "https://dashai-backend.onrender.com" -BuildApk
```

Remplace `https://dashai-backend.onrender.com` par l'URL reelle donnee par Render.
