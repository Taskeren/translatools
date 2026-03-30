import argparse
import asyncio
import json
import os.path
import sys
import traceback
from dataclasses import asdict
from pathlib import Path
from typing import overload, Literal

import cursefetch

from translatools.config import TranslatoolsMetadata, TrackedItem
from translatools.handler import TRANSLATION_HANDLERS
from translatools.paratranz import Paratranz
from translatools.translatools import Translatools


def main() -> None:
    print("Hello from translatools!")

    # load env from ~/.translatools.rc
    try:
        user_env = Path.home() / ".translatools.rc"
        if user_env.exists():
            from dotenv import load_dotenv
            load_dotenv(user_env)
    except Exception as e:
        traceback.print_exception(e)

    parser = argparse.ArgumentParser()
    parser.add_argument("--config", help="The configuration of the instance.", default="config.json")

    subparser = parser.add_subparsers(dest="command")

    # init
    init = subparser.add_parser("init", help="Initialize a new translation project from CurseForge packs.")
    init.add_argument("project_id", help="The CurseForge project ID.")
    init.add_argument("--version", help="The file id or name of the version to download.", default="latest")
    init.add_argument("-t", "--release-type",
                      help="The release type to filter by. (default: none). (only applicable when version is 'latest')",
                      choices=["release", "beta", "alpha"], default=None)
    init.add_argument("--api-key",
                      help="The CurseForge API key to use (can also be set via CF_API_KEY environment variable).")
    init.add_argument("--allow-non-empty-directory", action="store_true")

    # upload
    upload = subparser.add_parser("upload", help="Upload the translation entries to Paratranz.")
    upload.add_argument("--api-key",
                                help="The Paratranz API key to use (can also be set via PARATRANZ_API_KEY environment variable.)")
    upload.add_argument("--dry-run",
                                help="Dump the files that would be uploaded or updated to the directory locally.",
                                action="store_true")

    # download
    download = subparser.add_parser("download", help="Download the result of translated entries from Paratranz.")
    download.add_argument("--api-key",
                          help="The Paratranz API key to use (can also be set via PARATRANZ_API_KEY environment variable.)")
    download.add_argument("-o", "--output", help="The output path.", default=".generated")
    download.add_argument("--mode",
                          help="The selector of which entries should be dumped. 0 - Approved, 1 - Any translated, 2 - All.",
                          default="0")

    # tracked
    tracked = subparser.add_parser("tracked", help="Manage the tracked items in the workspace.")
    tracked_subparser = tracked.add_subparsers(dest="tracked_command")
    # tracked - add
    tracked_add = tracked_subparser.add_parser("add")
    tracked_add.add_argument("type", help="The type of the entry")
    tracked_add.add_argument("name", help="The name of the entry", default=None)

    args = parser.parse_args()

    match args.command:
        case "init":
            asyncio.run(_command_init(args))
        case "upload":
            asyncio.run(_command_sync_to_paratranz(args))
        case "download":
            asyncio.run(_command_generate(args))
        case "tracked":
            asyncio.run(_command_tracked(args))
        case _:
            parser.print_help()


@overload
def _get_translatools_from_args(args, exit_on_invalid_path: Literal[True]) -> Translatools: ...


@overload
def _get_translatools_from_args(args, exit_on_invalid_path: Literal[False]) -> Translatools | None: ...


def _get_translatools_from_args(args, exit_on_invalid_path: bool) -> Translatools | None:
    conf_path = Path(args.config)
    if not conf_path.exists():
        if exit_on_invalid_path:
            sys.exit(f"Configuration file is missing, expected {conf_path}")
        else:
            return None

    return Translatools(TranslatoolsMetadata.load_from_path(conf_path), conf_path)


async def _command_init(args):
    config_path = Path(args.config)
    cwd = config_path.parent

    # check if the directory is clear
    if not args.allow_non_empty_directory and len(list(cwd.iterdir())) > 0:
        sys.exit("Expected a empty directory.")

    # download the modpack
    if args.api_key is not None:
        os.environ["CF_API_KEY"] = args.api_key
    f = cursefetch.get_project_file(str(args.project_id), args.version, args.release_type)
    cursefetch.download_project_file_and_uncompress(f, ".")

    # write the config
    conf = TranslatoolsMetadata(
        project_id=int(args.project_id),
        paratranz_id=0,
        current_version_id=f.id,
    )
    with open(config_path, "w+") as output:
        json.dump(asdict(conf), output, ensure_ascii=False, indent=4)
    print(f"Initialized project at {cwd}.")
    print("Modify the paratranz_id to start working with Paratranz.")


async def _command_sync_to_paratranz(args):
    translatools_ = _get_translatools_from_args(args, True)
    para = Paratranz(os.environ.get("PARATRANZ_API_KEY", args.api_key))
    if args.dry_run:
        await translatools_.dump_translation_json(Path(".dry_run"))
    else:
        await translatools_.sync_to_paratranz_async(para)


async def _command_generate(args):
    translatools_ = _get_translatools_from_args(args, True)
    para = Paratranz(os.environ.get("PARATRANZ_API_KEY", args.api_key))
    output_path = args.output
    mode = int(args.mode)
    # pack_format = translatools_.config.pack_format
    # pack_desc = translatools_.config.pack_description
    # if pack_desc is None:
    #     pack_desc = "§bTranslatools Generated"
    # TODO: resourcepack mode

    await translatools_.dump_translated(para, Path(output_path), mode)
    print(f"Result dumped to {output_path}")


async def _command_tracked(args):
    translatools_ = _get_translatools_from_args(args, True)
    mcwd = translatools_.mcwd

    match args.tracked_command:
        case "add":
            type_ = args.type
            name = args.name

            if type_ is None:
                sys.exit("Argument type is missing")
            elif type_ not in TRANSLATION_HANDLERS.keys():
                sys.exit(f"Argument type is invalid, expected one of {TRANSLATION_HANDLERS.keys()}")
            if name is None:
                sys.exit("Argument name is missing")

            tracked_item = TrackedItem(type_)
            translatools_.config.tracked_items.append(tracked_item)
            translatools_.save_config()
            print(f"Added tracked: '{tracked_item.get_name()}'")
            tracked_paths = "\n".join(
                f"- {p.absolute().as_posix()}" for p in tracked_item.handler.get_paths(mcwd, tracked_item.extra))
            print(tracked_paths)
        case _:
            if len(translatools_.config.tracked_items) <= 0:
                print("Tracked nothing?! Add something by 'translatools tracked add'")
            else:
                for tracked_item in translatools_.config.tracked_items:
                    print(f"Tracked item: {tracked_item.type} with extra {tracked_item.extra}")
                    h = tracked_item.handler
                    tracked_paths = "\n".join(
                        f"- {p.absolute().as_posix()}" for p in h.get_paths(mcwd, tracked_item.extra))
                    print(tracked_paths)
                    print()
