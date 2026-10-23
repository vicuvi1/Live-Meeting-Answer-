import json
import logging
from typing import Dict, Set
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from sqlalchemy import select
from datetime import datetime

from app.core.database import AsyncSessionLocal
from app.models.transcript import TranscriptSegment
from app.models.session import MeetingSession
from app.models.answer import AiAnswer
from app.services.keyword_detector import detector
from app.services.llm_service import llm_service
from app.core.config import settings
from app.core.datetime_utils import utc_now

logger = logging.getLogger(__name__)

router = APIRouter(tags=["WebSocket"])

class ConnectionManager:
    def __init__(self):
        # Maps session_id -> Set of active WebSocket connections
        self.active_connections: Dict[str, Set[WebSocket]] = {}

    async def connect(self, session_id: str, websocket: WebSocket):
        await websocket.accept()
        if session_id not in self.active_connections:
            self.active_connections[session_id] = set()
        self.active_connections[session_id].add(websocket)
        logger.info(f"WebSocket client connected to session {session_id}")

    def disconnect(self, session_id: str, websocket: WebSocket):
        if session_id in self.active_connections:
            self.active_connections[session_id].discard(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"WebSocket client disconnected from session {session_id}")

    async def broadcast(self, session_id: str, message: dict):
        if session_id in self.active_connections:
            serialized = json.dumps(message, ensure_ascii=False)
            dead_connections = set()
            for connection in list(self.active_connections.get(session_id, [])):
                try:
                    await connection.send_text(serialized)
                except Exception:
                    dead_connections.add(connection)
            for dead in dead_connections:
                if session_id in self.active_connections:
                    self.active_connections[session_id].discard(dead)

manager = ConnectionManager()

@router.websocket("/ws/{session_id}")
async def websocket_session_endpoint(websocket: WebSocket, session_id: str):
    await manager.connect(session_id, websocket)
    try:
        while True:
            data_raw = await websocket.receive_text()
            try:
                msg = json.loads(data_raw)
            except Exception:
                continue

            msg_type = msg.get("type")

            # 1. Heartbeat / Ping
            if msg_type == "PING":
                await websocket.send_text(json.dumps({"type": "PONG"}))
                continue

            # 2. Live Transcript Segment pushed from client or audio processor
            elif msg_type == "TRANSCRIPT_CHUNK":
                text = msg.get("text", "").strip()
                speaker = msg.get("speaker", "Participant")
                is_final = msg.get("is_final", True)
                
                if not text:
                    continue

                analysis = detector.analyze(text)

                # Save to database asynchronously
                async with AsyncSessionLocal() as db:
                    segment = TranscriptSegment(
                        session_id=session_id,
                        speaker=speaker,
                        text=text,
                        is_final=is_final,
                        mentions_target=analysis["mentions_target"],
                        is_question=analysis["is_question"],
                        timestamp=utc_now()
                    )
                    db.add(segment)
                    await db.commit()
                    await db.refresh(segment)

                    broadcast_payload = {
                        "type": "TRANSCRIPT_SEGMENT",
                        "id": segment.id,
                        "session_id": session_id,
                        "speaker": segment.speaker,
                        "text": segment.text,
                        "timestamp": segment.timestamp.strftime("%H:%M:%S"),
                        "is_final": segment.is_final,
                        "mentions_target": segment.mentions_target,
                        "is_question": segment.is_question,
                        "urgency": analysis["urgency"]
                    }
                    await manager.broadcast(session_id, broadcast_payload)

                    # If Olivia is mentioned or high urgency, broadcast alert AND precompute answers in background
                    if analysis["mentions_target"] or analysis["urgency"] >= 7:
                        await manager.broadcast(session_id, {
                            "type": "TARGET_ALERT",
                            "speaker": speaker,
                            "text": text,
                            "urgency": analysis["urgency"]
                        })

                        # Zero-Latency Speculative Pre-computation:
                        # Asynchronously draft the 3 tactical answers so they are ready before she taps the button!
                        import asyncio
                        async def precompute_speculative_answers(sess_id: str, tgt_name: str):
                            try:
                                async with AsyncSessionLocal() as bg_db:
                                    t_q = (
                                        select(TranscriptSegment)
                                        .where(TranscriptSegment.session_id == sess_id)
                                        .order_by(TranscriptSegment.timestamp.desc())
                                        .limit(20)
                                    )
                                    bg_res = await bg_db.execute(t_q)
                                    recent_segs = list(reversed(bg_res.scalars().all()))
                                    ctx = "\n".join([f"[{s.timestamp.strftime('%H:%M:%S')}] {s.speaker}: {s.text}" for s in recent_segs])
                                    
                                    ai_spec = await llm_service.generate_quick_answer(
                                        transcript_context=ctx,
                                        target_name=tgt_name
                                    )

                                    await manager.broadcast(sess_id, {
                                        "type": "PRECOMPUTED_ANSWERS",
                                        "question_detected": ai_spec["question_detected"],
                                        "spoken_response": ai_spec["spoken_response"],
                                        "talking_points": ai_spec["talking_points"],
                                        "tactical_responses": ai_spec.get("tactical_responses"),
                                        "latency_ms": ai_spec.get("latency_ms", 0),
                                        "generated_at": utc_now().strftime("%H:%M:%S")
                                    })
                            except Exception as err:
                                logger.warning(f"Speculative pre-computation error: {err}")

                        resolved_target = msg.get("target_name") or settings.TARGET_NAME
                        asyncio.create_task(precompute_speculative_answers(session_id, resolved_target))

            # 3. Direct Request for Quick Answer
            elif msg_type == "REQUEST_QUICK_ANSWER":
                target_name = msg.get("target_name", settings.TARGET_NAME)
                
                async with AsyncSessionLocal() as db:
                    # Gather context
                    transcript_q = (
                        select(TranscriptSegment)
                        .where(TranscriptSegment.session_id == session_id)
                        .order_by(TranscriptSegment.timestamp.desc())
                        .limit(25)
                    )
                    res = await db.execute(transcript_q)
                    segments = list(reversed(res.scalars().all()))
                    
                    context_text = "\n".join([f"[{s.timestamp.strftime('%H:%M:%S')}] {s.speaker}: {s.text}" for s in segments])
                    
                    ai_res = await llm_service.generate_quick_answer(
                        transcript_context=context_text,
                        target_name=target_name
                    )

                    ans_record = AiAnswer(
                        session_id=session_id,
                        question_detected=ai_res["question_detected"],
                        spoken_response=ai_res["spoken_response"],
                        talking_points=json.dumps(ai_res["talking_points"], ensure_ascii=False),
                        tactical_responses=json.dumps(ai_res.get("tactical_responses", {}), ensure_ascii=False),
                        context_used=context_text,
                        llm_model=ai_res.get("model_used", "gemini-2.5-flash"),
                        latency_ms=ai_res.get("latency_ms", 0)
                    )
                    db.add(ans_record)
                    await db.commit()
                    await db.refresh(ans_record)

                    # Send back to all listeners of this session
                    await manager.broadcast(session_id, {
                        "type": "QUICK_ANSWER",
                        "id": ans_record.id,
                        "question_detected": ans_record.question_detected,
                        "spoken_response": ans_record.spoken_response,
                        "talking_points": ai_res["talking_points"],
                        "tactical_responses": ai_res.get("tactical_responses"),
                        "latency_ms": ans_record.latency_ms,
                        "generated_at": ans_record.generated_at.strftime("%H:%M:%S")
                    })

    except WebSocketDisconnect:
        manager.disconnect(session_id, websocket)
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        manager.disconnect(session_id, websocket)
