import json
from pathlib import Path
from typing import Protocol, Iterable, Tuple, Any, IO

import ftb_snbt_lib

from translatools.ftbquests import FTBQuestKeyGeneratingConfig, FTBQuestsChapter


class TranslationHandler(Protocol):
    def get_paths(self, mcwd: Path, extra: dict) -> Iterable[Path]:
        """
        The paths to the managed files.
        """
        ...

    def extract(self, mcwd: Path, extra: dict) -> Iterable[Tuple[Path, dict[str, str]]]:
        """
        Extract the key-value pairs.

        The return list contains the target path of the translated file and the content to be translated in JSON format.
        The path should be relative to the given Minecraft working directory (mcwd).
        """
        ...

    def assemble(self, mcwd: Path, translated: Iterable[Tuple[Path, dict[str, str]]], extra: dict):
        """
        Assemble the key-value pairs.

        The given list contains the target path of the translated file and the translated content in JSON format.
        The path should be relative to the given Minecraft working directory (mcwd).
        """
        ...


def _write_open(path, mode: str = "w+", encoding: str = "utf-8") -> IO[Any]:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    return open(p, mode, encoding=encoding)


class KubeJSAssetsHandler(TranslationHandler):
    def get_paths(self, mcwd: Path, extra: dict) -> Iterable[Path]:
        source_lang = extra.get("source_lang", "en_us")
        return mcwd.glob(f"kubejs/assets/*/lang/{source_lang}.json")

    def extract(self, mcwd: Path, extra: dict) -> Iterable[Tuple[Path, dict[str, str]]]:
        for json_path in self.get_paths(mcwd, extra):
            with open(json_path, encoding="utf-8") as f:
                source_lang = extra.get("source_lang", "en_us")
                target_lang = extra.get("target_lang", "zh_cn")
                target_json_path = json_path.parent / json_path.name.replace(source_lang, target_lang)
                yield target_json_path, json.load(f)

    def assemble(self, mcwd: Path, translated: Iterable[Tuple[Path, dict[str, str]]], extra: dict):
        for (target_path, translated_dict) in translated:
            with _write_open(mcwd / target_path, encoding="utf-8") as f:
                json.dump(translated_dict, f)


class FTBQuestsForciblyTranslated(TranslationHandler):
    @staticmethod
    def _generate_json(snbt_path: Path, config: FTBQuestKeyGeneratingConfig) -> dict[str, str]:
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
        return result

    def get_paths(self, mcwd: Path, extra: dict) -> Iterable[Path]:
        return mcwd.glob("config/ftbquests/quests/chapters/*.snbt")

    def extract(self, mcwd: Path, extra: dict) -> Iterable[Tuple[Path, dict[str, str]]]:
        # assemble to the kubejs directory to be loaded
        target_lang = extra.get("target_lang", "zh_cn")
        target_path = (mcwd / "kubejs" / "assets" / "ftbquests_forcibly_translated"
                       / "lang" / f"{target_lang}.json")
        # iterate all translation keys from the chapters and assemble them into 1 single JSON
        gathered = dict()
        for snbt_path in self.get_paths(mcwd, extra):
            gathered.update(self._generate_json(snbt_path, FTBQuestKeyGeneratingConfig.get_default()))
        yield target_path, gathered

    def assemble(self, mcwd: Path, translated: Iterable[Tuple[Path, dict[str, str]]], extra: dict):
        for (target_path, translated_dict) in translated:
            with _write_open(mcwd / target_path, "w", encoding="utf-8") as f:
                json.dump(translated_dict, f)


class FTBQuestsBuiltinLanguage(TranslationHandler):
    @staticmethod
    def _generate_json(snbt_path: Path) -> dict[str, str]:
        with open(snbt_path, encoding="utf-8") as f:
            data: ftb_snbt_lib.Compound = ftb_snbt_lib.load(f)
            json_: dict = json.loads(json.dumps(data))
            for (key, value) in json_.items():
                # flatten the lists (e.g. descriptions)
                if isinstance(value, list):
                    json_[key] = "\n".join(value)
            return json_

    @staticmethod
    def _generate_snbt(data: dict[str, str]) -> ftb_snbt_lib.Compound:
        r = ftb_snbt_lib.Compound()
        for (key, value) in data.items():
            value = value.split("\n")
            match len(value):
                case 0:
                    r[key] = ftb_snbt_lib.String("")
                    break
                case 1:
                    r[key] = ftb_snbt_lib.String(value[0])
                    break
                case _:
                    r[key] = ftb_snbt_lib.List(value)
                    break
        return r

    def get_paths(self, mcwd: Path, extra: dict) -> Iterable[Path]:
        source_lang = extra.get("source_lang", "en_us")
        return mcwd.glob(f"config/ftbquests/quests/lang/{source_lang}/chapters/*.snbt")

    def extract(self, mcwd: Path, extra: dict) -> Iterable[Tuple[Path, dict[str, str]]]:
        source_lang = extra.get("source_lang", "en_us")
        target_lang = extra.get("target_lang", "zh_cn")
        for snbt_path in self.get_paths(mcwd, extra):
            target_path = Path(snbt_path.as_posix().replace(source_lang, target_lang))
            yield target_path, self._generate_json(snbt_path)

    def assemble(self, mcwd: Path, translated: Iterable[Tuple[Path, dict[str, str]]], extra: dict):
        for (target_path, translated_dict) in translated:
            snbt_data = self._generate_snbt(translated_dict)
            with _write_open(mcwd / target_path, "w", encoding="utf-8") as f:
                ftb_snbt_lib.dump(snbt_data, f)


TRANSLATION_HANDLERS = {
    "kubejs_assets": KubeJSAssetsHandler(),
    "ftbquests_forcibly_translated": FTBQuestsForciblyTranslated(),
    "ftbquests_builtin_lang": FTBQuestsBuiltinLanguage(),
}
