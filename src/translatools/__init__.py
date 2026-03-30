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

from translatools.config import TranslatoolsMetadata, TrackedFile, FileType
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

    # sync2paratranz
    sync2paratranz = subparser.add_parser("sync2paratranz")
    sync2paratranz.add_argument("--api-key",
                                help="The Paratranz API key to use (can also be set via PARATRANZ_API_KEY environment variable.)")
    sync2paratranz.add_argument("--dry-run",
                                help="Dump the files that would be uploaded or updated to the directory locally.",
                                action="store_true")

    # generate
    generate = subparser.add_parser("generate")
    generate.add_argument("--api-key",
                          help="The Paratranz API key to use (can also be set via PARATRANZ_API_KEY environment variable.)")
    generate.add_argument("--dump-json", help="Dump the merged JSON only.", action="store_true")
    generate.add_argument("-o", "--output", help="The output path.", default=None)
    generate.add_argument("--mode",
                          help="The selector of which entries should be dumped. 0 - Approved, 1 - Any translated, 2 - All.",
                          default="0")

    # tracked
    tracked = subparser.add_parser("tracked")
    tracked_subparser = tracked.add_subparsers(dest="tracked_command")
    # tracked - add
    tracked_add = tracked_subparser.add_parser("add")
    tracked_add.add_argument("glob", help="The path or the glob to the files")
    tracked_add.add_argument("type", help="The type of tracked files")

    args = parser.parse_args()

    match args.command:
        case "init":
            asyncio.run(_command_init(args))
        case "sync2paratranz":
            asyncio.run(_command_sync_to_paratranz(args))
        case "generate":
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
    pack_format = translatools_.config.pack_format
    pack_desc = translatools_.config.pack_description
    if pack_desc is None:
        pack_desc = "§bTranslatools Generated"
    if args.dump_json:
        # dump as JSON only
        if output_path is None:
            output_path = ".dump.json"
        output = Path(output_path)
        await translatools_.dump_translated_to(para, output, mode)
        print(f"Dumped JSON to {output} in mode {mode}")
    else:
        if output_path is None:
            output_path = ".dump.zip"
        output = Path(output_path)
        await translatools_.dump_translated_zip(para, output, mode, pack_format=pack_format, pack_description=pack_desc)
        print(f"Dumped resourcepack to {output} in mode {mode}")


async def _command_tracked(args):
    translatools_ = _get_translatools_from_args(args, True)
    cwd = translatools_.cwd()

    match args.tracked_command:
        case "add":
            glob = args.glob
            type_ = args.type

            if glob is None:
                sys.exit("Argument glob is missing")
            if type_ is None:
                sys.exit("Argument type is missing")
            try:
                FileType(type_)
            except ValueError:
                sys.exit("Argument type is invalid")

            tracked_file = TrackedFile(glob, type_)
            translatools_.config.tracked_files.append(tracked_file)
            translatools_.save_config()
            print(f"Added tracked: '{tracked_file.path}' as {tracked_file.type}")
            tracked_paths = "\n".join(f"- {p.absolute().as_posix()}" for p in tracked_file.get_paths(cwd))
            print(tracked_paths)
        case _:
            if len(translatools_.config.tracked_files) <= 0:
                print("Tracked nothing?! Add something by 'translatools tracked add'")
            else:
                for tracked_file in translatools_.config.tracked_files:
                    print(f"Tracked: '{tracked_file.path}' as {tracked_file.type}")
                    # the paths that matches
                    tracked_paths = "\n".join(f"- {p.absolute().as_posix()}" for p in tracked_file.get_paths(cwd))
                    print(tracked_paths)
                    # separator
                    print()
