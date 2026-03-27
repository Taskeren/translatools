import json
import traceback
from dataclasses import dataclass
from os import PathLike
from pathlib import Path
from typing import Generator

import cursefetch
import ftb_snbt_lib
from ftb_snbt_lib.tag import Compound
from ftb_snbt_lib.tag import List
from tqdm import tqdm

from translatools import TranslatoolsMetadata, Paratranz


class Translatools:
    config: TranslatoolsMetadata
    _cwd: Path | None

    def __init__(self, config: TranslatoolsMetadata, cwd: PathLike):
        self.config = config
        self._cwd = Path(cwd)

    def install(self):
        f = cursefetch.get_project_file(str(self.config.project_id), "latest")
        cursefetch.download_project_file(f, "workspace", uncompress=True)

    def handle_ftbquests(self):
        # using ** to match any 'overrides', as long as it is in the directory.
        for path in tqdm(self._cwd.glob("**/config/ftbquests/quests/chapters/*.snbt"),
                         desc="Converting FTB Quests Chapters"):
            result_json = generate_json_from_ftbquests_chapter(path)
            # write the result to './ftbquests/{previous_chapter_filename}.json'
            output_dir = self._cwd / "ftbquests"
            if not output_dir.exists():
                output_dir.mkdir(exist_ok=True)
            with open(self._cwd / "ftbquests" / path.name.replace(".snbt", ".json"), "w+", encoding="utf-8") as output:
                output.write(result_json)

    def sync_to_paratranz(self, client: Paratranz):
        # load existing
        existing = client.get_file_list(self.config.paratranz_id)

        def upload_or_update_files(path_generator: Generator[Path, None, None], desc: str):
            for json_path in (bar := tqdm(path_generator, desc=desc)):
                try:
                    bar.set_postfix_str(str(json_path))
                    normalized_path = json_path.relative_to(self._cwd)
                    if normalized_path.as_posix() in existing:  # as_posix() makes '\\' to '/' when on Windows
                        data = existing[normalized_path.as_posix()]
                        file_id = data["id"]
                        client.update_file(self.config.paratranz_id, file_id, json_path)
                    else:
                        client.put_file(self.config.paratranz_id, json_path, normalized_path)
                except Exception as e:
                    print(f"Failed to upload {json_path}")
                    traceback.print_exception(e)

        # sync FTBQ first
        if self.config.ftbquests:
            self.handle_ftbquests()
            ftbquests_dir = self._cwd / "ftbquests"
            if ftbquests_dir.exists():
                upload_or_update_files(ftbquests_dir.glob("*.*"), "Sync-ing FTB Quests")
        else:
            print("Skipped FTB Quests")
        # sync JSON
        for tracked_json_path in self.config.tracked_json_paths:
            upload_or_update_files(self._cwd.glob(tracked_json_path), "Synch-ing tracked JSONs")
        # handle LANG
        for tracked_lang_path in self.config.tracked_lang_paths:
            def lang_json_transform(g: Generator[Path, None, None]) -> Generator[Path, None, None]:
                for lang_path in g:
                    try:
                        result = generate_json_from_lang(lang_path)
                        new_path = Path(lang_path.as_posix().replace(".lang", ".json"))
                        with open(new_path, "w+", encoding="utf-8") as json_file:
                            json_file.write(result)
                        yield new_path
                    except Exception as e:
                        print(f"Failed to convert LANG file to JSON format {lang_path}")
                        traceback.print_exception(e)

            upload_or_update_files(lang_json_transform(self._cwd.glob(tracked_lang_path)), "Sync-ing tracked LANGs")
        pass  # other kind of shits

@dataclass
class FTBQuestKeyGeneratingConfig:
    quest_title: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.title"
    quest_subtitle: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.subtitle"
    quest_description: str = "ftbquests.chapter.{chapter_id}.quests.{quest_id}.description.{description_index}"
    generate_description_index_for_empty_lines: bool = False

    @staticmethod
    def get_default() -> "FTBQuestKeyGeneratingConfig":
        return FTBQuestKeyGeneratingConfig()

    def get_title_key(self, chapter_id: str, quest_id: str):
        return (self.quest_title
                .replace("{chapter_id}", chapter_id)
                .replace("{quest_id}", quest_id))

    def get_subtitle_key(self, chapter_id: str, quest_id: str):
        return (self.quest_subtitle
                .replace("{chapter_id}", chapter_id)
                .replace("{quest_id}", quest_id))

    def get_description_key(self, chapter_id: str, quest_id: str, description_index: int):
        return (self.quest_description
                .replace("{chapter_id}", chapter_id)
                .replace("{quest_id}", quest_id)
                .replace("{description_index}", str(description_index)))


def generate_json_from_ftbquests_chapter(snbt_path: PathLike,
                                         config: FTBQuestKeyGeneratingConfig = FTBQuestKeyGeneratingConfig.get_default()) -> str:
    """
    Generate key-value-paired JSON file from FTB Quests Chapter files.
    :param snbt_path: the SNBT file path
    :param config: the configuration
    :return: the result JSON file content
    """
    with open(snbt_path, encoding="utf-8") as f:
        data = ftb_snbt_lib.load(f)
        chapter_id = str(data["id"])
        quests: List = data["quests"]
        if not isinstance(quests, List):
            raise ValueError("'quests' in the snbt is not a list")

        result = dict()
        for quest in quests:
            quest: Compound
            quest_id = str(quest["id"])

            if "title" in quest:
                title = quest["title"]
                result[config.get_title_key(chapter_id, quest_id)] = str(title)
            if "subtitle" in quest:
                subtitle = quest["subtitle"]
                result[config.get_subtitle_key(chapter_id, quest_id)] = str(subtitle)
            if "description" in quest:
                description: List = quest["description"]
                count = 0
                for index, desc in enumerate(description):
                    if config.generate_description_index_for_empty_lines:
                        result[config.get_description_key(chapter_id, quest_id, index)] = str(desc)
                    else:
                        result[config.get_description_key(chapter_id, quest_id, count)] = str(desc)
                        count += 1

        return json.dumps(result, indent=4)


def generate_json_from_lang(lang_path: PathLike) -> str:
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
