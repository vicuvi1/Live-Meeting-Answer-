import json
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.transcript import TranscriptSegment
from app.models.session import MeetingSession
from app.models.answer import AiAnswer
from app.schemas.schemas import (
    QuickAnswerRequest, 
    QuickAnswerResponse, 
    AnswerFeedbackRequest,
    TestKeyRequest,
    TestKeyResponse
)
from app.services.llm_service import llm_service
from app.core.config import settings

router = APIRouter(prefix="/api/copilot", tags=["Copilot"])

@router.post("/quick-answer", response_model=QuickAnswerResponse)
async def generate_quick_answer(request: QuickAnswerRequest, db: AsyncSession = Depends(get_db)):
    """
    Core Lifesaver Button Endpoint:
    Fetches the recent meeting transcript chunk and prompts the AI to produce
    the exact spoken Romanian response for Olivia to say.
    Supports Google Gemini (direct free/paid) and OpenRouter models.
    """
    # Verify session
    session_q = select(MeetingSession).where(MeetingSession.id == request.session_id)
    session_res = await db.execute(session_q)
    session = session_res.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")

    # Fetch last 30 segments (approx 3-5 minutes of speech)
    transcript_q = (
        select(TranscriptSegment)
        .where(TranscriptSegment.session_id == request.session_id)
        .order_by(desc(TranscriptSegment.timestamp))
        .limit(30)
    )
    res = await db.execute(transcript_q)
    segments = list(reversed(res.scalars().all()))

    if not segments and not request.override_context:
        context_text = f"Ședința tocmai a început. Participanții se salută și fac prezența. {request.target_name or 'Olivia'} este prezentă."
    elif request.override_context:
        context_text = request.override_context
    else:
        context_text = "\n".join([f"[{s.timestamp.strftime('%H:%M:%S')}] {s.speaker}: {s.text}" for s in segments])

    target = request.target_name or settings.TARGET_NAME

    # Call AI Engine (handles Gemini, OpenRouter, or smart fallback)
    ai_result = await llm_service.generate_quick_answer(
        transcript_context=context_text,
        target_name=target,
        custom_instruction=request.custom_instruction,
        provider=request.provider,
        api_key=request.api_key,
        model=request.model
    )

    # Persist in DB
    answer_record = AiAnswer(
        session_id=request.session_id,
        question_detected=ai_result["question_detected"],
        spoken_response=ai_result["spoken_response"],
        talking_points=json.dumps(ai_result["talking_points"], ensure_ascii=False),
        tactical_responses=json.dumps(ai_result.get("tactical_responses", {}), ensure_ascii=False),
        context_used=context_text,
        llm_model=ai_result.get("model_used", "gemini-2.5-flash"),
        latency_ms=ai_result.get("latency_ms", 0)
    )
    db.add(answer_record)
    await db.commit()
    await db.refresh(answer_record)

    return QuickAnswerResponse(
        id=answer_record.id,
        session_id=answer_record.session_id,
        question_detected=answer_record.question_detected,
        spoken_response=answer_record.spoken_response,
        talking_points=ai_result["talking_points"],
        tactical_responses=ai_result.get("tactical_responses"),
        latency_ms=answer_record.latency_ms,
        generated_at=answer_record.generated_at,
        model_used=answer_record.llm_model
    )

@router.get("/answers/{session_id}")
async def get_answers_for_session(session_id: str, db: AsyncSession = Depends(get_db)):
    """Retrieves all past AI-generated answers for this session."""
    query = select(AiAnswer).where(AiAnswer.session_id == session_id).order_by(desc(AiAnswer.generated_at))
    result = await db.execute(query)
    answers = result.scalars().all()
    
    return [
        {
            "id": a.id,
            "session_id": a.session_id,
            "question_detected": a.question_detected,
            "spoken_response": a.spoken_response,
            "talking_points": json.loads(a.talking_points) if a.talking_points else [],
            "tactical_responses": json.loads(a.tactical_responses) if a.tactical_responses else None,
            "latency_ms": a.latency_ms,
            "generated_at": a.generated_at,
            "feedback": a.feedback
        }
        for a in answers
    ]

@router.post("/answers/{answer_id}/feedback")
async def record_answer_feedback(
    answer_id: str,
    feedback_req: AnswerFeedbackRequest,
    db: AsyncSession = Depends(get_db)
):
    """Saves user feedback for a suggested answer (e.g. used, dismissed)."""
    query = select(AiAnswer).where(AiAnswer.id == answer_id)
    result = await db.execute(query)
    answer = result.scalar_one_or_none()
    if not answer:
        raise HTTPException(status_code=404, detail="Răspunsul nu a fost găsit")

    answer.feedback = feedback_req.feedback
    await db.commit()
    return {"status": "success", "feedback": answer.feedback}

@router.post("/test-key", response_model=TestKeyResponse)
async def test_api_key(req: TestKeyRequest):
    """Tests if a user-supplied Google Gemini or OpenRouter key is valid."""
    result = await llm_service.test_connection(
        provider=req.provider,
        api_key=req.api_key.strip(),
        model=req.model
    )
    return TestKeyResponse(
        success=result["success"],
        message=result["message"],
        model_used=result["model_used"],
        latency_ms=result["latency_ms"]
    )
