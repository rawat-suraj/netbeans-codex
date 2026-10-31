# NetBeans Codex

An unofficial NetBeans IDE plugin that brings OpenAI Codex into your editor.
Review and discuss your code without switching away from your project.

**Current version:** 1 · **Platform:** Windows x64 · **Tested IDE:** Apache NetBeans 31

## Features

- **ChatGPT sign-in:** sign in through your browser; a queued message sends once authentication finishes.
- **Project-aware chat:** use the detected NetBeans project or select a project folder.
- **Multiple repositories per chat:** include backend and frontend project folders in one conversation.
- **Active editor context:** include the current file, cursor line, and unsaved editor contents automatically.
- **File attachments:** attach saved source files through the composer or the **Add to Codex** context action.
- **Image paste:** paste screenshots into the composer with **Ctrl+V**, or attach PNG/JPEG/GIF/BMP files.
- **Streaming replies:** see responses as they arrive, with commentary and final messages separated.
- **Visible activity:** animated status and elapsed time appear below the latest reply while Codex is working.
- **Shared local history:** browse, search, and resume saved Codex conversations from compatible local clients.
- **IDE integration:** a dockable panel, light/dark styling, and text wrapping for narrow sidebars.

The composer has an access-mode dropdown. **Chat · Read only** inspects code;
**Agent · Edit workspace** can modify files and run sandboxed commands in the
chat's workspace folders. Choose editing mode explicitly after connecting.
New or restored conversations default to read-only. The selected mode applies
to the next turn, including follow-ups in an existing chat.

Editing mode keeps network access restricted and does not support permission
escalation dialogs. Commands that require downloads or access outside the
workspace may still be blocked.

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

Read-only mode can inspect project files. Select **Agent · Edit workspace** to
allow changes in the included workspace folders.
The most recently focused source editor is included automatically, with its path,
cursor line, and a snapshot of the current buffer (including unsaved changes).
The active file is shown above the composer. Buffers larger than 64 KB are
identified by path instead of being embedded.

Use **Folders…** above the composer to include multiple project folders in the
same conversation, such as separate backend and frontend repositories. The first
folder remains the working directory. You can add or remove additional folders
without creating a new chat. When a NetBeans project is nested inside a Git
repository, the repository root is used.

Sending a request with an active editor or explicitly selected attachment from
another project automatically includes that project's root. For files without a
NetBeans project or Git repository, their containing folder is included. Only
selected project folders are listed; other sibling repositories are not added.
The composer shows the included folder names, and their full paths are available
in its tooltip. Extra folders are saved locally per chat and restored when
resuming history through this plugin. The access-mode selector controls whether
Codex may also edit these folders.

Activity appears directly below the latest response with animated dots and an
elapsed-time counter. It stays visible throughout each turn, including the periods between an
introductory message, commands/tools, and the final response. It stops when the
server reports that the turn has finished.

### Attaching files

Use **Add to Codex** from a supported file's context menu, or the composer's
attachment button, to include saved UTF-8 source files.

- Explicit attachments must be saved, valid UTF-8 text files inside one of the chat's workspace folders.
- Each explicit attachment is limited to **64 KB**, with a combined limit of **256 KB**.
- Select an attachment and click **×** to remove it.
- The context action is registered for Java, PHP, and plain-text files.

### Pasting images

Copy a screenshot/image, focus the message composer, and press **Ctrl+V**.
You can also paste a single copied image file or select an image with the
attachment button. Each thumbnail has its own **×** button at the top-right;
click it to remove that image. Normal text copy/paste continues to work.

Images are sent as native visual input rather than embedded source text. You
can send an image without typing a prompt. The limit is four images, 8 MB per
encoded image, 16 MB total, and 25 megapixels per image. Images do not add their
containing directories to the chat's workspace. After a request is accepted,
sent images leave the draft attachment list; they remain part of the conversation.
Image input requires a model that supports images.

### Chat history and resuming across local Codex clients

Click the **History** (clock) button beside **New chat**. Sign in if prompted,
select a saved conversation, and click **Resume chat** or double-click it.
Search saved titles, refresh for changes made in another client, and use
**Load older chats** to browse additional pages. Hover over a chat to see its
project folder and ID.

The plugin uses Codex's shared local session store through `thread/list` and
`thread/resume`. Chats available in the same Codex home (normally `%USERPROFILE%\.codex`,
or the inherited `CODEX_HOME`) can be continued with their original thread ID,
saved messages, and project folder. This includes compatible local desktop,
CLI, VS Code, and plugin sessions. The project folder must still exist locally.
Finish a turn before switching clients, then refresh/reopen the chat to load the
latest saved messages. History is not a live subscription to another client's run.

Resuming resets the plugin to **read-only** mode, even if the conversation used
broader permissions elsewhere. Select editing mode before the next message if
you want to modify files. User and assistant messages are displayed;
private reasoning and raw tool payloads are omitted. Existing unsent draft text
is preserved, and attachments from the previous chat are cleared.

Local history browsing does not add ChatGPT web history, cloud-only chats, or
browser/mobile sync. A chat held by another Codex client cannot be resumed until
that client releases it. The desktop app may retain its lock after switching
conversations; the plugin cannot automatically take ownership from it.

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
project boundaries, message separation, activity, scrolling, history pagination,
and resuming saved chats. These standalone
checks are not automatically executed by `mvn test` alone.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| `JAVA_HOME` is invalid | Point it to a supported JDK directory, not its `bin` folder. |
| Runtime archive is missing or invalid | Run `git lfs install` and `git lfs pull`, then rebuild. |
| Send starts sign-in | Finish the browser authorization; the queued message sends automatically. |
| Codex asks which file to review | Focus the intended editor and verify the **Active file** label before sending. |
| Need backend and frontend context in one chat | Add both roots with **Folders…**, or focus a file in the other project before sending. |
| The old interface is still visible after updating | Fully close and reopen NetBeans, then check the version in the Codex header. |

## Current limitations

- The bundled runtime supports Windows x64 only.
- This package uses NetBeans APIs and cannot be installed in Android Studio or IntelliJ IDEA.
- Approval dialogs, a skill picker, and history deletion/archiving are not implemented.
- Skills requiring unavailable tools or permissions beyond the selected access mode cannot complete.

## License and third-party components

The plugin's source is licensed under [Apache License 2.0](LICENSE).
The bundled Codex runtime and dependencies retain their own notices and terms;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

This is a community project and is not an official OpenAI or Apache NetBeans plugin.
