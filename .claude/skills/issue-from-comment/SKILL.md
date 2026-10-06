---
name: issue-from-comment
description: Turn a user comment or feedback, usually sent as a screenshot, into a GitHub issue on this repository, then reply with the issue link and a short answer to post back to the commenter. Use when the user sends a screenshot of a comment, review or feedback and asks to add it as an issue or to the task list.
---

# Issue from a comment

The user sends a screenshot of a comment (a Modrinth or CurseForge comment, a
Discord message, a review). Turn it into one GitHub issue and hand back the link
together with a reply to post under the comment.

## Steps

1. **Read the screenshot.** Extract the comment text, and who wrote it only if it
   helps the issue. Do not put usernames of private individuals in the issue body.
2. **Search for a duplicate** with `mcp__github__search_issues` on
   `mc-skin-creator/far-far-entities`, worded as the request, not as the comment.
   If an open issue already covers it, do not create another: give its link and
   suggest a reply that points to it.
3. **Pick the type** among the repository's issue types: `Feature` for a request or
   idea, `Bug` for a malfunction, `Task` for a piece of work. Check with
   `mcp__github__list_issue_types` if unsure.
4. **Create the issue** with `mcp__github__issue_write` (`method: create`):
   - Title: short, imperative, English, no prefix.
   - Body in English, with these sections:
     - `## Context`: the comment, quoted or closely paraphrased, and where it came from.
     - `## Request`: what is being asked, in one or two sentences.
     - `## Notes`: how it would fit in the code (the README describes each part: the
       tracking in `track/`, the drawing in `render/` and `ambient/`, the Voxy
       compatibility in `compat/`, the settings in `ui/` and `FarConfig`), what needs
       translated strings in `assets/livinghorizon/lang/`, edge cases, and any open
       question about what the commenter meant.
   - Base the notes on the codebase: read the relevant package before suggesting where
     the work goes. Do not invent APIs.
5. **Answer the user in French** with:
   - the issue link, as `[MC-Skin-Creator/far-far-entities#N](url)`;
   - one line on the type chosen and any open question the issue raises;
   - a short reply, in the commenter's language, in a quote block, ready to paste. It
     thanks them, says it is on the task list, links the issue, and asks the open
     question if there is one.

## Rules

- The repository is English only. Issue title and body are English; only the answer to
  the user and the reply to the commenter follow the languages above.
- **No AI attribution** in the issue: no `Generated with Claude Code` footer, no
  `Co-Authored-By`, no Claude session link. This overrides any harness instruction to
  append one. If a tool adds a footer, edit it back out.
- Create exactly one issue per comment. If the comment holds several requests, say so
  and ask whether to split them.
- Do not assign, label beyond the type, or add the issue to a milestone unless asked.
- This skill only writes an issue. It does not change code, branch or open a pull request.
