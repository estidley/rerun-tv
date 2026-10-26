# Rerun channel identities / 1990s

102 icons and 102 outlined wordmarks: 60 named channels, 40 supported TV/music decade templates, and music/custom fallbacks. The approved Marvelous family and centered Toonuki artwork are preserved. All SVGs contain standalone vector paths with no external fonts or images.

`manifest.json` maps stable channel IDs to the files. Arbitrary Plex music genres use `music-generic`; unknown/custom channels use `custom-generic`. Guide wordmarks retain their designed names; the selected channel's current name appears in the program panel, so custom names and user renames stay readable.

The guide displays lossless 400 x 270 WebP renders of the wordmark SVGs, fitted without stretching or cropping. Channel management and playback overlays retain 256 px square icons. SVG masters stay here for editing; they are not decoded during TV playback. Regenerate both sets of committed Android drawables and the Kotlin resource lookups with:

```powershell
Set-Location tools\channel-icons
npm ci
npm run build
```

[Editable Figma library](https://www.figma.com/design/O5ZAn2Nl7AdUEpfSr8BPDB?node-id=11-3)
