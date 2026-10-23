# Core Configuration for Olivia Meeting Copilot
from typing import List, Optional
from pydantic_settings import BaseSettings
from pydantic import Field
import os

class Settings(BaseSettings):
    APP_NAME: str = "Olivia Meeting Copilot"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # Target / User personalization
    TARGET_NAME: str = "Olivia"
    TARGET_ALIASES: List[str] = ["olivia", "oli", "doamna olivia", "doamna oli"]
    DEFAULT_LANGUAGE: str = "ro"  # Romanian
    
    # Database
    DATABASE_URL: str = "sqlite+aiosqlite:///./data/copilot.db"
    
    # AI Models & Keys
    GEMINI_API_KEY: Optional[str] = Field(default=None, validation_alias="GEMINI_API_KEY")
    OPENROUTER_API_KEY: Optional[str] = Field(default=None, validation_alias="OPENROUTER_API_KEY")
    DEEPGRAM_API_KEY: Optional[str] = Field(default=None, validation_alias="DEEPGRAM_API_KEY")
    GROQ_API_KEY: Optional[str] = Field(default=None, validation_alias="GROQ_API_KEY")
    OPENAI_API_KEY: Optional[str] = Field(default=None, validation_alias="OPENAI_API_KEY")
    
    # Provider & Model Settings
    DEFAULT_PROVIDER: str = "gemini"  # "gemini" | "openrouter" | "heuristic"
    LLM_MODEL: str = "gemini-2.5-flash"  # Ultra-fast, highly capable multilingual model
    OPENROUTER_MODEL: str = "google/gemini-2.0-flash-exp:free" # Free model on OpenRouter
    OPENROUTER_BASE_URL: str = "https://openrouter.ai/api/v1"
    LLM_TEMPERATURE: float = 0.2
    
    # Buffer & Detection
    MAX_BUFFER_MINUTES: int = 5
    MAX_BUFFER_SEGMENTS: int = 50
    
    # CORS
    CORS_ORIGINS: List[str] = ["*"]

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "extra": "ignore"
    }

settings = Settings()

# Ensure local data directory exists
os.makedirs("./data", exist_ok=True)
