# DIASCO

DIASCO est un assistant Android natif avec un backend FastAPI sécurisé. Il peut tenir une conversation, répondre à la voix, analyser une photo, générer du code et des formules, créer une image et produire un site web autonome.

- `android/` : application Android Java, sans clé API embarquée.
- `server/` : backend FastAPI qui appelle les services IA côté serveur.
- `web/` : version PWA installable et publiable sur Netlify.
- `download-site/` : page de téléchargement direct de l'APK.

## Backend local

La clé `OPENAI_API_KEY` doit rester exclusivement dans `server/.env` ou dans les variables secrètes Render.

```powershell
cd server
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Vérification : `http://127.0.0.1:8000/health` doit répondre `{"status":"ok"}`.

## Android

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

La release utilise l'URL HTTPS configurée dans `android/keystore.properties` et refuse le trafic HTTP. Le debug peut utiliser HTTP pour les tests sur le réseau local.

Le réveil vocal est fixé à « Dis Diasco ». Sur Android récent, DIASCO utilise un service micro au premier plan avec notification persistante. L'utilisateur doit ouvrir l'application au moins une fois et autoriser le microphone.

## Fonctions IA

- Conversation persistante et réponses vocales.
- Analyse d'une image prise avec la caméra.
- Code et formules conservés dans un affichage copiable.
- Génération d'images via `POST /api/image`.
- Génération d'un site HTML autonome via `POST /api/site`.

La génération d'images nécessite un compte API OpenAI actif avec du crédit et une limite de dépenses suffisante. Une erreur `billing_hard_limit_reached` est un blocage de facturation du compte, pas une panne Android.

## Distribution

La page Netlify se trouve dans `download-site/` et distribue `diasco-2.0.0.apk`. Le backend de production reste hébergé sur Render avec HTTPS.
