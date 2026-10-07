#!/usr/bin/env python3
"""Prints every Stonecutter target as a JSON list, for the release workflow's matrices.

The targets are the tables of stonecutter.properties.toml - one per node, named like
"1.21.11" or "26.1.x-forge" - so a version added there is built and published without
the workflow being touched. Each entry carries what publishing needs: the loader, the
game releases the jar is marked compatible with, and the Modrinth release type.
"""
import json
import sys
import tomllib

LOADERS = ("neoforge", "quilt", "forge")

with open(sys.argv[1] if len(sys.argv) > 1 else "stonecutter.properties.toml", "rb") as f:
    config = tomllib.load(f)

targets = []
for node, table in config.items():
    if not node[:1].isdigit():
        continue
    loader = next((name for name in LOADERS if node.endswith("-" + name)), "fabric")
    mod = table.get("mod", {})
    targets.append({
        "node": node,
        "loader": loader,
        "releases": "\n".join(mod["mc_releases"]),
        # Beta unless a table says otherwise: the mod is below 1.0.0.
        "type": mod.get("release_type", "beta"),
    })

print(json.dumps(targets))
