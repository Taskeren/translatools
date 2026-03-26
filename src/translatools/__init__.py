import argparse
import json
import os.path
import sys
from dataclasses import asdict
from pathlib import Path

import cursefetch
from dacite import from_dict

from translatools.paratranz import Paratranz
from translatools.translatools import TranslatoolsMetadata


def main() -> None:
    print("Hello from translatools!")

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

    args = parser.parse_args()

    match args.command:
        case "init":
            _command_init(args)
        case "sync2paratranz":
            _command_sync_to_paratranz(args)
        case _:
            parser.print_help()


def _command_init(args):
    config_path = Path(args.config)
    cwd = config_path.parent

    # check if the directory is clear
    if not args.allow_non_empty_directory and len(list(cwd.iterdir())) > 0:
        sys.exit("Expected a empty directory.")

    # download the modpack
    if args.api_key is not None:
        os.environ["CF_API_KEY"] = args.api_key
    f = cursefetch.get_project_file(str(args.project_id), args.version, args.release_type)
    cursefetch.download_project_file(f, ".", uncompress=True)

    # write the config
    config = TranslatoolsMetadata(
        project_id=int(args.project_id),
        paratranz_id=0,
        current_version_id=f.id,
    )
    with open(config_path, "w+") as output:
        json.dump(asdict(config), output, ensure_ascii=False, indent=4)
    print(f"Initialized project at {cwd}.")
    print("Modify the paratranz_id to start working with Paratranz.")


def _command_sync_to_paratranz(args):
    config_path = Path(args.config)
    cwd = config_path.parent

    if not config_path.exists():
        sys.exit(f"Configuration file is missing, expected {config_path}")
    config = from_dict(data_class=TranslatoolsMetadata, data=json.load(open(args.config)))
    config.set_cwd(cwd)

    para = Paratranz(os.environ.get("PARATRANZ_API_KEY", args.api_key))

    config.sync_to_paratranz(para)
