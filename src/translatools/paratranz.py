from pathlib import Path
from typing import Any, Optional

import httpx

BASE_URL = "https://paratranz.cn/api"


class Paratranz:
    token: str
    _client: Optional[httpx.AsyncClient]

    def __init__(self, token: str, client: Optional[httpx.AsyncClient] = None):
        self.token = token
        self._client = client
        if len(token) == 0:
            raise ValueError("A Paratranz token must be provided")

    async def __aenter__(self):
        if self._client is None:
            self._client = httpx.AsyncClient(headers={"Authorization": f"Bearer {self.token}"})

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self._client is not None:
            await self._client.aclose()

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
        resp = await self._client.post(url, files={"file": open(path, mode="rb")},
                                       data={"path": relative_path.parent.as_posix(), "filename": relative_path.name})
        resp.raise_for_status()

    async def update_file(self, paratranz_project_id: int, file_id: str, path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}"
        resp = await self._client.post(url, files={"file": open(path, mode="rb")})
        resp.raise_for_status()

    async def get_translated_file(self, paratranz_project_id: int, file_id: int) -> str:
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}/translation"
        resp = await self._client.get(url)
        resp.raise_for_status()
        return resp.text
