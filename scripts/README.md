# Sharkord i18n Translation Sync Tool

This directory contains utility scripts to keep the Android application's translations synchronized with the official Sharkord server and web client. 

## Overview

Sharkord's web repository is a monorepo that contains multi-language JSON translation files under `apps/client/src/i18n/locales`. To prevent manually copying and translating keys when developing the Android application, this Python script automatically pulls all official translations, processes them, merges them with mobile-specific custom strings, and compiles them into Android's native XML resources (`strings.xml`).

The Android app itself builds and ships with these statically compiled XML resources, keeping the app fast, native, and fully offline-first in production.

---

## File Structure

- `sync_translations.py`: The Python 3 synchronization engine.
- `custom_strings.json`: Local mobile-only strings and translations (e.g. mobile labels, custom tab icons, or mobile-specific error messages).
- `sync.bat` (located in the project root): A simple launcher shortcut to run the script with a single double-click.

---

## How It Works

When you run the tool:
1. **Repository Check**: The script automatically locates the adjacent `sharkord` monorepo at `../sharkord`.
2. **Key Prefixes**: All web translation keys are prefixed with the file name to avoid namespace collisions (e.g. `identityLabel` in `connect.json` becomes `<string name="connect_identityLabel">` in Android).
3. **Format Conversions**: Web-client double-curly variables like `Could not connect: {{message}}` are automatically parsed and translated to Android format specifiers like `Could not connect: %1$s`.
4. **Android XML Escaping**: Special characters such as `'` (apostrophes) and `&` (ampersands) are dynamically escaped to match Android compiler requirements.
5. **Mobile Custom String Merge**: Custom translations from `custom_strings.json` are dynamically merged for each locale.
6. **Output**: Fully localized native `strings.xml` files are written directly to:
   - `app/src/main/res/values/strings.xml` (Default English)
   - `app/src/main/res/values-{locale}/strings.xml` (e.g., `values-it/strings.xml` for Italian, `values-es/strings.xml` for Spanish, etc.)

---

## How to Run It

During development, whenever you want to update translations from the server project, simply:

### Windows
Double-click the `sync.bat` file in the root of the project.

### macOS / Linux
Open a terminal in the root of the project and execute:
```bash
python3 scripts/sync_translations.py
```

---

## Technical Notes

### String Formatting in Code
When using dynamic string placeholders in Kotlin / Jetpack Compose:
```kotlin
// Example connect_connectError string: "Could not connect: %1$s"
val errorMessage = "Server offline"
Text(
    text = stringResource(id = R.string.connect_connectError, errorMessage)
)
```
The Android framework will automatically substitute the `%1$s` with the contents of `errorMessage`.
