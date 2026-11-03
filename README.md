# CRH-和谐
CRH-Hexie (Hexie meaning Harmony,a slogan of China Railway from 2008) is a 
Chinese-style railway module based on the art resources from [kuayue 0.3.9n](https://gitee.com/WillowKoishi/KuaYue/). It incorporates the basic gameplay mechanics and expands upon them.
This mod requires Create 6.0.10 or higher and is compatible with the rail-system of Create:Aeronautice's train-system(Create:Simurail).

## License

- **Code, build scripts and documentation:** [MIT](LICENSE.txt), unless a separate notice states otherwise.
- **Artwork in `art_assets/`:** [GNU GPL version 3 only (`GPL-3.0-only`)](art_assets/LICENSE.txt), including artwork in its subdirectories and asset archives. See the [art asset licensing scope](art_assets/README.md).
- **Third-party components:** retain their original licenses and copyright notices; see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and the notices accompanying those components.

The GPLv3 license for the artwork does not change the MIT license of the project's code.

Release JARs include the project and third-party notices under `META-INF/`,
the upstream KuaYue MIT text under `META-INF/licenses/kuayue/`, full font
licenses under `META-INF/licenses/sign-fonts/`, and the artwork license and
scope under `META-INF/licenses/hexie-art/`.

### Release packaging

Run `./gradlew assemble` to build the mod, code sources JAR and matching
`-art-sources.zip` in `build/libs/`. Publish the matching source artifacts
alongside the mod download. The artwork archive contains the current
`art_assets/` tree, including editable models, and its license notices.
Before publishing, check that any distributed GPL-covered artwork has its
corresponding editable sources in that archive; the archive alone does not
establish a source-to-export mapping or ownership of third-party assets.
