DashAI V3 - relances contextuelles

1) Ouvrir le backend : dashai-rebuilt/server
2) Créer .env depuis .env.example si absent
3) Lancer : .\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000
4) Ouvrir Android Studio sur dashai-rebuilt/android
5) Clean Project, Rebuild Project, puis réinstaller l'application sur le téléphone

Vérification importante : le sous-titre dans l'application doit afficher "V3 contexte".
