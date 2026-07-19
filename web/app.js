const config = window.DASHAI_CONFIG || {};
const state = {
  history: [],
  listening: false,
  waitingForQuestion: false,
  recognizer: null,
  installPrompt: null
};

const els = {
  endpointInput: document.querySelector("#endpointInput"),
  saveSettingsButton: document.querySelector("#saveSettingsButton"),
  testBackendButton: document.querySelector("#testBackendButton"),
  installButton: document.querySelector("#installButton"),
  messages: document.querySelector("#messages"),
  statusText: document.querySelector("#statusText"),
  modeText: document.querySelector("#modeText"),
  composer: document.querySelector("#composer"),
  questionInput: document.querySelector("#questionInput"),
  wakeButton: document.querySelector("#wakeButton"),
  cameraButton: document.querySelector("#cameraButton"),
  photoInput: document.querySelector("#photoInput")
};

const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

init();

function init() {
  const savedEndpoint = localStorage.getItem("diasco.backendUrl")
    || localStorage.getItem("dashai.backendUrl")
    || config.defaultBackendUrl
    || "";
  els.endpointInput.value = savedEndpoint;
  appendAssistant("Bonjour, je suis DIASCO. Je peux discuter avec vous, analyser une photo, ecrire du code, creer une image ou construire un site web.");
  bindEvents();
  registerServiceWorker();
  updateSpeechSupport();
}

function bindEvents() {
  els.saveSettingsButton.addEventListener("click", saveSettings);
  els.testBackendButton.addEventListener("click", testBackend);
  els.composer.addEventListener("submit", (event) => {
    event.preventDefault();
    const question = els.questionInput.value.trim();
    if (!question) return;
    els.questionInput.value = "";
    ask(question);
  });
  els.wakeButton.addEventListener("click", toggleWakeListening);
  els.cameraButton.addEventListener("click", () => els.photoInput.click());
  els.photoInput.addEventListener("change", describeSelectedPhoto);
  els.installButton.addEventListener("click", installPwa);
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    state.installPrompt = event;
    els.installButton.style.display = "inline-block";
  });
}

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  navigator.serviceWorker.register("/sw.js").catch(() => {
    setStatus("Mode hors ligne indisponible sur ce navigateur.");
  });
}

async function installPwa() {
  if (!state.installPrompt) return;
  state.installPrompt.prompt();
  await state.installPrompt.userChoice;
  state.installPrompt = null;
  els.installButton.style.display = "none";
}

function updateSpeechSupport() {
  if (!SpeechRecognition) {
    els.wakeButton.disabled = true;
    els.wakeButton.title = "Reconnaissance vocale non disponible dans ce navigateur";
    els.modeText.textContent = "Texte + camera";
  }
}

function saveSettings() {
  const endpoint = normalizedEndpoint();
  localStorage.setItem("diasco.backendUrl", endpoint);
  localStorage.removeItem("dashai.backendUrl");
  setStatus("Configuration sauvegardee.");
}

async function testBackend() {
  const endpoint = normalizedEndpoint();
  if (!endpoint) {
    appendError("Ajoute d'abord l'URL du backend.");
    return;
  }
  const healthUrl = endpoint.replace(/\/api\/ask\/?$/, "/health");
  setStatus("Test du backend...");
  try {
    const response = await fetch(healthUrl);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const body = await response.json();
    setStatus(body.status === "ok" ? "Backend connecte." : "Backend joignable.");
  } catch (error) {
    appendError(`Backend inaccessible : ${error.message}`);
    setStatus("Backend non joignable.");
  }
}

async function ask(question) {
  appendUser(question);
  if (isWebsiteRequest(question)) {
    await generateWebsite(question);
    return;
  }
  if (isImageRequest(question)) {
    await generateImage(question);
    return;
  }

  const endpoint = normalizedEndpoint();
  if (!endpoint) {
    appendError("Configure l'URL HTTPS du backend avant d'envoyer une question.");
    return;
  }

  setStatus("IA : reflexion en cours...");
  try {
    const answer = await postJson(endpoint, {
      question,
      locale: config.defaultLocale || navigator.language || "fr-FR",
      client: "diasco-web-pwa",
      history: buildHistory()
    });
    const text = cleanAnswer(answer.answer || "");
    appendAssistant(text);
    remember(question, text);
    speak(text);
  } catch (error) {
    appendError(error.message);
  } finally {
    setStatus("Pret.");
  }
}

async function generateImage(prompt) {
  const endpoint = normalizedEndpoint();
  if (!endpoint) {
    appendError("Configure l'URL HTTPS du backend avant de generer une image.");
    return;
  }

  setStatus("Image : generation en cours...");
  try {
    const result = await postJson(endpoint.replace(/\/api\/ask\/?$/, "/api/image"), {
      prompt,
      locale: config.defaultLocale || navigator.language || "fr-FR",
      client: "diasco-web-pwa"
    });
    const message = cleanAnswer(result.answer || "Voici l'image generee.");
    appendAssistant(message, result.image_base64, result.mime_type);
    remember(prompt, message);
    speak(message);
  } catch (error) {
    appendError(error.message);
  } finally {
    setStatus("Pret.");
  }
}

async function generateWebsite(prompt) {
  const endpoint = normalizedEndpoint();
  if (!endpoint) {
    appendError("Le service DIASCO est momentanement indisponible.");
    return;
  }

  setStatus("Site : creation en cours...");
  try {
    const result = await postJson(endpoint.replace(/\/api\/ask\/?$/, "/api/site"), {
      prompt,
      locale: config.defaultLocale || navigator.language || "fr-FR",
      client: "diasco-web-pwa",
      history: buildHistory()
    });
    const message = cleanAnswer(result.answer || "Votre site est pret.");
    appendAssistant(message);
    appendWebsite(result.title || "Site cree par DIASCO", result.html || "");
    remember(prompt, message);
    speak(message);
  } catch (error) {
    appendError(error.message);
  } finally {
    setStatus("Pret.");
  }
}

async function describeSelectedPhoto() {
  const file = els.photoInput.files && els.photoInput.files[0];
  els.photoInput.value = "";
  if (!file) return;

  const endpoint = normalizedEndpoint();
  if (!endpoint) {
    appendError("Configure l'URL HTTPS du backend avant d'analyser une photo.");
    return;
  }

  const prompt = els.questionInput.value.trim() || "Decris clairement ce que tu vois sur cette image.";
  appendUser(`${prompt} (photo)`);
  setStatus("Image : analyse en cours...");
  try {
    const imageBase64 = await fileToBase64(file);
    const answer = await postJson(endpoint.replace(/\/api\/ask\/?$/, "/api/vision"), {
      image_base64: imageBase64,
      mime_type: file.type || "image/jpeg",
      prompt,
      locale: config.defaultLocale || navigator.language || "fr-FR",
      client: "diasco-web-pwa",
      history: buildHistory()
    });
    const text = cleanAnswer(answer.answer || "");
    appendAssistant(text);
    remember(`Photo : ${prompt}`, text);
    speak(text);
  } catch (error) {
    appendError(error.message);
  } finally {
    setStatus("Pret.");
  }
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  let body = null;
  try {
    body = await response.json();
  } catch (_) {
    body = {};
  }
  if (!response.ok) {
    const detail = body.detail || `Erreur HTTP ${response.status}`;
    const message = typeof detail === "string" ? detail : JSON.stringify(detail);
    if (response.status === 402) {
      throw new Error("La creation d'image est temporairement indisponible. Le proprietaire de DIASCO doit reactiver le credit du service IA.");
    }
    throw new Error(message);
  }
  return body;
}

function toggleWakeListening() {
  if (!SpeechRecognition) return;
  if (state.listening) {
    stopRecognition();
    return;
  }
  startRecognition(false);
}

function startRecognition(questionMode) {
  stopRecognition();
  const recognizer = new SpeechRecognition();
  recognizer.lang = "fr-FR";
  recognizer.interimResults = false;
  recognizer.continuous = false;
  state.recognizer = recognizer;
  state.listening = true;
  state.waitingForQuestion = questionMode;
  els.wakeButton.classList.add("active");
  setStatus(questionMode ? "Oui, je vous ecoute..." : "Reveil vocal : dites dis Diasco.");

  recognizer.onresult = (event) => {
    const text = Array.from(event.results)
      .map((result) => result[0]?.transcript || "")
      .join(" ")
      .trim();
    handleSpeechText(text);
  };
  recognizer.onerror = () => {
    stopRecognition();
    setStatus("Micro : aucune phrase detectee.");
  };
  recognizer.onend = () => {
    state.listening = false;
    els.wakeButton.classList.remove("active");
    if (!state.waitingForQuestion) setStatus("Pret.");
  };
  recognizer.start();
}

function handleSpeechText(text) {
  if (!text) {
    setStatus("Micro : aucune phrase detectee.");
    return;
  }

  if (state.waitingForQuestion) {
    state.waitingForQuestion = false;
    stopRecognition();
    ask(text);
    return;
  }

  if (containsWakePhrase(text)) {
    state.waitingForQuestion = true;
    appendAssistant("Oui, je vous ecoute.");
    speak("Oui, je vous ecoute.");
    window.setTimeout(() => startRecognition(true), 950);
    return;
  }

  setStatus(`Entendu : ${text}`);
}

function stopRecognition() {
  if (state.recognizer) {
    try {
      state.recognizer.abort();
    } catch (_) {
      // Certains navigateurs jettent une erreur si l'ecoute est deja terminee.
    }
  }
  state.recognizer = null;
  state.listening = false;
  state.waitingForQuestion = false;
  els.wakeButton.classList.remove("active");
}

function containsWakePhrase(text) {
  const clean = normalizeText(text).replace(/\s+/g, "");
  return clean.includes("diasco") || clean.includes("diasko") || clean.includes("djasco");
}

function isImageRequest(text) {
  const clean = normalizeText(text);
  const asksVisual = clean.includes("image") || clean.includes("photo") || clean.includes("dessin") || clean.includes("illustration");
  const command = /^(affiche|montre|genere|cree|dessine|fais|produis|une image|image)/.test(clean);
  return asksVisual && command;
}

function isWebsiteRequest(text) {
  const clean = normalizeText(text);
  const asksSite = clean.includes("site web")
    || clean.includes("site internet")
    || clean.includes("page web")
    || clean.includes("landing page");
  const command = /^(genere|cree|construis|fais|developpe|realise|produis)/.test(clean);
  return asksSite && command;
}

function normalizedEndpoint() {
  return els.endpointInput.value.trim().replace(/\/api\/askq$/, "/api/ask").replace(/\/api\/ask\/$/, "/api/ask");
}

function appendUser(text) {
  appendMessage("user", text);
}

function appendAssistant(text, imageBase64, mimeType) {
  appendMessage("assistant", text, imageBase64, mimeType);
}

function appendError(text) {
  appendMessage("error", text);
}

function appendMessage(type, text, imageBase64, mimeType) {
  const message = document.createElement("div");
  message.className = `message ${type}`;
  message.textContent = type === "user" ? `Vous : ${text}` : `DIASCO : ${text}`;
  if (imageBase64) {
    const image = document.createElement("img");
    image.alt = "Image generee par DIASCO";
    image.src = `data:${mimeType || "image/png"};base64,${imageBase64}`;
    message.appendChild(image);
  }
  els.messages.appendChild(message);
  els.messages.scrollTop = els.messages.scrollHeight;
}

function appendWebsite(title, sourceHtml) {
  if (!sourceHtml) return;

  const frame = document.createElement("section");
  frame.className = "site-preview";

  const heading = document.createElement("strong");
  heading.textContent = title;
  frame.appendChild(heading);

  const preview = document.createElement("iframe");
  preview.title = `Apercu de ${title}`;
  preview.setAttribute("sandbox", "allow-scripts");
  preview.srcdoc = securePreviewHtml(sourceHtml);
  frame.appendChild(preview);

  const download = document.createElement("button");
  download.className = "button";
  download.type = "button";
  download.textContent = "Telecharger le site";
  download.addEventListener("click", () => {
    const blob = new Blob([sourceHtml], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${slugify(title) || "site-diasco"}.html`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  frame.appendChild(download);

  els.messages.appendChild(frame);
  els.messages.scrollTop = els.messages.scrollHeight;
}

function securePreviewHtml(sourceHtml) {
  const policy = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:;\">";
  if (/<head[\s>]/i.test(sourceHtml)) {
    return sourceHtml.replace(/<head([^>]*)>/i, `<head$1>${policy}`);
  }
  return `${policy}${sourceHtml}`;
}

function slugify(text) {
  return normalizeText(text).replace(/\s+/g, "-").replace(/[^a-z0-9-]/g, "").slice(0, 64);
}

function remember(question, answer) {
  state.history.push(`Vous : ${question}`);
  state.history.push(`DIASCO : ${answer}`);
  while (state.history.length > 18) state.history.shift();
}

function buildHistory() {
  return state.history.join("\n");
}

function cleanAnswer(text) {
  return String(text || "")
    .replaceAll("**", "")
    .replaceAll("__", "")
    .replaceAll("`", "")
    .replace(/^#{1,6}\s*/gm, "")
    .trim();
}

function speak(text) {
  if (!("speechSynthesis" in window)) return;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "fr-FR";
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(utterance);
}

function setStatus(text) {
  els.statusText.textContent = text;
}

function normalizeText(text) {
  return String(text || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[’']/g, " ")
    .replace(/[^a-z0-9\s-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      resolve(result.includes(",") ? result.split(",", 2)[1] : result);
    };
    reader.onerror = () => reject(new Error("Impossible de lire l'image."));
    reader.readAsDataURL(file);
  });
}
