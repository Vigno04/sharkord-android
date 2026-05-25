# Sharkord i18n Translation Sync Tool

This directory contains utility scripts to keep the Android application's translations synchronized with the official Sharkord server and web client. 

## Overview

Sharkord's web repository is a monorepo that contains multi-language JSON translation files under `apps/client/src/i18n/locales`. To prevent manually copying and translating keys when developing the Android application, this Python script automatically pulls all official translations from the development branch on GitHub, processes them, merges them with mobile-specific custom strings, and compiles them into Android's native XML resources (`strings.xml`).

The Android app itself builds and ships with these statically compiled XML resources.

---

## File Structure

- `sync_translations.py`: The Python 3 remote synchronization engine.
- `custom_strings.json`: Local mobile-only strings and translations (e.g. mobile labels, custom tab icons, or mobile-specific error messages).
- `auto-translation/`: A folder where you can place temporary custom translation files (JSON or XML) to be automatically processed/moved and integrated.
- `sync.bat` (located in the project root): A simple launcher shortcut to run the script with a single double-click.

---

## How It Works

When you run the tool:
1. **Remote Fetching**: The script automatically fetches the official translations directly from the development branch of `https://github.com/Sharkord/sharkord` using a temporary directory, removing the need for a local monorepo checkout.
2. **Auto-Translation Integration**: The script scans the `scripts/auto-translation` folder. Any `.json` files are automatically moved and integrated into the locales folder before compilation. Any `.xml` translation files (pre-compiled Android resources) are automatically moved directly to the appropriate `values-{locale}` resources directories.
3. **Key Prefixes**: All web translation keys are prefixed with the file name to avoid namespace collisions (e.g. `identityLabel` in `connect.json` becomes `<string name="connect_identityLabel">` in Android).
4. **Format Conversions**: Web-client double-curly variables like `Could not connect: {{message}}` are automatically parsed and translated to Android format specifiers like `Could not connect: %1$s`.
5. **Android XML Escaping**: Special characters such as `'` (apostrophes) and `&` (ampersands) are dynamically escaped to match Android compiler requirements.
6. **Mobile Custom String Merge**: Custom translations from `custom_strings.json` are dynamically merged for each locale.
7. **Output**: Fully localized native `strings.xml` files are written directly to:
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

