import re
from typing import List, Tuple
from app.core.config import settings

# Romanian question indicators & polite address keywords
ROMANIAN_QUESTION_PATTERNS = [
    r"\bce (crezi|părere ai|zici|spui|propui|avem)\b",
    r"\bpoți (să|confirma|verifica|prelua)\b",
    r"\bcum (vezi|stăm|facem|procedăm)\b",
    r"\bcând (crezi|este|poți|finalizăm)\b",
    r"\bcare (este|e|ar fi)\b",
    r"\b(ești|e) de acord\b",
    r"\bai (primit|văzut|trimis|timp)\b",
    r"\?$"
]

class KeywordDetector:
    def __init__(self, target_aliases: List[str] = None):
        self.target_aliases = [a.lower().strip() for a in (target_aliases or settings.TARGET_ALIASES)]
        # Compile regexes
        self.question_regexes = [re.compile(p, re.IGNORECASE) for p in ROMANIAN_QUESTION_PATTERNS]

    def normalize_text(self, text: str) -> str:
        """Removes excessive spaces and normalizes romanian diacritics for matching."""
        return text.lower().strip()

    def check_mentions_target(self, text: str, custom_target: str = None) -> Tuple[bool, str]:
        """
        Checks if the text mentions the target name (e.g. Olivia, doamna Olivia).
        Returns (is_mentioned, matched_name)
        """
        normalized = self.normalize_text(text)
        aliases = [custom_target.lower().strip()] if custom_target else self.target_aliases
        
        for alias in aliases:
            # Word boundary check
            pattern = rf"\b{re.escape(alias)}\b"
            if re.search(pattern, normalized):
                return True, alias
        return False, ""

    def check_is_question(self, text: str) -> bool:
        """Checks if the Romanian text is formulating a question."""
        cleaned = text.strip()
        if cleaned.endswith("?"):
            return True
        for regex in self.question_regexes:
            if regex.search(cleaned):
                return True
        return False

    def analyze(self, text: str, custom_target: str = None) -> dict:
        """
        Comprehensive analysis of a transcript segment.
        Returns whether Olivia is called, whether it's a question, and an urgency score.
        """
        mentions, matched_name = self.check_mentions_target(text, custom_target)
        is_q = self.check_is_question(text)
        
        # Urgency: 
        # 10: Olivia was directly asked a question (e.g., "Olivia, ce părere ai?")
        # 7:  Olivia was mentioned
        # 4:  General question in the room
        # 0:  Normal statement
        urgency = 0
        if mentions and is_q:
            urgency = 10
        elif mentions:
            urgency = 7
        elif is_q:
            urgency = 4
            
        return {
            "mentions_target": mentions,
            "matched_name": matched_name,
            "is_question": is_q,
            "urgency": urgency
        }

detector = KeywordDetector()
