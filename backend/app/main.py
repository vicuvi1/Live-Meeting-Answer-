import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import init_db
from app.api import sessions, transcripts, copilot, websocket

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("olivia_copilot")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: ensure DB tables exist
    await init_db()
    yield
    # Shutdown logic if needed

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Backend engine for Olivia's live meeting assistant and Romanian AI copilot.",
    lifespan=lifespan
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global Exception Handler (Never-Crash Guard)
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"[NEVER-CRASH GUARD] Caught unhandled exception on {request.method} {request.url.path}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "status": "error",
            "message": "A server error occurred, but the system intercepted it to prevent any crash.",
            "error_type": exc.__class__.__name__,
            "detail": str(exc)
        }
    )

# Register routers
app.include_router(sessions.router)
app.include_router(transcripts.router)
app.include_router(copilot.router)
app.include_router(websocket.router)

@app.get("/api/health", tags=["Health"])
async def health_check():
    return {
        "status": "healthy",
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "target_name": settings.TARGET_NAME,
        "default_language": settings.DEFAULT_LANGUAGE,
        "gemini_configured": bool(settings.GEMINI_API_KEY),
        "openrouter_configured": bool(settings.OPENROUTER_API_KEY),
        "deepgram_configured": bool(settings.DEEPGRAM_API_KEY)
    }
