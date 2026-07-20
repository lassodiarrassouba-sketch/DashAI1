import base64
import os
import unittest
from unittest.mock import patch

import main


class CloudflareProviderTests(unittest.TestCase):
    @patch("main.cloudflare_run")
    def test_cloudflare_text_response(self, run_mock):
        run_mock.return_value = {"response": "Bonjour depuis le moteur gratuit."}

        answer = main.generate_cloudflare_text("Dis bonjour", 200)

        self.assertEqual(answer, "Bonjour depuis le moteur gratuit.")
        payload = run_mock.call_args.args[1]
        self.assertEqual(payload["messages"][0]["content"], "Dis bonjour")
        self.assertEqual(payload["max_tokens"], 200)

    @patch("main.cloudflare_run")
    def test_cloudflare_image_response(self, run_mock):
        jpeg = b"\xff\xd8\xff\xe0" + b"diasco"
        run_mock.return_value = {"image": base64.b64encode(jpeg).decode("ascii")}

        encoded, mime_type, revised_prompt = main.generate_cloudflare_image("Une ville futuriste")

        self.assertEqual(base64.b64decode(encoded), jpeg)
        self.assertEqual(mime_type, "image/jpeg")
        self.assertIsNone(revised_prompt)

    @patch("main.generate_openai_text")
    @patch("main.generate_cloudflare_text")
    def test_auto_provider_prefers_cloudflare(self, cloudflare_mock, openai_mock):
        cloudflare_mock.return_value = "Réponse Cloudflare"
        with patch.dict(
            os.environ,
            {
                "AI_PROVIDER": "auto",
                "CLOUDFLARE_ACCOUNT_ID": "account123",
                "CLOUDFLARE_API_TOKEN": "token123",
            },
            clear=False,
        ):
            answer = main.generate_text_with_fallback("Question", "modele", 200, "conversationnel")

        self.assertEqual(answer, "Réponse Cloudflare")
        openai_mock.assert_not_called()

    @patch("main.generate_openai_text")
    @patch("main.generate_cloudflare_text")
    def test_auto_provider_falls_back_to_openai(self, cloudflare_mock, openai_mock):
        cloudflare_mock.side_effect = RuntimeError("cloudflare_unreachable")
        openai_mock.return_value = "Réponse de secours"
        with patch.dict(
            os.environ,
            {
                "AI_PROVIDER": "auto",
                "CLOUDFLARE_ACCOUNT_ID": "account123",
                "CLOUDFLARE_API_TOKEN": "token123",
            },
            clear=False,
        ):
            answer = main.generate_text_with_fallback("Question", "modele", 200, "conversationnel")

        self.assertEqual(answer, "Réponse de secours")

    @patch("main.generate_text_with_fallback")
    def test_ask_route_keeps_normal_question_in_conversation(self, provider_mock):
        provider_mock.return_value = "Le président du Gabon est Brice Clotaire Oligui Nguema."

        response = main.ask(main.AskRequest(question="Qui est le président de la République du Gabon ?"))

        self.assertIn("président du Gabon", response.answer)
        provider_mock.assert_called_once()

    def test_cloudflare_quota_error_is_safe(self):
        error = main.provider_chain_http_exception([RuntimeError("cloudflare_rate_limit")], "d’image")

        self.assertEqual(error.status_code, 429)
        self.assertIn("quota gratuit", error.detail)


if __name__ == "__main__":
    unittest.main()
