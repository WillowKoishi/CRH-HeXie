# CRH1A panels

`crh1a_panel_small_window.bbmodel` is registered as
`hexie:crh1a_panel_small_window` in the EMU creative tab.
`crh1a_panel_nowindow.bbmodel` is registered alongside it as
`hexie:crh1a_panel_nowindow` (CRH1A 无窗车厢板).

Run `python art_assets/CRH1/export_panels.py` from the repository root to
regenerate its OBJ models, materials, split body/glass textures, blockstates,
item model and collision data. Add `--check` to verify the packaged resources.
The exporter uses Python's standard library and the existing bogey mesh helpers.

Only the seven meshes inside the authored panel group are exported. The
top-level `参考方块` has been removed from the source; the exporter also filters
out reference cubes if reintroduced, excluding them from both handed game
models and their collision bounds. The panel retains its
original dimensions (one block wide, two blocks high), UVs and RGBA colors.
The reference frame matches the existing CRH2A parts. The original texture is
split by alpha so opaque bodywork and translucent windows use separate render
passes. Handed models reverse face winding when mirrored.
Invisible member cells use a panel-specific empty model with the authored
particle texture, so hitting or breaking the upper cell does not show the
missing-texture sprite.

Placement, member blocks, wrench rotation, drops, Create schematics and static
shell rendering use the shared carriage-part pipeline. The CRH1A panel does
not add the CRH2A-specific vehicle markings or passenger-display attachments.

The artwork license and attribution rules in [../README.md](../README.md)
apply to these source and generated assets.

The solid panel exports two meshes / sixteen faces, with its top-level reference
cube removed from the source and excluded by the exporter. Its fully opaque
texture uses only the body render layer; it has no glass transparency pass.
Both panels share cached shapes, handed placement and particle-textured member
models for the upper cell.
