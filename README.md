# Snip Notes (v2)

Works with no signal. Three coloured pages:

- **Snap & Reference (teal)**: take a small photo with a note. Tap a snip to edit its note, pin it to the top, star it or delete it. Tap Select (or press and hold) to pin, star or delete several at once.
- **Scan Prices > Item + price (blue)**: point at a shelf label, item name and price fill two separate boxes, tap Add. Every name and price in the slim list is editable. Copy for spreadsheet copies item, tab, price per row.
- **Scan Prices > Shopping list (purple)**: point at a product name and tap Add. Fix spelling by tapping a name, tick items off, Copy list.

## Get the APK from GitHub

1. Create a new GitHub repository and upload everything in this folder, including the hidden `.github` folder.
2. Open the repository's **Actions** tab and run **Build APK** (it also runs on every push to main).
3. When it finishes, open the run and download the **SnipNotes-apk** artifact (a zip).
4. Inside: `app-release.apk` (smaller) and `app-debug.apk` (backup). Install either on the phone. Allow "install unknown apps" if asked.

If the release build step shows a warning, the debug APK is still produced.

## Size

Built for 64-bit ARM phones only, with the text-reading model bundled in. Expect roughly 8 to 12 MB for the release APK.

## Notes

- Photos are stored in the app's private storage, downscaled to 1280 px on the longest side.
- Prices are copied as plain numbers (no £ sign) so spreadsheets treat them as numbers.
