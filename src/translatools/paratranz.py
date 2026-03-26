from pathlib import Path
from typing import Any

import requests

BASE_URL = "https://paratranz.cn/api"


class Paratranz:

    def __init__(self, token: str):
        self.token = token
        if len(token) == 0:
            raise ValueError("A Paratranz token must be provided")

    def get_file_list(self, paratranz_project_id: int) -> dict[str, Any]:
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files"
        resp = requests.get(url, headers={"Authorization": f"Bearer {self.token}"})
        resp.raise_for_status()
        data = resp.json()
        if not isinstance(data, list):
            raise ValueError(f"Malformed response, expected a list payload: {data}")
        data: list
        return {v["name"]: v for v in data}

    def put_file(self, paratranz_project_id: int, path: Path, relative_path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files"
        resp = requests.post(url, headers={"Authorization": f"Bearer {self.token}"}, files={"file": open(path, "r")},
                             data={"path": relative_path.parent.as_posix(), "filename": relative_path.name})
        resp.raise_for_status()

    def update_file(self, paratranz_project_id: int, file_id: str, path: Path):
        url = f"{BASE_URL}/projects/{paratranz_project_id}/files/{file_id}"
        resp = requests.post(url, headers={"Authorization": f"Bearer {self.token}"}, files={"file": open(path, "r")})
        resp.raise_for_status()
