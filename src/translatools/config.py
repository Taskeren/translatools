import json
from dataclasses import asdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import dacite

from translatools.ftbquests import FTBQuestKeyGeneratingConfig, FTBQuestsChapter
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


def _write_json_from_ftbq_chapter_snbt(snbt_path: Path, json_path: Path):
    json_content = _generate_json_from_ftbquests_chapter(snbt_path)
    json_path.write_text(json_content, encoding="utf-8")


def _write_json_from_lang(lang_path: Path, json_path: Path):
    json_content = _generate_json_from_lang(lang_path)
    json_path.write_text(json_content, encoding="utf-8")


def _generate_json_from_lang(lang_path: Path) -> str:
    result = dict()
    with open(lang_path, encoding="utf-8") as lang_file:
        entries = lang_file.read().splitlines()
        for entry in entries:
            entry = entry.strip()
            # ignore comments
            if entry.startswith("#") or len(entry) == 0:
                continue
            pair = entry.split("=", maxsplit=1)
            if len(pair) != 2:
                raise ValueError(f"Unable to split the language entry: {entry}")
            # store the key-value pair
            result[pair[0]] = pair[1]

        return json.dumps(result, indent=4)


def _generate_json_from_ftbquests_chapter(snbt_path: Path,
                                          config: FTBQuestKeyGeneratingConfig = FTBQuestKeyGeneratingConfig.get_default()) -> str:
    result = dict()

    chapter = FTBQuestsChapter.load(snbt_path)
    for quest_index, quest in enumerate(chapter.quests):
        if quest.title is not None:
            result[config.get_title_key(chapter, quest, quest_index)] = quest.title
        if quest.subtitle is not None:
            result[config.get_subtitle_key(chapter, quest, quest_index)] = quest.subtitle
        if quest.description is not None:
            for desc_index, desc in enumerate(quest.description):
                result[config.get_description_key(chapter, quest, quest_index, desc_index)] = desc

    return json.dumps(result, indent=4)
