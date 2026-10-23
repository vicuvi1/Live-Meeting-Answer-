import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db, engine, Base
from app.services.keyword_detector import detector

@pytest.mark.asyncio
async def test_keyword_detector_romanian():
    # Test target detection
    analysis1 = detector.analyze("Olivia, te rog să ne spui dacă bugetul este aprobat?")
    assert analysis1["mentions_target"] is True
    assert analysis1["is_question"] is True
    assert analysis1["urgency"] == 10

    # Test question without target
    analysis2 = detector.analyze("Când putem programa următoarea discuție?")
    assert analysis2["mentions_target"] is False
    assert analysis2["is_question"] is True
    assert analysis2["urgency"] == 4

    # Test normal statement
    analysis3 = detector.analyze("Astăzi am finalizat raportul de activitate.")
    assert analysis3["mentions_target"] is False
    assert analysis3["is_question"] is False
    assert analysis3["urgency"] == 0

@pytest.mark.asyncio
async def test_full_copilot_workflow():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Health check
        res = await client.get("/api/health")
        assert res.status_code == 200
        assert res.json()["status"] == "healthy"

        # 2. Create session
        create_res = await client.post("/api/sessions", json={"title": "Ședință Test Buget", "language": "ro"})
        assert create_res.status_code == 201
        session_data = create_res.json()
        session_id = session_data["id"]
        assert session_data["title"] == "Ședință Test Buget"

        # 3. Add transcript segments
        t1 = await client.post("/api/transcripts", json={
            "session_id": session_id,
            "speaker": "Director",
            "text": "Bună ziua tuturor, începem analiza trimestrială."
        })
        assert t1.status_code == 201
        assert t1.json()["mentions_target"] is False

        t2 = await client.post("/api/transcripts", json={
            "session_id": session_id,
            "speaker": "Director",
            "text": "Olivia, ce părere ai despre termenul de livrare pentru vineri?"
        })
        assert t2.status_code == 201
        assert t2.json()["mentions_target"] is True
        assert t2.json()["is_question"] is True

        # 4. Trigger Quick Answer ("Ce să răspund?")
        ans_res = await client.post("/api/copilot/quick-answer", json={
            "session_id": session_id,
            "target_name": "Olivia"
        })
        assert ans_res.status_code == 200
        ans_data = ans_res.json()
        assert "spoken_response" in ans_data
        assert len(ans_data["spoken_response"]) > 5
        assert "question_detected" in ans_data
        assert isinstance(ans_data["talking_points"], list)
        assert "tactical_responses" in ans_data
        assert "agree" in ans_data["tactical_responses"]
        assert "stall" in ans_data["tactical_responses"]
        assert "caution" in ans_data["tactical_responses"]

        # 5. Record feedback
        feedback_res = await client.post(f"/api/copilot/answers/{ans_data['id']}/feedback", json={
            "feedback": "used"
        })
        assert feedback_res.status_code == 200

        # 6. Summarize session
        sum_res = await client.post(f"/api/sessions/{session_id}/summarize")
        assert sum_res.status_code == 200
        assert "summary" in sum_res.json()

        # 7. Export markdown
        export_res = await client.get(f"/api/sessions/{session_id}/export")
        assert export_res.status_code == 200
        assert "Ședință Test Buget" in export_res.text
        assert "Olivia" in export_res.text

        # 8. Test key validation endpoint
        test_key_res = await client.post("/api/copilot/test-key", json={
            "provider": "openrouter",
            "api_key": "sk-dummy-test-key",
            "model": "google/gemini-2.0-flash-exp:free"
        })
        assert test_key_res.status_code == 200
        test_key_data = test_key_res.json()
        assert "success" in test_key_data
        assert "message" in test_key_data
