# Unicode Character Database 16.0.0 (vendored subset)

These three files are unmodified copies from
`https://www.unicode.org/Public/16.0.0/ucd/UCD.zip`
(archive SHA-256 `c86dd81f2b14a43b0cc064aa5f89aa7241386801e35c59c7984e579832634eb2`):

| File | SHA-256 |
| --- | --- |
| `auxiliary/GraphemeBreakProperty.txt` | `c29360bd6f7132811d701d29069541e827eb44bfc4c8fbde8c370d6982689dc1` |
| `DerivedCoreProperties.txt` | `39d35161f2954497f69e08bdb9e701493f476a3d30222de20028feda36c1dabd` |
| `emoji/emoji-data.txt` | `f1365a5173eee18e1f98b240cdc492e84a25f1ce7e0c9d1094eb29c41a22696a` |

They are the input of the `generateGraphemeProperties` Gradle task in `:shared`,
which checks these checksums and produces the grapheme-cluster lookup table. The
build never downloads anything for this.

Copyright (c) 1991-2024 Unicode, Inc. Distributed under the Unicode License v3,
https://www.unicode.org/license.txt (also referenced in the header of every file).
