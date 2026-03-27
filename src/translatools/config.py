from dataclasses import dataclass, field


@dataclass
class TranslatoolsMetadata:
    # the CurseForge project ID
    project_id: int
    # the Paratranz project ID
    paratranz_id: int
    # the CurseForge file ID, or 0 for unknown or uninitialized
    current_version_id: int = 0
    # the tracked flat key-value-paired JSON files, relative to the configuration file, glob supported
    tracked_json_paths: list[str] = field(default_factory=list)
    # the tracked Mojang-flavored LANG files, relative to the configuration file, glob supported
    tracked_lang_paths: list[str] = field(default_factory=list)
    # true to enable support for FTBQuests
    # but if somehow the modpack doesn't contain FTB Quests, or the contents are already localization-friendly,
    # set it to false.
    ftbquests: bool = True
