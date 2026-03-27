# TranslaTools

A toolset of translating Minecraft modpacks.

_Currently working in progress._

## Usage

### Start a new translation project

#### a) Download CurseForge Modpack

`translatools init {curseforge_project_id}`

Using the command to initialize the project in the current directory. The directory must be empty.

You need to add `CF_API_KEY` to the environment variable, or passing it as an argument `--api-key {the_key}`,
or [using dotenv](#using-_dotenv_).

When the downloading is complete, you need to edit the `config.json` file, fill the `paratranz_id`. And there's a flaw
in the current version that `_cwd` is also exported to the config file, you can remove it.

#### b) Add Configuration to existing modpack directory

Create a file, `config.json` preferred.

```json5
{
  // CurseForge project ID
  "project_id": 345678,
  // Paratranz project ID
  "paratranz_id": 67890,
  // The CurseForge file ID, or 0 if you don't know
  // It's used for updating the files from the latest version. Not implemented yet.
  "current_version_id": 1234567,
  // deprecated!
  "tracked_json_paths": [],
  // deprecated!
  "tracked_lang_paths": [],
  // deprecated!
  "ftbquests": false,
  // tracked files with their type.
  "tracked_files": [
    {
      // the path to the file, or the glob to match all the files.
      // See glob: https://en.wikipedia.org/wiki/Glob_(programming)
      "path": "overrides/kubejs/assets/**/lang/en_us.json",
      // the type of the files.
      // it determines how the app treat these files.
      // supported types:
      // - "json_kv": the JSON files where the root is an object, which it only contains string values.
      // - "lang_kv": the regular Minecraft .lang files
      // - "ftbquests_chapter": the SNBT file of Chapter definition of FTB Quests
      "type": "json_kv"
    }
  ]
}
```

Fill in these fields, and **remove the comments**, because they're not supported in standard JSON.

### Upload to Paratranz

`translatools sync2paratranz`

Like initialization, you need to add `PARATRANZ_API_KEY` to the environment variable, or passing it as an argument
`--api-key {the_key}`, or using dotenv.

### Using _dotenv_

It now also supports reading environment variables from dotenv files like `.env`.

You can create a `.env` file in the same directory of the config file, and it will be read automatically.

If you want to use another name, you can set `dotenv_name` in the config.

```dotenv
CF_API_KEY=NEVER_GONNA_GIVE_YOU_UP
PARATRANZ_API_KEY=NEVER_GONNA_LET_YOU_DOWN
```
