from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import PlainTextResponse
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.models.session import MeetingSession
from app.models.transcript import TranscriptSegment
from app.models.answer import AiAnswer
from app.schemas.schemas import SessionCreate, SessionOut, SessionUpdate, SummaryResponse
from app.services.summary_service import summary_service

router = APIRouter(prefix="/api/sessions", tags=["Sessions"])

@router.post("", response_model=SessionOut, status_code=status.HTTP_201_CREATED)
async def create_session(data: SessionCreate, db: AsyncSession = Depends(get_db)):
    """Starts a new meeting session."""
    session = MeetingSession(
        title=data.title or f"Ședință {datetime.now().strftime('%d.%m.%Y %H:%M')}",
        language=data.language or "ro",
        status="active"
    )
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session

@router.get("", response_model=List[SessionOut])
async def list_sessions(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db)
):
    """Lists past meeting sessions ordered by most recent."""
    query = select(MeetingSession).order_by(desc(MeetingSession.start_time)).offset(offset).limit(limit)
    result = await db.execute(query)
    return result.scalars().all()

@router.get("/{session_id}", response_model=SessionOut)
async def get_session(session_id: str, db: AsyncSession = Depends(get_db)):
    """Retrieves session details."""
    query = select(MeetingSession).where(MeetingSession.id == session_id)
    result = await db.execute(query)
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")
    return session

@router.patch("/{session_id}", response_model=SessionOut)
async def update_session(session_id: str, data: SessionUpdate, db: AsyncSession = Depends(get_db)):
    """Updates session metadata or ends the session."""
    query = select(MeetingSession).where(MeetingSession.id == session_id)
    result = await db.execute(query)
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")

    if data.title is not None:
        session.title = data.title
    if data.status is not None:
        session.status = data.status
        if data.status == "completed" and not session.end_time:
            session.end_time = datetime.utcnow()
            diff = (session.end_time - session.start_time).total_seconds()
            session.duration_seconds = max(0, int(diff))
    if data.summary is not None:
        session.summary = data.summary
    if data.action_items is not None:
        session.action_items = data.action_items

    await db.commit()
    await db.refresh(session)
    return session

@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_session(session_id: str, db: AsyncSession = Depends(get_db)):
    """Deletes a session and its transcripts."""
    query = select(MeetingSession).where(MeetingSession.id == session_id)
    result = await db.execute(query)
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")
    await db.delete(session)
    await db.commit()
    return None

@router.post("/{session_id}/summarize", response_model=SummaryResponse)
async def summarize_session(session_id: str, db: AsyncSession = Depends(get_db)):
    """Generates an AI executive summary and action items for the session."""
    query = select(MeetingSession).options(selectinload(MeetingSession.transcripts)).where(MeetingSession.id == session_id)
    result = await db.execute(query)
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")

    # Combine transcripts into full text
    full_text = "\n".join([f"[{t.timestamp.strftime('%H:%M:%S')}] {t.speaker}: {t.text}" for t in session.transcripts])
    summary_data = await summary_service.generate_meeting_summary(full_text)

    # Save to session
    session.summary = summary_data.get("summary", "")
    session.action_items = "\n".join(summary_data.get("action_items", []))
    if summary_data.get("title") and session.title.startswith("Ședință Nouă"):
        session.title = summary_data["title"]

    await db.commit()

    return {
        "session_id": session.id,
        "title": session.title,
        "summary": session.summary,
        "action_items": summary_data.get("action_items", []),
        "questions_addressed_to_user": summary_data.get("questions_addressed_to_user", [])
    }

@router.get("/{session_id}/export")
async def export_session_markdown(session_id: str, db: AsyncSession = Depends(get_db)):
    """Exports the entire meeting session, transcripts, and AI answers as clean Markdown."""
    query = (
        select(MeetingSession)
        .options(selectinload(MeetingSession.transcripts), selectinload(MeetingSession.answers))
        .where(MeetingSession.id == session_id)
    )
    result = await db.execute(query)
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Sesiunea nu a fost găsită")

    md = []
    md.append(f"# {session.title}")
    md.append(f"**Data:** {session.start_time.strftime('%Y-%m-%d %H:%M:%S')}  ")
    md.append(f"**Durată:** {session.duration_seconds // 60} minute ({session.duration_seconds}s)  ")
    md.append(f"**Status:** {session.status.upper()}\n")

    if session.summary:
        md.append("## Rezumat Executiv")
        md.append(session.summary + "\n")

    if session.action_items:
        md.append("## Plan de Acțiune / Decizii")
        md.append(session.action_items + "\n")

    if session.answers:
        md.append("## Răspunsuri Sugerate de Copilot (Olivia)")
        for ans in session.answers:
            md.append(f"- **[{ans.generated_at.strftime('%H:%M:%S')}] Întrebare:** {ans.question_detected}")
            md.append(f"  - **Răspuns sugerat:** *\"{ans.spoken_response}\"*")
        md.append("")

    md.append("## Transcriere Detaliată")
    for t in session.transcripts:
        prefix = "⚡ " if t.mentions_target else ""
        md.append(f"- `{t.timestamp.strftime('%H:%M:%S')}` **{prefix}{t.speaker}:** {t.text}")

    content = "\n".join(md)
    return PlainTextResponse(content=content, media_type="text/markdown")
