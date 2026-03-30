import json
from dataclasses import asdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import dacite

from translatools.ftbquests import FTBQuestKeyGeneratingConfig
from translatools.handler import TRANSLATION_HANDLERS, TranslationHandler


@dataclass
class TrackedItem:
    type: str
    name: Optional[str] = field(default=None)
    extra: dict = field(default_factory=dict)

    @property
    def handler(self) -> "TranslationHandler":
        handler = TRANSLATION_HANDLERS.get(self.type)
        if handler is None:
            raise ValueError(f"Unknown handler: {self.type}, expected {TRANSLATION_HANDLERS.keys()}")
        return handler

    def get_name(self) -> str:
        if self.name is not None:
            return self.name
        return self.type


@dataclass
class TranslatoolsMetadata:
    # the CurseForge project ID
    project_id: int
    # the Paratranz project ID
    paratranz_id: int
    # the CurseForge file ID, or 0 for unknown or uninitialized
    current_version_id: int = 0
    # the tracked 2.0
    tracked_items: list[TrackedItem] = field(default_factory=list)
    # dotenv name
    dotenv_name: Optional[str] = field(default=None)
    # resourcepack format
    # https://minecraft.wiki/w/Pack_format
    pack_format: int = field(default=15)
    # resourcepack description
    pack_description: Optional[str] = field(default=None)
    # ftbquests key generation config
    ftbquests_key_config: str | dict = field(default="default")

    @staticmethod
    def load_from_path(path: Path) -> "TranslatoolsMetadata":
        return dacite.from_dict(TranslatoolsMetadata, json.loads(path.read_text(encoding="utf-8")))

    @staticmethod
    def write_to_path(path: Path, config: "TranslatoolsMetadata"):
        path.write_text(json.dumps(asdict(config), indent=4), encoding="utf-8")

    def get_ftbquests_key_config(self) -> FTBQuestKeyGeneratingConfig:
        return FTBQuestKeyGeneratingConfig.load(self.ftbquests_key_config)
