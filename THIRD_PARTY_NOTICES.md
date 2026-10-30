# Third-party components

The repository's Apache License 2.0 applies to the NetBeans plugin's own code.
Bundled components retain their respective copyrights and license terms.

## OpenAI Codex runtime

The Windows x64 archive in `src/main/resources/codex-runtime/` contains OpenAI
Codex version **0.160.0**, together with its packaged resources and helper programs.

- Source: https://github.com/openai/codex
- Upstream license: https://github.com/openai/codex/blob/main/LICENSE
- Codex copyright: OpenAI
- Codex source license: Apache License 2.0

The archive also preserves the native voice runtime's separate notices, licenses,
source references, and runtime metadata under `codex-resources/voice/`, including
`NOTICE.md`, `licenses/`, `sources.json`, and `windows-crt.json`. Those components
include separately licensed GStreamer/GLib libraries and the Microsoft Visual C++
runtime. The plugin's license does not replace their terms. Keep these files
with the bundled runtime when redistributing it.

## Maven dependencies

- Apache NetBeans APIs: Apache License 2.0; https://netbeans.apache.org/
- Google Gson 2.14.0: Apache License 2.0; https://github.com/google/gson
- Error Prone annotations 2.48.0 (transitive): Apache License 2.0;
  https://github.com/google/error-prone

The Maven build packages dependency JARs with their upstream metadata. Preserve
their license and notice files when distributing the installable plugin.
