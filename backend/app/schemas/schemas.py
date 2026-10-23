from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, Field, ConfigDict

# --- Session Schemas ---
class SessionCreate(BaseModel):
    title: Optional[str] = "Ședință Nouă"
    language: Optional[str] = "ro"

class SessionUpdate(BaseModel):
    title: Optional[str] = None
    status: Optional[str] = None
    summary: Optional[str] = None
    action_items: Optional[str] = None

class SessionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    language: str
    status: str
    start_time: datetime
    end_time: Optional[datetime] = None
    duration_seconds: int
    summary: Optional[str] = None
    action_items: Optional[str] = None

# --- Transcript Schemas ---
class TranscriptCreate(BaseModel):
    session_id: str
    text: str
    speaker: Optional[str] = "Participant"
    is_final: Optional[bool] = True
    relative_seconds: Optional[float] = 0.0

class TranscriptOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    session_id: str
    speaker: str
    text: str
    timestamp: datetime
    relative_seconds: float
    is_final: bool
    mentions_target: bool
    is_question: bool
    confidence: float

# --- AI Answer & Copilot Schemas ---
class TacticalCard(BaseModel):
    label: str
    spoken_response: str
    talking_points: List[str] = Field(default_factory=list)

class QuickAnswerRequest(BaseModel):
    session_id: str
    target_name: Optional[str] = "Olivia"
    custom_instruction: Optional[str] = None
    override_context: Optional[str] = None
    provider: Optional[str] = None  # "gemini" | "openrouter"
    api_key: Optional[str] = None   # dynamically provided key
    model: Optional[str] = None     # e.g., "google/gemini-2.0-flash-exp:free" or "gemini-2.5-flash"

class QuickAnswerResponse(BaseModel):
    id: str
    session_id: str
    question_detected: str
    spoken_response: str
    talking_points: List[str]
    tactical_responses: Optional[dict] = None  # { "agree": ..., "stall": ..., "caution": ... }
    latency_ms: int
    generated_at: datetime
    model_used: str

class TestKeyRequest(BaseModel):
    provider: str = Field(..., description="'gemini' or 'openrouter'")
    api_key: str
    model: Optional[str] = None

class TestKeyResponse(BaseModel):
    success: bool
    message: str
    model_used: str
    latency_ms: int

class AnswerFeedbackRequest(BaseModel):
    feedback: str = Field(..., description="'used', 'dismissed', 'thumbs_up', 'thumbs_down'")

# --- Summary Schemas ---
class SummaryGenerateRequest(BaseModel):
    session_id: str
    detailed: Optional[bool] = True

class SummaryResponse(BaseModel):
    session_id: str
    title: str
    summary: str
    action_items: List[str]
    questions_addressed_to_user: List[str]
