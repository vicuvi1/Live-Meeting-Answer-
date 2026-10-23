import re
import unicodedata
from collections import deque
from typing import List, Tuple, Dict, Any
from app.core.config import settings

# Comprehensive Romanian Question & Urgency Trigger Patterns
ROMANIAN_QUESTION_PATTERNS = [
    # Direct opinion / inquiry
    r"\bce (crezi|părere|parere|zici|spui|propui|avem|știi|stii|urmează|urmeaza|facem)\b",
    r"\bcum (vezi|stăm|stam|facem|procedăm|procedam|rezolvăm|rezolvam|crezi|e|ar fi)\b",
    r"\bcând (crezi|este|poți|poti|finalizăm|finalizam|trimitem|începem|incepem)\b",
    r"\bcare (este|e|ar fi|sunt|crezi că sunt|crezi ca sunt)\b",
    r"\bcine (se ocupă|se ocupa|preia|știe|stie|trimite|finalizează)\b",
    # Ability / request
    r"\bpoți (să|confirma|verifica|prelua|trimite|spune|ajuta|interveni)\b",
    r"\bpoti (sa|confirma|verifica|prelua|trimite|spune|ajuta|interveni)\b",
    r"\bputeți (să|confirma|verifica|prelua|spune|interveni)\b",
    r"\bputeti (sa|confirma|verifica|prelua|spune|interveni)\b",
    # Agreement / Status
    r"\b(ești|esti|e|sunteți|sunteti) de acord\b",
    r"\bai (primit|văzut|vazut|trimis|timp|reușit|reusit|noutăți|noutati|vești|vesti)\b",
    r"\bați (primit|văzut|vazut|trimis|timp|reușit|reusit)\b",
    r"\bati (primit|vazut|trimis|timp|reusit)\b",
    # Direct call to microphone / presence
    r"\bne (auzi|auziți|auziti|poți auzi|poti auzi|poți spune|poti spune)\b",
    r"\b(ești|esti) (pe mute|cu noi|online|disponibilă|disponibila)\b",
    r"\bdeschide (microfonul|sunetul)\b",
    r"\bte rugăm (să|sa|ne poți|ne poti)\b",
    r"\bte rugam (sa|ne poti)\b",
    r"\?$"
]

class KeywordDetector:
    def __init__(self, target_aliases: List[str] = None):
        raw_aliases = target_aliases or settings.TARGET_ALIASES
        self.target_aliases = [a.lower().strip() for a in raw_aliases]
        # Include variations without diacritics
        extra = []
        for alias in self.target_aliases:
            stripped = self.strip_diacritics(alias)
            if stripped not in self.target_aliases:
                extra.append(stripped)
        self.target_aliases.extend(extra)

        # Compiled regexes
        self.question_regexes = [re.compile(p, re.IGNORECASE) for p in ROMANIAN_QUESTION_PATTERNS]
        
        # Sliding context window of recent mentions (per session/caller)
        self.recent_mentions_history = deque(maxlen=4)

    @staticmethod
    def strip_diacritics(text: str) -> str:
        """Strips Romanian diacritics (ă->a, â->a, î->i, ș->s, ț->t) for resilient matching."""
        nfkd_form = unicodedata.normalize('NFKD', text)
        return "".join([c for c in nfkd_form if not unicodedata.combining(c)])

    def normalize_text(self, text: str) -> str:
        return text.lower().strip()

    def check_mentions_target(self, text: str, custom_target: str = None) -> Tuple[bool, str]:
        """Checks if text mentions the target name directly or honorific (Olivia, doamna Olivia)."""
        normalized = self.normalize_text(text)
        stripped = self.strip_diacritics(normalized)
        
        aliases = [custom_target.lower().strip()] if custom_target else self.target_aliases
        
        for alias in aliases:
            alias_clean = alias.lower().strip()
            alias_stripped = self.strip_diacritics(alias_clean)
            
            pattern = rf"\b{re.escape(alias_clean)}\b"
            pattern_stripped = rf"\b{re.escape(alias_stripped)}\b"
            
            if re.search(pattern, normalized) or re.search(pattern_stripped, stripped):
                return True, alias
        return False, ""

    def check_is_question(self, text: str) -> bool:
        """Determines if the text is asking a question in Romanian."""
        cleaned = text.strip()
        if cleaned.endswith("?"):
            return True
            
        stripped = self.strip_diacritics(cleaned)
        for regex in self.question_regexes:
            if regex.search(cleaned) or regex.search(stripped):
                return True
        return False

    def analyze(self, text: str, custom_target: str = None) -> Dict[str, Any]:
        """
        Deep contextual analysis of a transcript segment.
        Detects target mention, Romanian question patterns, and calculates urgency.
        """
        mentions, matched_name = self.check_mentions_target(text, custom_target)
        is_q = self.check_is_question(text)

        urgency = 0
        if mentions and is_q:
            # Direct question in the same sentence: "Olivia, ce părere ai?"
            urgency = 10
        elif mentions:
            # Target named: "A vorbit și doamna Olivia despre acest subiect."
            urgency = 7
        elif is_q:
            # General meeting question: "Care este termenul limită?"
            urgency = 4

        return {
            "mentions_target": mentions,
            "matched_name": matched_name,
            "is_question": is_q,
            "urgency": urgency
        }

    def reset_context(self):
        """Resets conversational memory between different meetings."""
        self.recent_mentions_history.clear()

detector = KeywordDetector()
