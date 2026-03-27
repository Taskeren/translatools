import traceback
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import cursefetch
from dotenv import load_dotenv
from tqdm import tqdm

from translatools import TranslatoolsMetadata, Paratranz
from translatools.config import update_deprecated_metadata


class Translatools:
    config: TranslatoolsMetadata
    _conf_path: Path

    def __init__(self, config: TranslatoolsMetadata, conf_path: Path):
        self.config = config
        self._conf_path = conf_path
        # load the dotenv in the ctor
        self._load_dotenv()

    def __str__(self):
        return "Translatools(" + str(self.config) + ", conf_path=" + str(self._conf_path) + ")"

    def cwd(self):
        return self._conf_path.parent

    def save_config(self):
        TranslatoolsMetadata.write_to_path(self._conf_path, self.config)

    def _load_dotenv(self):
        dotenv_name = self.config.dotenv_name
        if dotenv_name is None:
            dotenv_name = ".env"
        dotenv_path = self.cwd() / dotenv_name
        if dotenv_path.exists():
            print(f"Loading dotenv {dotenv_path}")
            load_dotenv(dotenv_path)

    def install(self):
        f = cursefetch.get_project_file(str(self.config.project_id), "latest")
        cursefetch.download_project_file(f, "workspace", uncompress=True)

    def sync_to_paratranz(self, client: Paratranz):
        # load existing
        existing = client.get_file_list(self.config.paratranz_id)

        def upload_or_update_files(paths_: Iterable[Path], desc: str):
            for json_path in (bar := tqdm(paths_, desc=desc)):
                try:
                    bar.set_postfix_str(str(json_path))
                    normalized_path = json_path.relative_to(self.cwd())
                    if normalized_path.as_posix() in existing:  # as_posix() makes '\\' to '/' when on Windows
                        data = existing[normalized_path.as_posix()]
                        file_id = data["id"]
                        client.update_file(self.config.paratranz_id, file_id, json_path)
                    else:
                        client.put_file(self.config.paratranz_id, json_path, normalized_path)
                except Exception as e:
                    print(f"Failed to upload {json_path}")
                    traceback.print_exception(e)

        # Migrate!
        update_deprecated_metadata(self.config)

        # upload or update the files from tracked files
        for tracked_file in self.config.tracked_files:
            paths = tracked_file.get_transformed_json_paths(self.cwd())
            upload_or_update_files(paths, f"Sync-ing {tracked_file.path} as {tracked_file.type}")


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
