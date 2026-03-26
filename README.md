# TranslaTools

A toolset of translating Minecraft modpacks.

_Currently working in progress._

## Usage

### Start a new translation project

#### a) Download CurseForge Modpack

`translatools init {curseforge_project_id}`

Using the command to initialize the project in the current directory. The directory must be empty.

You need to add `CF_API_KEY` to the environment variable, or passing it as an argument `--api-key {the_key}`.

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
  // A list of paths to tracked JSONs
  // And glob is supported, where you can use '*' and '**' for matching multiple files,
  // See glob: https://en.wikipedia.org/wiki/Glob_(programming)
  "tracked_json_paths": [
    "overrides/foo/bar/*.json"
  ],
  "tracked_lang_paths": [
    "overrides/resources/**/lang/en_us.lang"
  ],
  // true to enable ftb quests support
  "ftbquests": false
}
```

Fill in these fields, and **remove the comments**, because they're not supported in standard JSON.

### Upload to Paratranz

`translatools sync2paratranz`

Like initialization, you need to add `PARATRANZ_API_KEY` to the environment variable, or passing it as an argument
`--api-key {the_key}`
