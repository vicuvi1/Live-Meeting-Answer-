import json
import logging
from typing import AsyncGenerator, Callable, Optional
import httpx
from app.core.config import settings

logger = logging.getLogger(__name__)

class STTService:
    def __init__(self):
        self.deepgram_api_key = settings.DEEPGRAM_API_KEY
        self.language = settings.DEFAULT_LANGUAGE

    def get_deepgram_ws_url(self) -> str:
        """Constructs the Deepgram streaming WebSocket URL configured for Romanian."""
        # Deepgram Nova-2 streaming parameters
        params = [
            "model=nova-2",
            f"language={self.language}",
            "punctuate=true",
            "interim_results=true",
            "endpointing=300",
            "smart_format=true",
            "encoding=linear16",
            "sample_rate=16000",
            "channels=1"
        ]
        return f"wss://api.deepgram.com/v1/listen?{'&'.join(params)}"

    def parse_deepgram_response(self, data_str: str) -> Optional[dict]:
        """
        Parses Deepgram JSON responses and extracts clean transcript text,
        speaker label, confidence, and finality flag.
        """
        try:
            payload = json.loads(data_str)
            channel = payload.get("channel", {})
            alternatives = channel.get("alternatives", [{}])
            if not alternatives:
                return None
            
            first_alt = alternatives[0]
            transcript = first_alt.get("transcript", "").strip()
            confidence = first_alt.get("confidence", 1.0)
            is_final = payload.get("is_final", False)
            
            if not transcript:
                return None
                
            return {
                "text": transcript,
                "is_final": is_final,
                "confidence": confidence,
                "speaker": "Participant"
            }
        except Exception as e:
            logger.error(f"Error parsing STT payload: {e}")
            return None

stt_service = STTService()
