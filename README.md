# Hello. Hello.

CRH-Hexie (Hexie meaning Harmony,a slogan of China Railway from 2008) is a Chinese-style railway mod,which is a railway decoration mod built with art assets from KuaYue 0.3.9n. Lay your first track. Assemble a complete train. Bring the Chinese railways you know to life in a world of blocks.

Requires Create 6.0.10 or later. Also compatible with tracks from Create: simurail.

![banner](https://cdn.modrinth.com/data/cached_images/1823ec142f3a8b09986fe9296ea3cf69f61f5747_0.webp)

## Fresh thinking. From the first rail to the first departure.

### Tracks. Just the right height.

CRH Hexie offers several track types in 1,435 mm standard gauge, with the railhead raised to 0.75 m. So your trains line up properly with Chinese-standard 1.25 m platforms. A little lift. A much better fit.

Ballast is built right in. Simply right-click with a block in hand to change its material. Less time wrestling with slab alignment and ballast around curves. More time bringing your railway to life.

![railway](https://cdn.modrinth.com/data/cached_images/3a95b7515c0a24fee1b6e981f63f2c592735d67a.jpeg)

Train control. At speed. At ease.

Built for high-speed rail, CRH Hexie introduces MTCS-200 (Minecraft Train Control System – 200 km/h), a train control system based on CTCS (Chinese Train Control System).

It works with Create’s train schedules and lets you switch back to Create’s original driving controls at any time. Two ways to drive. Every journey in your hands. ![Minecraft Train Control System](https://cdn.modrinth.com/data/cached_images/ddbbec0b480b65807b322a0fca073b46c8bbe6c1.gif) Minecraft Train Control System-200

A new train system. Built around each carriage.

A revolutionary carriage management system takes your builds beyond Create’s two-bogie limit. Locomotives with three, four, or even more bogies are now easier to recreate.

Customize the distance between the carriage floor and the rails. Fine-tune bogie bolster height and fore-and-aft positioning. Get the proportions just the way you pictured them.

CRH Hexie also improves how players move with trains. Walk around. Jump freely. Enjoy the freedom to move on board.

CRH Hexie provides a novel method for calculating train speed, which involves comprehensively calculating the maximum running speed of the entire train based on the power of the bogies; accordingly, the power of the bogies for trailers and passenger coach is set to 0.

The coupler system lets you connect carriages and freely rearrange your train. Adapters bring different coupler types together, too. From a single carriage to a complete train, build the formation you have in mind.

The carriage management system makes these features possible:

![mutil-bogies](https://cdn.modrinth.com/data/cached_images/b5ee9e25e9b0d0e632a04123ae8c7f7286660e20_0.webp) mutil-bogies

![Animated yaw dampers](https://cdn.modrinth.com/data/cached_images/92fb03a7379806d5bfef05ea595ce26da5e24e30.gif) Animated yaw dampers

![Flexible train formations](https://cdn.modrinth.com/data/cached_images/f719c4f9f01eff68bbce6243603c078507a47e8f.gif) Flexible train formations

![Adjustable bogies](https://cdn.modrinth.com/data/cached_images/437adc61a65d2aa67e5b7c42c2b3c0ec30c8d458.gif) Adjustable bogies

Multiple bogies Animated bogie traction rods Coupler system Adjustable bogie bolster height and fore-and-aft positioning

CarriageFlow. It all falls into place.

Finding the right place for carriage plates. Getting the layout right. Selecting plates without the usual fuss.

Meet CarriageFlow. Designed to make these steps feel simpler and more intuitive, so you can focus on the train taking shape.

![CarriageFlow](https://cdn.modrinth.com/data/cached_images/4c11b7da041f549a21b033c273901034610838d6.gif) CarriageFlow

Compatibility. More connections. More possibilities.

CRH Hexie works smoothly with Create: Steam'n'Rails, Create: Simurail, Create: Aeronautics, and other mods in the Aeronautics ecosystem. It also supports RailX’s superelevation feature for banked curves.

![Compatible with Simurail](https://cdn.modrinth.com/data/cached_images/50e7f0738241e11b7c44cf90df9601b93ac8c932.jpeg) Compatible with Simurail

![Compatible with RailX superelevation](https://cdn.modrinth.com/data/cached_images/38adecc9359ee5b579afede7759913590f24c29c_0.webp) Compatible with RailX superelevation


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
