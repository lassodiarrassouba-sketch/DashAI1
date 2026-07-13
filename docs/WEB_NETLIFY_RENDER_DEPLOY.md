# DashAI web - deploiement sans Play Store

Ce deploiement donne un lien public utilisable sur Android, iPhone, tablette et ordinateur. Les utilisateurs peuvent aussi installer la PWA depuis leur navigateur.

## Architecture recommandee

- Frontend PWA : `web/` sur Netlify.
- Backend IA FastAPI : `server/` sur Render ou Google Cloud Run.
- Cle OpenAI : variable d'environnement du backend uniquement.
- Android natif : reste disponible en APK/AAB, mais n'est pas necessaire pour partager DashAI publiquement.

## Pourquoi pas seulement GitHub ou Netlify ?

GitHub Pages et Netlify sont parfaits pour le site web/PWA. Le backend FastAPI doit tourner sur une plateforme serveur comme Render, Railway, Fly.io ou Google Cloud Run. Netlify seul ne suffit pas pour heberger ce backend Python FastAPI en continu.

## 1. Mettre le projet sur GitHub

Depuis le dossier du projet :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt
.\scripts\Publish-ToGitHub.ps1 -RepositoryUrl "https://github.com/VOTRE_COMPTE/dashai.git"
```

Si le depot Git existe deja, garde seulement :

```powershell
.\scripts\Publish-ToGitHub.ps1 -RepositoryUrl "https://github.com/VOTRE_COMPTE/dashai.git" -CommitMessage "Update DashAI deployment"
```

Le script verifie que les fichiers sensibles restent hors Git : `.env`, keystore Android, APK et archives de publication.

## 2. Deployer le backend sur Render

Render peut lire `render.yaml` a la racine du depot.

Dans Render :

1. New > Blueprint.
2. Connecter le depot GitHub `dashai`.
3. Render detecte `render.yaml`.
4. Le service `dashai-backend` est force en plan gratuit avec `plan: free`.
5. Renseigner les variables secretes :

```text
OPENAI_API_KEY=sk-...
ALLOWED_ORIGINS=https://votre-site.netlify.app
```

Si Render affiche `A Blueprint file was found, but there was an issue`, verifie que la derniere version de `render.yaml` contient bien :

```yaml
plan: free
```

Puis clique sur `Retry`.

Le service expose ensuite une URL du type :

```text
https://dashai-backend.onrender.com
```

Test :

```powershell
curl https://dashai-backend.onrender.com/health
```

L'URL API a utiliser dans la PWA sera :

```text
https://dashai-backend.onrender.com/api/ask
```

Pour eviter que les utilisateurs Android configurent eux-memes l'URL backend, injecte cette URL dans l'APK release :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt
.\scripts\Configure-ProductionBackend.ps1 -BackendUrl "https://dashai-backend.onrender.com" -BuildApk
```

Le script accepte aussi directement :

```text
https://dashai-backend.onrender.com/api/ask
```

Il met a jour :

- `android/keystore.properties`
- `web/config.js`
- `download-site/dashai-1.0.4.apk`
- `dashai-android-download-netlify.zip`

## 3. Deployer la PWA sur Netlify

Dans Netlify :

1. Add new project > Import an existing project.
2. Connecter GitHub.
3. Choisir le depot `dashai`.
4. Netlify lit `netlify.toml`.
5. Publish directory : `web`.
6. Build command : vide.
7. Deploy.

Apres le premier deploy, Netlify donnera une URL :

```text
https://votre-site.netlify.app
```

## 4. Configurer l'URL backend dans la PWA

Option rapide : ouvrir la PWA, coller l'URL backend dans le champ `Backend HTTPS`, puis appuyer sur `Sauver`.

Option publique propre : modifier `web/config.js` avant de pousser :

```js
window.DASHAI_CONFIG = {
  defaultBackendUrl: "https://dashai-backend.onrender.com/api/ask",
  defaultLocale: "fr-FR"
};
```

Puis :

```powershell
git add web/config.js
git commit -m "Set production backend URL"
git push
```

Netlify redeploie automatiquement.

## 5. Autoriser Netlify cote backend

Dans Render, mets `ALLOWED_ORIGINS` avec l'URL Netlify exacte :

```text
ALLOWED_ORIGINS=https://votre-site.netlify.app
```

Pour plusieurs domaines :

```text
ALLOWED_ORIGINS=https://votre-site.netlify.app,https://www.votre-domaine.com
```

## 6. Option Google Cloud Run

Le dossier `server/` contient deja un `Dockerfile`. Tu peux deployer ce conteneur sur Cloud Run, puis definir :

```text
OPENAI_API_KEY
OPENAI_MODEL
MAX_OUTPUT_TOKENS
ALLOWED_ORIGINS
```

Cloud Run est robuste, mais Google Cloud demande souvent un compte de facturation actif.

## 7. Limites de la PWA

- Le reveil vocal fonctionne seulement quand la page est ouverte et que le navigateur autorise le micro.
- iOS et certains navigateurs limitent l'ecoute vocale continue.
- L'empreinte vocale locale avancee reste meilleure dans l'app Android native.
- Une API publique peut consommer des credits IA. Ajoute des limites, de l'authentification ou un quota avant une diffusion large.

## 8. Fichiers ajoutes

- `web/` : application PWA.
- `netlify.toml` : configuration Netlify.
- `render.yaml` : configuration Render.
- `server/.env.example` : ajout de `ALLOWED_ORIGINS`.
