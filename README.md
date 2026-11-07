# CRH-和谐
CRH-Hexie (Hexie meaning Harmony,a slogan of China Railway from 2008) is a 
Chinese-style railway module based on the art resources from [kuayue 0.3.9n](https://gitee.com/WillowKoishi/KuaYue/). It incorporates the basic gameplay mechanics and expands upon them.
This mod requires Create 6.0.10 or higher and is compatible with the rail-system of Create:Aeronautice's train-system(Create:Simurail).

## License

- **Code, build scripts and documentation:** [MIT](LICENSE.txt), unless a separate notice states otherwise.
- **Artwork in `art_assets/`:** [GNU GPL version 3 only (`GPL-3.0-only`)](art_assets/LICENSE.txt), including artwork in its subdirectories and asset archives. See the [art asset licensing scope](art_assets/README.md).
- **Third-party components:** retain their original licenses and copyright notices; see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and the notices accompanying those components.

The GPLv3 license for the artwork does not change the MIT license of the project's code.

The main JAR includes project, KuaYue and artwork notices under `META-INF/`.
Font notices and full licenses are included in the separate **HeXie Fonts** JAR.

### Release packaging

Run `./gradlew build` (Windows: `gradlew.bat build`). Both `build` and
`assemble` place exactly the current build's two installable mod JARs in
`build/release/` (older files in this release directory are removed):

- `Hexie-<minecraft>-NeoForge-<version>-<date>.jar`: code and all non-font game artwork.
- `Hexie-Fonts-<minecraft>-NeoForge-<version>-<date>.jar`: font binaries,
  sign font profiles, copyright notices and the original TeX Gyre distribution.

Install both JARs in the client's `mods/` directory. The font mod loads its
resources automatically; it is not a ZIP to put in `resourcepacks/`. Dedicated
servers only need the main mod. Existing `hexie:sign_fonts/...` resource IDs
are preserved, including resource-pack overrides. Development runs continue
to use the same font resources directly from the source tree.

The code sources JAR and matching `-art-sources.zip` are generated in
`build/sources/`. They are additional source artifacts, not installable mods.
Publish them alongside the mod download. CI uploads the two installable JARs
together and the corresponding sources as a separate artifact.
The artwork archive contains the current `art_assets/` tree and its notices.
Before publishing, verify that GPL-covered exports have their corresponding
editable sources in that archive.
