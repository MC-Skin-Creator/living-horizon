---
name: add-contributor
description: Credit a person as a contributor of Living Horizon in every place the project lists them (CREDITS.md, the four mod metadata files, the README). Use when someone's pull request is merged or about to be, when the user says someone contributed or asks to add them to the contributors, and at the start of any pull request to check that its author, human or not, is already credited.
---

# Add a contributor

A contributor is someone whose work was accepted, or is part of the pull request being
prepared. They are credited as a **contributor**, never as an author: `authors` stays
`clixmods` everywhere. The credit stays whatever happens to the license (LICENSE, section 3).

## Check first

Run this check whenever a pull request is opened or prepared, **including by an AI agent
working for someone else**:

1. Find who the work is for: the GitHub login of the pull request's author, or of the
   person the user says the session works for (`git config user.name`, the PR author,
   the user's own words).
2. Search for that login in `CREDITS.md` (all sections: author, collaborators, contributors).
3. Not there, and not `clixmods`: add them with the steps below, **in the same pull request**.
   An agent never skips it because the change is small.
4. Already there: nothing to do.

## Steps

Use the exact GitHub login, with its case. Keep the order of the first contribution.

1. **`CREDITS.md`**, section *Contributors*: one line, replacing `- None yet.` for the first:
   `- [**Login**](https://github.com/Login), a few words on what they brought (#PR).`
   Plain words a player understands, in English.
2. **`src/main/resources/fabric.mod.json`**: add the login to `"contributors": [...]`.
3. **`src/main/resources/quilt.mod.json`**: add `"Login": "Contributor"` to `"contributors"`.
4. **`src/main/resources/META-INF/mods.toml`** and **`neoforge.mods.toml`**: the `credits`
   line is one string, `credits = "Testers: A. Contributors: B, C"`. Append to the
   *Contributors* part, or add it after the testers.
5. **`README.md`**, *More*, the *Credits* line: `Contributors: B, C.` after the collaborators
   and testers. The store description does not carry it.
6. Commit as `docs(credits): add Login to the contributors`. No changelog entry: it is not a
   change a player sees.

A tester or collaborator who then contributes code moves to *Contributors* in `CREDITS.md`
and keeps their other role in the metadata.

## Do not

- put anyone under `authors` or `Owner`;
- write the person's real name, email or anything but their public GitHub login and link;
- credit a bot or an AI agent: credit the human the work is done for.
