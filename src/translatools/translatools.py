import json
import traceback
import zipfile
from pathlib import Path
from typing import Iterable

import cursefetch
from dotenv import load_dotenv
from tqdm.asyncio import tqdm as tqdm_async

from translatools import TranslatoolsMetadata, Paratranz

PACK_MCDATA = """
{
  "pack": {
    "pack_format": {pack_format},
    "description": "{pack_description}"
  }
}
"""


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

    async def sync_to_paratranz_async(self, client: Paratranz):
        async with client:
            # load existing
            existing = await client.get_file_list(self.config.paratranz_id)

            async def upload_or_update_files(paths_: Iterable[Path], desc: str):
                async for json_path in (bar := tqdm_async(paths_, desc=desc)):
                    try:
                        bar.set_postfix_str(str(json_path))
                        normalized_path = json_path.relative_to(self.cwd())
                        if normalized_path.as_posix() in existing:  # as_posix() makes '\\' to '/' when on Windows
                            data = existing[normalized_path.as_posix()]
                            file_id = data["id"]
                            await client.update_file(self.config.paratranz_id, file_id, json_path)
                        else:
                            await client.put_file(self.config.paratranz_id, json_path, normalized_path)
                    except Exception as e:
                        print(f"Failed to upload {json_path}")
                        traceback.print_exception(e)

            # upload or update the files from tracked files
            for tracked_file in self.config.tracked_files:
                paths = tracked_file.get_transformed_json_paths(self.cwd())
                await upload_or_update_files(paths, f"Sync-ing {tracked_file.path} as {tracked_file.type}")

    async def _download_and_merge_translated_content_to_dict(self, client: Paratranz, mode: int = 0) -> dict:
        """
                Stage:
                 0 - untranslated
                 1 - translated (not approved)
                 2 - questioned
                 3 - approved
                 5 - approved
                 9 - locked (only admins can unlock, and is considered as translated)
                -1 - hidden (untranslated value is used)

                3-approved only exists when the project enables double check, and it means it is checked for the first time.
                otherwise, only 5-approved is used.

                Mode:
                0 - Approved, dump approved (2nd-time) only and locked
                1 - Any translated, dump translated, questioned, both approved and locked
                2 - All, dump anything
                """

        def should_dump(entry_) -> bool:
            """
            Check if the 'stage' (status) needs dumping.
            See the function document for details of the modes.
            """
            stage = entry_["stage"]
            match mode:
                case 0:
                    return stage in (5, 9)
                case 1:
                    return stage in (1, 2, 3, 5, 9)
                case 2:
                    return stage in range(-1, 10)  # -1 to 9
                case _:
                    raise ValueError(f"Unexpected mode {mode}")

        def select_value(entry_) -> str:
            """
            Select the 'original' or 'translation' value depends on the 'stage' (status).
            """
            match entry_["stage"]:
                case 0 | -1:
                    return entry_["original"]
                case 1 | 2 | 3 | 5 | 9:
                    return entry_["translation"]
                case _:
                    raise ValueError(f"Unexpected stage/status {entry_['stage']}")

        result = dict()

        list_ = await client.get_file_list(self.config.paratranz_id)
        async for name, file in (bar := tqdm_async(list_.items(), desc="Loading translation")):
            bar.set_postfix_str(name)

            json_str = None
            try:
                file_id = file["id"]
                json_str = await client.get_translated_file(self.config.paratranz_id, file_id)
            except Exception as e:
                print(f"Failed to get content of {name}")
                traceback.print_exception(e)
            try:
                # the result is a JSON list that contains many entry data
                json_: list = json.loads(json_str)
                for entry in json_:
                    if should_dump(entry):
                        result[entry["key"]] = select_value(entry)
                    else:
                        # maybe add something skip information here?
                        continue
            except Exception as e:
                print(f"Failed to parse translated content of {name}")
                print(f"Content:\n{json_str}")
                traceback.print_exception(e)

        return result

    async def _make_translated_json(self, client: Paratranz, mode: int = 0) -> str:
        result = await self._download_and_merge_translated_content_to_dict(client, mode)
        return json.dumps(result, ensure_ascii=False, indent=4)

    async def _make_translated_lang(self, client: Paratranz, mode: int = 0) -> str:
        result = await self._download_and_merge_translated_content_to_dict(client, mode)
        return "\n".join(f"{key}={value}" for key, value in result.items())

    async def dump_translated_to(self, client: Paratranz, path: Path, mode: int = 0):
        async with client:
            json_str = await self._make_translated_json(client, mode)
            path.write_text(json_str, encoding="utf-8")

    @staticmethod
    def _generate_pack_mcmeta(pack_format: int, pack_description: str) -> str:
        return PACK_MCDATA.replace("{pack_format}", str(pack_format)).replace("{pack_description}", pack_description)

    async def dump_translated_zip(self, client: Paratranz, zip_path: Path, mode: int = 0, *, pack_format: int = 15,
                                  pack_description: str = "§bTranslatools Generated"):
        async with client:
            with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
                # write the language LANG
                if pack_format <= 3:  # before Minecraft 1.13, the flattening
                    lang_str = await self._make_translated_lang(client, mode)
                    z.writestr("assets/translatools/lang/zh_CN.lang", lang_str)
                # write the language JSON
                else:
                    json_str = await self._make_translated_json(client, mode)
                    z.writestr("assets/translatools/lang/zh_cn.json", json_str)
                # write resourcepack info
                z.writestr("pack.mcdata", Translatools._generate_pack_mcmeta(pack_format, pack_description))
