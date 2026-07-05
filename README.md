# DashAI reconstruit

Cette archive contient une reconstruction propre de l'application DashAI :

- `android/` : application Android native en Java, sans clé API embarquée.
- `web/` : version PWA installable et deployable sur Netlify.
- `server/` : backend FastAPI qui appelle l'API IA côté serveur.
- `docs/` : architecture et notes de migration.

L'objectif est de corriger le problème principal de l'ancien APK : l'application ne doit pas se faire passer pour une IA conversationnelle alors qu'elle utilise une FAQ locale + Google Custom Search. Ici, les questions générales passent par un vrai backend IA configurable.

## 1. Lancer le backend IA

```bash
cd server
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
cp .env.example .env
```

Modifiez `.env` et mettez votre clé côté serveur uniquement :

```bash
OPENAI_API_KEY=sk-proj-votre-cle-ici
OPENAI_MODEL=gpt-5.4-mini
```

Puis lancez :

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

Test rapide :

```bash
curl -X POST http://127.0.0.1:8000/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"Réponds uniquement OK","locale":"fr-FR"}'
```

## 2. Ouvrir l'application Android

Ouvrez le dossier `android/` dans Android Studio.

Paramètres principaux :

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 23`
- Android Gradle Plugin `8.13.0`

Build debug :

```bash
cd android
gradle :app:assembleDebug
```

Ou directement depuis Android Studio : **Run**.

## 3. Configurer l'URL dans l'application

Dans l'écran DashAI, renseignez l'URL du backend :

- Émulateur Android local : `http://10.0.2.2:8000/api/ask`
- Téléphone réel : utilisez une URL HTTPS publique, par exemple `https://votre-domaine.com/api/ask`

Activez **Mode en ligne**, puis appuyez sur **Tester**.

## 4. Ce que l'application fait hors ligne

Sans backend, elle répond localement à quelques demandes sûres :

- heure ;
- date ;
- batterie ;
- modèle du téléphone ;
- calculs simples comme `12 + 5`, `10 / 2`, `7 x 8` ;
- aide et présentation.

Pour les questions générales, elle indique clairement qu'il faut activer le mode en ligne.

## 5. Sécurité

Ne mettez jamais `OPENAI_API_KEY`, une clé Google ou une autre clé fournisseur dans le code Android. Une APK peut être décompilée, donc toute clé embarquée doit être considérée comme exposée.

## 6. Limite de cette archive

Je fournis ici une reconstruction source propre et modifiable. Je n'ai pas inclus d'APK compilé, car cet environnement ne contient pas le SDK Android/build-tools nécessaires pour produire et signer un binaire installable de manière fiable. Ouvrez `android/` dans Android Studio pour générer l'APK ou l'AAB.

## Correction locale HTTP Android

Cette archive inclut une correction pour l'erreur Android :

```text
Cleartext HTTP traffic to ... not permitted
```

Les builds debug autorisent désormais HTTP pour tester un backend local. Les builds release restent configurés pour refuser HTTP ; utilisez HTTPS en production.
Voir `docs/LOCAL_HTTP_ANDROID.md`.


## Version V2 locale debug

Cette archive ajoute un meilleur comportement conversationnel : le client Android envoie le contexte récent au backend, les réponses sont nettoyées pour éviter l'affichage des marqueurs Markdown comme `**texte**`, et les erreurs audio silencieuses ne remplissent plus autant le fil de discussion. Voir `docs/V2_IMPROVEMENTS.md`.

## Version web sans Play Store

DashAI contient maintenant une PWA dans `web/`. Elle peut etre publiee sur Netlify et connectee au backend FastAPI heberge sur Render ou Google Cloud Run. Voir `docs/WEB_NETLIFY_RENDER_DEPLOY.md`.
