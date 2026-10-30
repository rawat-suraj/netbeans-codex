# NetBeans Codex

An unofficial NetBeans IDE plugin that brings OpenAI Codex into your editor.
Review and discuss your code without switching away from your project.

**Current version:** 1 · **Platform:** Windows x64 · **Tested IDE:** Apache NetBeans 31

## Features

- **ChatGPT sign-in:** sign in through your browser; a queued message sends once authentication finishes.
- **Project-aware chat:** use the detected NetBeans project or select a project folder.
- **Active editor context:** include the current file, cursor line, and unsaved editor contents automatically.
- **File attachments:** attach saved source files through the composer or the **Add to Codex** context action.
- **Streaming replies:** see responses as they arrive, with commentary and final messages separated.
- **Visible activity:** animated status and elapsed time appear below the latest reply while Codex is working.
- **IDE integration:** a dockable panel, light/dark styling, and text wrapping for narrow sidebars.

This version runs in **read-only chat mode**. It can inspect project files but
does not modify them.

## Requirements

- Apache NetBeans 31
- Windows x64 (the Codex runtime is bundled)
- A ChatGPT account with Codex access and an internet connection
- A JDK supported by your NetBeans installation to build from source
- Git and [Git LFS](https://git-lfs.com/) to clone the bundled runtime

## Get the code and build

Install Git LFS before cloning. The bundled Windows runtime is an LFS object;
a source checkout containing only its small pointer file cannot produce a
working plugin.

```powershell
git lfs install
git clone https://github.com/rawat-suraj/netbeans-codex.git
cd netbeans-codex
git lfs pull
```

Set `JAVA_HOME` to your JDK directory. For example, if you use the JDK bundled
with NetBeans at its default installation location:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Apache NetBeans\jdk'
.\mvnw.cmd package
```

Adjust the path if NetBeans or your JDK is installed elsewhere. The Maven wrapper
downloads Maven and the project dependencies on the first build.

The installable plugin is written to **`target/NetBeansCodex-1.nbm`**.

## Install in your regular IDE

1. Open your regular NetBeans IDE, then **Tools → Plugins → Downloaded**.
2. Click **Add Plugins** and select the built `target/NetBeansCodex-1.nbm`.
3. Complete the installation and restart NetBeans when prompted.
4. Open **Window → Codex**, type a message, and click Send.
5. Complete ChatGPT sign-in if requested. The message sends automatically after sign-in.

Once installed, the plugin stays in your regular NetBeans profile. You do not
need to open this project or run a development/test IDE to use it.

Running Maven `install` installs artifacts into the local Maven repository;
it does not install the plugin into your regular IDE.

## Using the chat

1. Focus the source file you want to discuss.
2. Open the Codex panel and check the **Active file** label above the composer.
3. Type a request such as `Review the code in this file for bugs`.
4. Press **Enter** or click the arrow to send. Use **Shift + Enter** for a new line.
5. Use the **New chat** button to start a separate conversation.

### Editor and project context

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

### Attaching files

Use **Add to Codex** from a supported file's context menu, or the composer's
attachment button, to include saved UTF-8 source files.

- Explicit attachments must be saved, valid UTF-8 text files inside the chat's project.
- Each explicit attachment is limited to **64 KB**, with a combined limit of **256 KB**.
- Select an attachment and click **×** to remove it.
- The context action is registered for Java, PHP, and plain-text files.

## Checks

Compile the module with Maven:

```powershell
.\mvnw.cmd test
```

Run the standalone editor-context and progress regression checks:

```powershell
.\scripts\check-context.ps1 -JavaHome $env:JAVA_HOME
```

The script compiles the project and then runs simulated editor and app-server
scenarios without contacting the Codex service. It covers unsaved buffers,
project boundaries, message separation, activity, and scrolling. These standalone
checks are not automatically executed by `mvn test` alone.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| `JAVA_HOME` is invalid | Point it to a supported JDK directory, not its `bin` folder. |
| Runtime archive is missing or invalid | Run `git lfs install` and `git lfs pull`, then rebuild. |
| Send starts sign-in | Finish the browser authorization; the queued message sends automatically. |
| Codex asks which file to review | Focus the intended editor and verify the **Active file** label before sending. |
| Active file is outside the chat project | Start a new chat for that project. |
| The old interface is still visible after updating | Fully close and reopen NetBeans, then check the version in the Codex header. |

## Current limitations

- The bundled runtime supports Windows x64 only.
- This package uses NetBeans APIs and cannot be installed in Android Studio or IntelliJ IDEA.
- File editing, approval dialogs, a skill picker, and chat-history management are not implemented.
- Skills that require unavailable tools or write access cannot complete in the current chat mode.

## License and third-party components

The plugin's source is licensed under [Apache License 2.0](LICENSE).
The bundled Codex runtime and dependencies retain their own notices and terms;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

This is a community project and is not an official OpenAI or Apache NetBeans plugin.
