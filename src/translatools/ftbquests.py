import json
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import ftb_snbt_lib
from dacite import from_dict


@dataclass
class FTBQuestsChapterQuest:
    id: str
    title: Optional[str]
    subtitle: Optional[str]
    description: Optional[list[str]]


@dataclass
class FTBQuestsChapter:
    id: str
    filename: str
    quests: list[FTBQuestsChapterQuest]

    @staticmethod
    def load(snbt_path: Path) -> "FTBQuestsChapter":
        with open(snbt_path, encoding="utf-8") as f:
            snbt = ftb_snbt_lib.load(f)
            # FIXME: find a better solution
            json_ = json.dumps(snbt)
            data = json.loads(json_)
            return from_dict(data_class=FTBQuestsChapter, data=data)


@dataclass
class FTBQuestKeyGeneratingConfig:
    quest_title: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.title"
    quest_subtitle: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.subtitle"
    quest_description: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.description.{description_index}"
    generate_description_index_for_empty_lines: bool = False

    @staticmethod
    def get_default() -> "FTBQuestKeyGeneratingConfig":
        return FTBQuestKeyGeneratingConfig()

    @staticmethod
    def load(name_or_dict: str | dict) -> "FTBQuestKeyGeneratingConfig":
        """
        Get the pre-defined config from the given name or create a config from the given dict data.
        """
        if isinstance(name_or_dict, dict):
            return FTBQuestKeyGeneratingConfig(**name_or_dict)
        elif name_or_dict == "default":
            return FTBQuestKeyGeneratingConfig.get_default()
        # maybe other translation injector?
        else:
            raise ValueError("Invalid name or data for FTBQuestKeyGeneratingConfig")

    @staticmethod
    def _replace_by_chapter(s: str, chapter: FTBQuestsChapter) -> str:
        return (s.replace("{chapter_id}", chapter.id)
                .replace("{chapter_filename}", chapter.filename))

    @staticmethod
    def _replace_by_quest(s: str, quest: FTBQuestsChapterQuest, index: int) -> str:
        return (s.replace("{quest_id}", quest.id)
                .replace("{quest_index}", str(index)))

    @staticmethod
    def _replace_by_description_index(s: str, description_index: int) -> str:
        return s.replace("{description_index}", str(description_index))

    def get_title_key(self, chapter: FTBQuestsChapter, quest: FTBQuestsChapterQuest, quest_index: int):
        key = self.quest_title
        key = self._replace_by_chapter(key, chapter)
        key = self._replace_by_quest(key, quest, quest_index)
        return key

    def get_subtitle_key(self, chapter: FTBQuestsChapter, quest: FTBQuestsChapterQuest, quest_index: int):
        key = self.quest_subtitle
        key = self._replace_by_chapter(key, chapter)
        key = self._replace_by_quest(key, quest, quest_index)
        return key

    def get_description_key(self, chapter: FTBQuestsChapter, quest: FTBQuestsChapterQuest, quest_index: int,
                            description_index: int):
        key = self.quest_description
        key = self._replace_by_chapter(key, chapter)
        key = self._replace_by_quest(key, quest, quest_index)
        key = self._replace_by_description_index(key, description_index)
        return key
