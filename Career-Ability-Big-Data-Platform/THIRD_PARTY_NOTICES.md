# Third-Party Notices

## PDF rendering

`com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10` is used to render the PDF-only
report feature. It is distributed under LGPL-2.1. The project keeps the library
as an unmodified Maven dependency. Redistributing a binary must retain its
copyright and license notices and provide the corresponding LGPL source offer
required by that license. Upstream source: https://github.com/danfickle/openhtmltopdf

The transitive Apache PDFBox components are licensed under Apache-2.0. Their
notices are retained by the dependency artifacts.

`com.itextpdf:html2pdf:5.0.5` was removed because its AGPL/commercial licensing
does not fit this MIT-licensed repository's release model.

## Chinese font

The runtime image downloads `NotoSansSC[wght].ttf` from the Google Fonts commit
`03781cf7a714af8431d14b6f337f923c774429d7`, verifies its SHA-256
`A3041811A78C361B1DE50F953C805E0244951C21C5BD412F7232EF0D899AF0DA`, and
installs it as `/usr/share/fonts/noto/NotoSansSC.ttf`. Noto Sans SC is licensed
under the SIL Open Font License 1.1. The application embeds the configured font
in generated PDF documents so Chinese titles, tables, and punctuation do not
depend on a viewer's local font set.
