# Translatools

## Environment Variables

Tokens are passed to Translatools in Environment Variables.

Translatools will read dotenv files, either the current working directory (`.env`) or the target working directory if
provided in `-w <path>` (`--working-dir=<path>`).

Here is the list of used environment variables.

| Variable Name      | Description                                                                  |
|:-------------------|:-----------------------------------------------------------------------------|
| `CURSEFETCH_TOKEN` | Token of CurseForge, used for fetching, and downloading modpacks.            |
| `PARATRANZ_TOKEN`  | Token of Paratranz, used for uploading, updating, and fetching translations. |
