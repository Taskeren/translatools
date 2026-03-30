import json
import traceback
from pathlib import Path
from typing import Iterable

import cursefetch
from dotenv import load_dotenv
from tqdm import tqdm
from tqdm.asyncio import tqdm as tqdm_async

from translatools import TranslatoolsMetadata, Paratranz
from translatools.paratranz import download_translated_content

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

    @property
    def cwd(self):
        return self._conf_path.parent

    @property
    def mcwd(self):
        return self.cwd / "overrides"  # FIXME: add a config for this

    def save_config(self):
        TranslatoolsMetadata.write_to_path(self._conf_path, self.config)

    def _load_dotenv(self):
        dotenv_name = self.config.dotenv_name
        if dotenv_name is None:
            dotenv_name = ".env"
        dotenv_path = self.cwd / dotenv_name
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

            # upload or update the files from tracked files
            for tracked_item in self.config.tracked_items:
                handler = tracked_item.handler
                async for (path, data) in (
                        bar := tqdm_async(list(handler.extract(self.mcwd, tracked_item.extra)),
                                          desc=tracked_item.get_name())):
                    try:
                        bar.set_postfix_str(str(path))
                        if path.as_posix() in existing:
                            file_id = existing[path.as_posix()]["id"]
                            await client.update_file_text(self.config.paratranz_id, file_id,
                                                          json.dumps(data, ensure_ascii=False))
                        else:
                            await client.put_file_text(self.config.paratranz_id, json.dumps(data, ensure_ascii=False),
                                                       path)
                    except Exception as e:
                        print(f"Failed to upload {path}")
                        traceback.print_exception(e)

    async def dump_translation_json(self, destination: Path):
        for tracked_item in tqdm(self.config.tracked_items):
            handler = tracked_item.handler
            for (path, data) in handler.extract(self.mcwd, tracked_item.extra):
                output_path = destination / path
                output_path.parent.mkdir(parents=True, exist_ok=True)
                with open(output_path, "w", encoding="utf-8") as output:
                    json.dump(data, output, ensure_ascii=False, indent=4)

    async def dump_translated(self, client: Paratranz, destination: Path, mode: int = 0):
        async with client:
            resp = await download_translated_content(client, self.config.paratranz_id, mode)
            for tracked_item in self.config.tracked_items:
                handler = tracked_item.handler
                # find the paths that managed by the handler
                paths = [path for (path, _) in handler.extract(self.mcwd, tracked_item.extra)]
                # ask handler to update the managed translation files
                data_list = [(path, data) for (path, data) in resp.items() if path in paths]
                handler.assemble(destination, data_list, tracked_item.extra)

    @staticmethod
    def _generate_pack_mcmeta(pack_format: int, pack_description: str) -> str:
        return PACK_MCDATA.replace("{pack_format}", str(pack_format)).replace("{pack_description}", pack_description)
