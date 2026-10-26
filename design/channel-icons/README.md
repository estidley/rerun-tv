# Rerun channel identities / 1990s

102 icons and 102 outlined wordmarks: 60 named channels, 40 supported TV/music decade templates, and music/custom fallbacks. The approved Marvelous family and centered Toonuki artwork are preserved. All SVGs contain standalone vector paths with no external fonts or images.

`manifest.json` maps stable channel IDs to the files. Managed `:w` feeds reuse their East icon. Arbitrary Plex music genres use `music-generic`; unknown/custom channels use `custom-generic`. Channel names remain normal UI text so user renames and West suffixes stay readable.

The app displays lossless 256 px WebP icons in the guide, channel manager and playback overlays. SVG and wordmark masters stay here for editing and other uses; they are not decoded during TV playback. Regenerate committed Android drawables and the Kotlin resource lookup with:

```powershell
cd tools/channel-icons
npm ci
npm run build
```

[Editable Figma library](https://www.figma.com/design/O5ZAn2Nl7AdUEpfSr8BPDB?node-id=11-3)
