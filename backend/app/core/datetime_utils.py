from datetime import datetime, timezone

def utc_now() -> datetime:
    """Returns current UTC datetime (naive) for clean SQLite storage and comparison."""
    return datetime.now(timezone.utc).replace(tzinfo=None)
