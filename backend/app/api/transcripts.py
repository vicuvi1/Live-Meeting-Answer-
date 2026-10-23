from typing import List
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.transcript import TranscriptSegment
from app.models.session import MeetingSession
from app.schemas.schemas import TranscriptCreate, TranscriptOut
from app.services.keyword_detector import detector

router = APIRouter(prefix="/api/transcripts", tags=["Transcripts"])

@router.get("/{session_id}", response_model=List[TranscriptOut])
async def get_transcripts_for_session(
    session_id: str,
    limit: int = Query(200, ge=1, le=1000),
    db: AsyncSession = Depends(get_db)
):
    """Fetches all transcripts for a meeting session in chronological order."""
    query = (
        select(TranscriptSegment)
        .where(TranscriptSegment.session_id == session_id)
        .order_by(TranscriptSegment.timestamp.asc())
        .limit(limit)
    )
    result = await db.execute(query)
    return result.scalars().all()

@router.post("", response_model=TranscriptOut, status_code=status.HTTP_201_CREATED)
async def add_transcript_segment(data: TranscriptCreate, db: AsyncSession = Depends(get_db)):
    """Appends a new transcript line and analyzes it for keyword mentions or target name."""
    # Verify session exists
    session_q = select(MeetingSession).where(MeetingSession.id == data.session_id)
    session_res = await db.execute(session_q)
    session = session_res.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea specificată nu există")

    # Analyze with Romanian keyword detector
    analysis = detector.analyze(data.text)

    segment = TranscriptSegment(
        session_id=data.session_id,
        text=data.text.strip(),
        speaker=data.speaker or "Participant",
        is_final=data.is_final if data.is_final is not None else True,
        relative_seconds=data.relative_seconds or 0.0,
        mentions_target=analysis["mentions_target"],
        is_question=analysis["is_question"]
    )

    db.add(segment)
    await db.commit()
    await db.refresh(segment)
    
    return segment
