# NetBeans Codex

Chat with OpenAI Codex inside NetBeans, with ChatGPT sign-in, project context,
file attachments, and streaming responses.

## Requirements

- Apache NetBeans 31
- Windows x64 (the Codex runtime is bundled)
- A ChatGPT account with Codex access and an internet connection

## Install in your regular IDE

1. Open your regular NetBeans IDE, then **Tools → Plugins → Downloaded**.
2. Click **Add Plugins** and select `NetBeansCodex-1.nbm`.
3. Complete the installation and restart NetBeans when prompted.
4. Open **Window → Codex**, type a message, and click Send.
5. Complete ChatGPT sign-in if requested. The message sends automatically after sign-in.

Once installed, the plugin stays in your regular NetBeans profile. You do not
need to open this project or run a development/test IDE to use it.

The current chat mode can read project files; it does not modify them.
The most recently focused source editor is included automatically, with its path,
cursor line, and a snapshot of the current buffer (including unsaved changes).
The active file is shown above the composer. Buffers larger than 64 KB are
identified by path instead of being embedded. Start a new chat when switching
to a different project.

Activity appears directly below the latest response with animated dots and an
elapsed-time counter. It stays visible throughout each turn, including the periods between an
introductory message, commands/tools, and the final response. It stops when the
server reports that the turn has finished.

Use **Add to Codex** from a supported file's context menu, or the composer's
attachment button, to include saved UTF-8 source files.

## Build

Install [Git LFS](https://git-lfs.com/) before cloning this repository. The bundled
Windows runtime is stored in LFS. For an existing clone, run `git lfs install`
and `git lfs pull` before building.

Set `JAVA_HOME` to a JDK supported by your NetBeans installation, then run:

```powershell
.\mvnw.cmd package
```

The installable plugin is written to `target/NetBeansCodex-1.nbm`.
Maven `install` only installs build artifacts into the local Maven repository;
use the NetBeans Plugins manager to install the plugin into the IDE.

Run `scripts/check-context.ps1` to compile and run the standalone editor-context
and progress regression checks. They simulate editor buffers and app-server
notifications without contacting the Codex service.
