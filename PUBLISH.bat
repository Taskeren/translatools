@echo off
REM call uv-publish to publish the packages
REM https://github.com/bulletmark/uv-publish
uv build
uvx uv-publish
