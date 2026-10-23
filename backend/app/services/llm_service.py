import time
import json
import logging
from typing import List, Dict, Any, Optional
from app.core.config import settings

logger = logging.getLogger(__name__)

ROMANIAN_COPILOT_SYSTEM_PROMPT = """Ești asistentul personal și copilotul executiv de încredere al Oliviei (sau al utilizatorului indicat).
Utilizatoarea se află într-o ședință de lucru profesională în limba română (pe Zoom, Google Meet sau Teams).
Tocmai i s-a adresat o întrebare sau i s-a cerut părerea/intervenția.

SARCINA TA:
Analizează transcrierea recentă a conversației din ședință și generează IMEDIAT 3 VARIANTE TACTICE DE RĂSPUNS (exact frazele pe care Olivia să le poată rosti cu voce tare, la persoana I, fără nicio ezitare):

1. "agree" (Acord / Susținere):
   - Răspuns afirmativ, profesionist și constructiv. Susține direcția echipei sau confirmă termenul/bugetul.
   - Lungime: MAXIM 1-2 fraze clare.

2. "stall" (Cumpără Timp / Scuză de Mute):
   - Scuza executivă perfectă când a fost luată prin surprindere sau nu a fost atentă.
   - Include menționarea scurtă a butonului de microfon ("Scuzați-mă, aveam microfonul oprit...") și oferă o aliniere generală cu promisiunea unui e-mail detaliat.

3. "caution" (Prudență / Amânare Diplomatică):
   - Răspuns prudent, diplomatic, care evită un angajament prematur înainte de verificarea datelor financiare sau tehnice.

FORMATUL RĂSPUNSULUI (JSON OBLIGATORIU):
{
  "question_detected": "Întrebarea rezumată clar în română",
  "spoken_response": "Fraza principală recomandată (de regulă varianta agree)",
  "talking_points": [
    "Punct de suport 1",
    "Punct de suport 2"
  ],
  "tactical_responses": {
    "agree": {
      "label": "Acord / Susținere",
      "spoken_response": "Fraza exactă de acord la persoana I...",
      "talking_points": ["Idee suport 1", "Idee suport 2"]
    },
    "stall": {
      "label": "Cumpără Timp (Scuză Mute)",
      "spoken_response": "Scuzați-mă, a durat o secundă să găsesc butonul de unmute. Sunt aliniată cu ideea, trimit confirmarea scrisă pe mail imediat după ședință.",
      "talking_points": ["Câștigă 15 secunde de respiro", "Mută detaliile pe e-mail"]
    },
    "caution": {
      "label": "Prudență / Amânare",
      "spoken_response": "Avem nevoie de cifrele finale înainte să fixăm o decizie fermă; revin cu confirmarea după ce verific datele.",
      "talking_points": ["Evită angajamente premature", "Păstrează controlul deciziei"]
    }
  }
}
"""

class LLMService:
    def __init__(self):
        self.gemini_client = None
        self._init_gemini()

    def _init_gemini(self):
        api_key = settings.GEMINI_API_KEY
        if api_key:
            try:
                from google import genai
                self.gemini_client = genai.Client(api_key=api_key)
                logger.info("Initialized Google GenAI client.")
            except Exception as e:
                logger.warning(f"Failed to initialize Google GenAI: {e}")

    async def generate_quick_answer(
        self,
        transcript_context: str,
        target_name: str = "Olivia",
        custom_instruction: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generates an instant spoken response for the target user based on the meeting context.
        """
        start_time = time.time()
        
        user_prompt = f"""NUMELE PERSOANEI ASISTATE: {target_name}

ULTIMELE MINUTE DIN ȘEDINȚĂ (TRANSCRIERE):
\"\"\"
{transcript_context}
\"\"\"
"""
        if custom_instruction:
            user_prompt += f"\nINSTRUCȚIUNE SPECIFICĂ ADĂUGATĂ: {custom_instruction}\n"
            
        user_prompt += "\nGenerează răspunsul în formatul JSON solicitat:"

        # Try Gemini first if key available
        if self.gemini_client or settings.GEMINI_API_KEY:
            try:
                return await self._call_gemini(user_prompt, start_time)
            except Exception as e:
                logger.error(f"Gemini API error: {e}. Falling back to smart heuristic engine.")

        # Fallback to intelligent contextual heuristic engine (works with 0 external API keys for testing)
        return self._generate_heuristic_response(transcript_context, target_name, start_time)

    async def _call_gemini(self, user_prompt: str, start_time: float) -> Dict[str, Any]:
        """Calls Gemini Flash with ultra-fast latency."""
        from google import genai
        from google.genai import types
        
        client = self.gemini_client or genai.Client(api_key=settings.GEMINI_API_KEY)
        
        response = client.models.generate_content(
            model=settings.LLM_MODEL,
            contents=user_prompt,
            config=types.GenerateContentConfig(
                system_instruction=ROMANIAN_COPILOT_SYSTEM_PROMPT,
                response_mime_type="application/json",
                temperature=settings.LLM_TEMPERATURE
            )
        )
        
        latency = int((time.time() - start_time) * 1000)
        parsed = json.loads(response.text)
        
        tactical = parsed.get("tactical_responses", {})
        default_spoken = parsed.get("spoken_response") or (tactical.get("agree", {}).get("spoken_response", "Sunt de acord cu planul prezentat și putem merge mai departe."))
        
        return {
            "question_detected": parsed.get("question_detected", "Întrebare adresată în ședință"),
            "spoken_response": default_spoken,
            "talking_points": parsed.get("talking_points", ["Totul este aliniat cu obiectivele stabilite."]),
            "tactical_responses": tactical,
            "latency_ms": latency,
            "model_used": settings.LLM_MODEL
        }

    def _generate_heuristic_response(self, transcript: str, target_name: str, start_time: float) -> Dict[str, Any]:
        """
        High-quality heuristic response engine with 3 tactical cards for offline/testing mode.
        """
        latency = int((time.time() - start_time) * 1000)
        transcript_lower = transcript.lower()

        if "buget" in transcript_lower or "bani" in transcript_lower or "cost" in transcript_lower:
            question = f"{target_name} a fost întrebată despre bugetul sau costurile proiectului."
            agree_spoken = "Din estimările mele inițiale, ne încadrăm în bugetul alocat și nu avem blocaje majore."
            agree_points = ["Bugetul este sub control", "Cheltuielile sunt în parametrii stabiliți"]
            caution_spoken = "Avem nevoie să validăm ultimele facturi înainte de a aproba cheltuieli suplimentare; trimit situația după ședință."
            caution_points = ["Necesită verificare financiară", "Evită depășirile neplanificate"]
        elif "termen" in transcript_lower or "termenul" in transcript_lower or "vineri" in transcript_lower or "deadline" in transcript_lower:
            question = f"{target_name} a fost întrebată dacă termenul de livrare poate fi respectat."
            agree_spoken = "Suntem în grafic cu sarcinile principale; putem livra conform termenului agreat fără probleme."
            agree_points = ["Livrabilul este în stadiu avansat", "Echipa finalizează ultimele verificări"]
            caution_spoken = "Pentru a menține calitatea și a evita riscurile, propun să alocăm o marjă de o jumătate de zi suplimentară."
            caution_points = ["Prioritate pe calitate", "Marjă de siguranță recomandată"]
        elif "raport" in transcript_lower or "document" in transcript_lower or "prezentare" in transcript_lower:
            question = f"{target_name} a fost întrebată despre statusul raportului sau documentației."
            agree_spoken = "Raportul este aproape gata; fac o ultimă revizuire pe cifre și îl distribui tuturor colegilor."
            agree_points = ["Documentul este în faza finală", "Urmează trimiterea pe canalul echipei"]
            caution_spoken = "Așteptăm datele finale din teren pentru a completa anexele; va fi gata imediat ce primim confirmările."
            caution_points = ["Depinde de date externe", "Fără compromisuri pe acuratețe"]
        else:
            question = f"{target_name} a fost solicitată pentru o opinie sau confirmare."
            agree_spoken = "Sunt pe aceeași lungime de undă cu propunerea discutată și susțin direcția propusă de echipă."
            agree_points = ["Aliniere completă cu grupul", "Disponibilitate pentru implementare"]
            caution_spoken = "Direcția este bună, dar aș prefera să analizăm impactul asupra resurselor înainte de decizia finală."
            caution_points = ["Evaluare a resurselor necesară", "Decizie prudentă"]

        # Universal stall excuse card
        stall_spoken = "Vă rog să mă scuzați, a durat o secundă să găsesc butonul de unmute. Sunt aliniată cu ideea, trimit confirmarea detaliată pe mail imediat după ședință."
        stall_points = ["Scuză universală de microfon", "Câștigă 15 secunde fără stres"]

        tactical = {
            "agree": {
                "label": "🟢 Acord / Da",
                "spoken_response": agree_spoken,
                "talking_points": agree_points
            },
            "stall": {
                "label": "🟡 Cumpără Timp (Scuză Mute)",
                "spoken_response": stall_spoken,
                "talking_points": stall_points
            },
            "caution": {
                "label": "🔴 Prudență / Amânare",
                "spoken_response": caution_spoken,
                "talking_points": caution_points
            }
        }

        return {
            "question_detected": question,
            "spoken_response": agree_spoken,
            "talking_points": agree_points,
            "tactical_responses": tactical,
            "latency_ms": max(latency, 260),
            "model_used": "copilot-smart-heuristic"
        }

llm_service = LLMService()
