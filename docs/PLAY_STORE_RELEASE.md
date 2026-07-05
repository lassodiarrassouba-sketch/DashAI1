# DashAI - préparation Play Store

Ce document décrit le chemin de publication Android pour DashAI. La publication finale nécessite un compte Google Play Console, un backend HTTPS public et une clé de signature privée.

## Etat technique actuel

- Application Android native : `android/`
- Backend IA FastAPI : `server/`
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 23`
- `applicationId = com.dashai.app`
- Version Play prête : `versionCode = 102`, `versionName = 1.0.2`
- HTTP local autorisé uniquement en debug.
- Release : cleartext HTTP désactivé.
- Clés API : uniquement côté serveur, jamais dans l'APK.

## Obligatoire avant publication

1. Déployer le backend FastAPI sur un domaine public en HTTPS.
2. Mettre l'URL HTTPS dans `android/keystore.properties` avec `DASHAI_PROD_API_ENDPOINT`.
3. Créer une clé d'upload Android locale.
4. Générer un Android App Bundle signé : `.aab`.
5. Créer l'application dans Google Play Console.
6. Remplir la fiche Play Store, la classification du contenu, le formulaire Data safety et la politique de confidentialité.
7. Tester en piste interne, puis fermée, puis production.

## Créer la clé d'upload

Exemple PowerShell :

```powershell
New-Item -ItemType Directory -Force C:\Users\msi\DashAI\keys

keytool -genkeypair `
  -v `
  -keystore C:\Users\msi\DashAI\keys\dashai-upload.jks `
  -alias dashai-upload `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

Garde le fichier `.jks` et les mots de passe hors du projet.

## Configurer la signature locale

Copie :

```powershell
Copy-Item .\android\keystore.properties.example .\android\keystore.properties
```

Puis modifie `android/keystore.properties` localement :

```properties
DASHAI_UPLOAD_STORE_FILE=C:\\Users\\msi\\DashAI\\keys\\dashai-upload.jks
DASHAI_UPLOAD_STORE_PASSWORD=mot-de-passe-prive
DASHAI_UPLOAD_KEY_ALIAS=dashai-upload
DASHAI_UPLOAD_KEY_PASSWORD=mot-de-passe-prive
DASHAI_PROD_API_ENDPOINT=https://api.ton-domaine.com/api/ask
```

Ne commit jamais ce fichier.

## Générer les artifacts

Debug local :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt\android
.\gradlew.bat :app:assembleDebug
```

Release APK de vérification :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt\android
.\gradlew.bat :app:assembleRelease
```

Bundle Play Store :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt\android
.\gradlew.bat :app:bundleRelease
```

Fichier attendu :

```text
android/app/build/outputs/bundle/release/app-release.aab
```

Si `android/keystore.properties` n'existe pas encore, le bundle généré sert seulement à vérifier la compilation. Pour un upload Play Store, regénère le bundle après avoir configuré la clé `.jks` et l'URL HTTPS de production.

## Données à déclarer dans Google Play

DashAI peut traiter :

- Questions écrites ou dictées : envoyées au backend IA pour générer une réponse.
- Photos choisies par l'utilisateur : envoyées au backend IA pour description.
- Empreinte vocale locale : stockée sur l'appareil, non envoyée au backend.
- URL backend : stockée localement dans les préférences Android.

Déclaration probable Data safety :

- User-generated content / app messages : collecte pour fonctionnalité de l'app.
- Photos : collecte seulement quand l'utilisateur demande une analyse caméra.
- Voice or sound recordings : l'empreinte vocale est traitée localement ; si une version future envoie de l'audio brut au serveur, il faudra le déclarer.
- Encryption in transit : oui uniquement si le backend production est HTTPS.
- Deletion mechanism : prévoir une adresse de contact et/ou un bouton de suppression si des journaux serveur sont conservés.

## Plateformes après Android

Play Store ne couvre qu'Android. Pour une vraie diffusion multi-plateforme :

- Android : Play Store avec AAB.
- iPhone/iPad : app iOS native ou cross-platform, publication App Store.
- Web/Desktop : PWA DashAI connectée au même backend HTTPS.
- Backend : hébergement cloud, domaine, TLS, monitoring, limites d'usage et coûts IA.
