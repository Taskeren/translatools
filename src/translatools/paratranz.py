import io
import json
import traceback
from pathlib import Path
from typing import Any, Optional

import httpx
import tqdm.asyncio

BASE_URL = "https://paratranz.cn/api"


class Paratranz:
    token: str
    _client: Optional[httpx.AsyncClient]

    def __init__(self, token: str):
        self.token = token
        self._client = None
        if token is None or len(token) == 0:
            raise ValueError("A Paratranz token must be provided")

    async def __aenter__(self):
        if self._client is None:
            self._client = httpx.AsyncClient(headers={"Authorization": f"Bearer {self.token}"})
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self._client is not None:
            await self._client.aclose()
            self._client = None  # allow reuse of the Paratranz instance

    async def get_file_list(self, paratranz_project_id: int) -> dict[str, Any]:
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files"
        resp = await self._client.get(url)
        resp.raise_for_status()
        data = resp.json()
        if not isinstance(data, list):
            raise ValueError(f"Malformed response, expected a list payload: {data}")
        data: list
        return {v["name"]: v for v in data}

    async def put_file(self, paratranz_project_id: int, path: Path, relative_path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files"
        with open(path, mode="rb") as f:
            resp = await self._client.post(
                url, files={"file": f}, data={"path": relative_path.parent.as_posix(), "filename": relative_path.name})
            resp.raise_for_status()

    async def put_file_text(self, paratranz_project_id: int, json_text: str, relative_path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files"
        resp = await self._client.post(
            url, files={"file": (relative_path.name, io.BytesIO(json_text.encode("utf-8")), "application/json")},
            data={"path": relative_path.parent.as_posix()}
        )
        resp.raise_for_status()

    async def update_file(self, paratranz_project_id: int, file_id: str, path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}"
        with open(path, mode="rb") as f:
            resp = await self._client.post(url, files={"file": f})
            resp.raise_for_status()

    async def update_file_text(self, paratranz_project_id: int, file_id: str, json_text: str):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}"
        resp = await self._client.post(url, files={
            "file": (".json", io.BytesIO(json_text.encode("utf-8")), "application/json")})
        resp.raise_for_status()

    async def get_translated_file(self, paratranz_project_id: int, file_id: int) -> str:
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}/translation"
        resp = await self._client.get(url)
        resp.raise_for_status()
        return resp.text


async def download_translated_content(client: Paratranz, paratranz_project_id: int, mode: int = 0) -> dict[
    Path, dict[str, str]]:
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

    result: dict[Path, dict[str, str]] = {}

    list_ = await client.get_file_list(paratranz_project_id)
    async for name, file in (bar := tqdm.asyncio.tqdm(list_.items(), desc="Downloading translations")):
        bar.set_description(name)

        current: dict[str, str] = {}

        json_str = None
        try:
            file_id = file["id"]
            json_str = await client.get_translated_file(paratranz_project_id, file_id)
        except Exception as e:
            print(f"Failed to download translated content of {name}")
            traceback.print_exception(e)
        try:
            json_: list = json.loads(json_str)
            for entry in json_:
                if should_dump(entry):
                    current[entry["key"]] = select_value(entry)
                else:
                    continue
            # put the data to the result if not empty
            if len(current) > 0:
                result[Path(file["name"])] = current
        except Exception as e:
            print(f"Failed to parse translated content of {name}")
            traceback.print_exception(e)

    return result
