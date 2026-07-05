# Test Android avec un backend local HTTP

Android bloque les requêtes HTTP non chiffrées par défaut quand l'application cible les versions modernes d'Android.
Ce projet autorise maintenant le HTTP **uniquement pour les builds debug** afin de tester le backend local sur le réseau.

## Depuis l'émulateur Android

Utilisez cette URL dans DashAI :

```text
http://10.0.2.2:8000/api/ask
```

## Depuis un vrai téléphone

1. Le PC et le téléphone doivent être sur le même Wi-Fi ou le même partage de connexion.
2. Lancez le serveur avec :

```powershell
cd C:\Users\msi\Downloads\dashai-rebuilt\server
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000
```

3. Trouvez l'adresse IPv4 du PC :

```powershell
ipconfig
```

4. Testez depuis le navigateur du téléphone :

```text
http://ADRESSE-IP-DU-PC:8000/health
```

5. Si le navigateur affiche `{"status":"ok"}`, utilisez dans DashAI :

```text
http://ADRESSE-IP-DU-PC:8000/api/ask
```

Exemple :

```text
http://172.20.10.4:8000/api/ask
```

## Production

Ne publiez pas une application qui utilise HTTP. Déployez le backend derrière HTTPS et configurez DashAI avec :

```text
https://votre-domaine.com/api/ask
```
