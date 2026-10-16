# Keel TV identity

A rounded blue play button merging into a ship's hull, with a swept keel fin, golden waterline,
and white play aperture. `mark.svg` is the editable vector master. Blue is `#26A5E4`, gold is
`#FCB30E`, charcoal is `#0F0F11`, and light lettering is `#F2F2F4`.

Run `npm install && npm run brand:generate` from this folder after editing the artwork. That
rewrites the logos here and the app's icons.

`generate.mjs` outlines the wordmark in Manrope Semibold, with "Keel" and "TV" at the same
size and weight, a shared baseline, and slightly tightened spacing. The square icon master
uses a 256 × 256 viewBox. The artwork needs no runtime font and the generator needs nothing
from the app.

`manrope-semibold.ttf` is a static weight-600 instance of
[Manrope from Google Fonts](https://github.com/google/fonts/tree/main/ofl/manrope), distributed
under the SIL Open Font License in `manrope-OFL.txt`.

`logo-light.svg` has dark lettering for light surfaces; `logo-dark.svg` has light lettering.
Generated artwork is committed, so an app build never needs to regenerate it.

The generator writes:

- The app's README logos in `docs/`.
- Android vector marks/wordmark, adaptive and monochrome launcher layers, legacy square
  and round icons, and TV banners at all five densities. Adaptive artwork stays within
  the central safe area; TV banners include the full name. The monochrome layer knocks out
  the play aperture and waterline to preserve both details when Android applies a theme colour.

Platform references: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive),
and [Android TV icons and banners](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines).
