#!/usr/bin/env python3
import os
import json
import re
import xml.etree.ElementTree as ET
from xml.dom import minidom
import tempfile
import subprocess
import shutil

# Path configurations
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPTS_DIR))
RES_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res")

CUSTOM_STRINGS_DIR = os.path.join(SCRIPTS_DIR, "custom_strings")

def sanitize_key(name):
    """Sanitize key to conform to Android XML resource name restrictions."""
    # Convert non-alphanumeric characters to underscores
    sanitized = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    # Ensure it doesn't start with a number
    if sanitized and sanitized[0].isdigit():
        sanitized = "_" + sanitized
    return sanitized

def convert_placeholders(text):
    """Convert web i18next double-curly placeholders to native Android placeholders."""
    if not isinstance(text, str):
        return str(text)
    
    # Matches {{param}} or {{ param }}
    pattern = r'\{\{\s*[a-zA-Z0-9_]+\s*\}\}'
    
    count = [0]
    def replace_fn(match):
        count[0] += 1
        return f"%{count[0]}$s"
        
    return re.sub(pattern, replace_fn, text)

def escape_xml(text):
    """Escape characters for Android XML compatibility."""
    if not isinstance(text, str):
        return str(text)
    
    # XML standard escaping
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    # Android string resource escaping: escape apostrophes and double quotes
    text = text.replace("'", "\\'")
    text = text.replace('"', '\\"')
    return text

def flatten_json(y):
    """Flattens a multi-level JSON dictionary into a flat dictionary."""
    out = {}
    def flatten(x, name=''):
        if type(x) is dict:
            for a in x:
                flatten(x[a], name + a + '_')
        elif type(x) is list:
            i = 0
            for a in x:
                flatten(a, name + str(i) + '_')
                i += 1
        else:
            out[name[:-1]] = x
    flatten(y)
    return out

def get_android_locale_dir(locale):
    """Map web locales to Android-specific resource values directories."""
    # Android default (English) is placed in 'values' without suffix
    if locale == 'en':
        return os.path.join(RES_DIR, "values")
    else:
        # e.g., values-it, values-es, values-fr, etc.
        return os.path.join(RES_DIR, f"values-{locale}")

def main():
    print("--------------------------------------------------")
    print("Sharkord i18n Translation Synchronizer (Android)")
    print("--------------------------------------------------")
    
    # 1. Fetch official Sharkord locales directory from GitHub
    print("[INFO] Fetching official Sharkord locales from development branch on GitHub...")
    temp_dir_obj = tempfile.TemporaryDirectory()
    temp_dir = temp_dir_obj.name
    
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", "-b", "development", "https://github.com/Sharkord/sharkord.git", temp_dir],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        web_locales_dir = os.path.join(temp_dir, "apps", "client", "src", "i18n", "locales")
        if not os.path.exists(web_locales_dir):
            raise FileNotFoundError(f"Locales folder not found in cloned repository at: {web_locales_dir}")
        print(f"[SUCCESS] Successfully fetched and located remote locales folder.")
    except Exception as e:
        print(f"[ERROR] Failed to clone remote Sharkord repository: {e}")
        print("Please check your internet connection or git installation.")
        temp_dir_obj.cleanup()
        return

    # 2. Process and integrate files from scripts/auto-translation (excluding custom_strings directory)
    AUTO_TRANSLATION_DIR = SCRIPTS_DIR
    if os.path.exists(AUTO_TRANSLATION_DIR):
        print(f"[INFO] Scanning auto-translation folder: {AUTO_TRANSLATION_DIR}")
        for root, dirs, files in os.walk(AUTO_TRANSLATION_DIR):
            # Exclude custom_strings folder from recursion
            if "custom_strings" in dirs:
                dirs.remove("custom_strings")
                
            for file in files:
                if file == "sync_translations.py" or file == "README.md":
                    continue
                    
                src_path = os.path.join(root, file)
                rel_path = os.path.relpath(src_path, AUTO_TRANSLATION_DIR)
                
                # Check if it's an XML file (pre-compiled Android resources)
                if file.endswith(".xml"):
                    parts = rel_path.split(os.sep)
                    target_locale_dir = None
                    
                    if len(parts) >= 2 and parts[-2].startswith("values"):
                        target_locale_dir = os.path.join(RES_DIR, parts[-2])
                    else:
                        locale_name = os.path.splitext(file)[0]
                        if locale_name == "en" or locale_name == "strings":
                            target_locale_dir = os.path.join(RES_DIR, "values")
                        else:
                            if locale_name.startswith("values-"):
                                target_locale_dir = os.path.join(RES_DIR, locale_name)
                            else:
                                target_locale_dir = os.path.join(RES_DIR, f"values-{locale_name}")
                    
                    if target_locale_dir:
                        os.makedirs(target_locale_dir, exist_ok=True)
                        dest_file = os.path.join(target_locale_dir, "strings.xml")
                        try:
                            if os.path.exists(dest_file):
                                os.remove(dest_file)
                            shutil.copy2(src_path, dest_file)
                            print(f"[SUCCESS] Integrated XML translation from {rel_path} to {os.path.relpath(dest_file, PROJECT_ROOT)}")
                        except Exception as e:
                            print(f"[ERROR] Failed to integrate {rel_path}: {e}")
                
                # Check if it's a JSON file (web client format)
                elif file.endswith(".json"):
                    parts = rel_path.split(os.sep)
                    if len(parts) >= 2:
                        locale_name = parts[-2]
                        dest_dir = os.path.join(web_locales_dir, locale_name)
                        os.makedirs(dest_dir, exist_ok=True)
                        dest_file = os.path.join(dest_dir, file)
                        try:
                            if os.path.exists(dest_file):
                                os.remove(dest_file)
                            shutil.copy2(src_path, dest_file)
                            print(f"[SUCCESS] Integrated JSON translation from {rel_path} to temp web locales ({locale_name}/{file})")
                        except Exception as e:
                            print(f"[ERROR] Failed to integrate {rel_path}: {e}")
                    else:
                        locale_name = os.path.splitext(file)[0]
                        dest_dir = os.path.join(web_locales_dir, locale_name)
                        os.makedirs(dest_dir, exist_ok=True)
                        dest_file = os.path.join(dest_dir, "auto_translation.json")
                        try:
                            if os.path.exists(dest_file):
                                os.remove(dest_file)
                            shutil.copy2(src_path, dest_file)
                            print(f"[SUCCESS] Integrated flat JSON translation from {rel_path} to temp web locales ({locale_name}/auto_translation.json)")
                        except Exception as e:
                            print(f"[ERROR] Failed to integrate {rel_path}: {e}")

    # 3. Load custom mobile-only strings (one file per language under custom_strings/ folder)
    custom_strings = {}
    if os.path.exists(CUSTOM_STRINGS_DIR):
        print(f"[INFO] Loading custom strings from directory: {CUSTOM_STRINGS_DIR}")
        for filename in os.listdir(CUSTOM_STRINGS_DIR):
            if filename.endswith(".json"):
                locale = os.path.splitext(filename)[0]
                json_path = os.path.join(CUSTOM_STRINGS_DIR, filename)
                try:
                    with open(json_path, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    
                    flattened = flatten_json(data)
                    custom_strings[locale] = flattened
                    print(f"[INFO] Loaded custom strings for locale '{locale}' ({len(flattened)} keys)")
                except Exception as e:
                    print(f"[WARNING] Skipping custom strings file '{filename}' due to error: {e}")
    else:
        print("[INFO] No custom_strings/ directory found. Proceeding with Web translations only.")

    try:
        # 4. Detect locales in the web repository
        web_locales = [d for d in os.listdir(web_locales_dir) if os.path.isdir(os.path.join(web_locales_dir, d))]
        print(f"[INFO] Detected locales in web project: {', '.join(web_locales)}")

        # 5. Generate XML strings for each detected locale
        for locale in web_locales:
            locale_dir_path = os.path.join(web_locales_dir, locale)
            merged_translations = {}
            
            # A. Process official web files
            for filename in os.listdir(locale_dir_path):
                if filename.endswith(".json"):
                    file_base = os.path.splitext(filename)[0]
                    json_path = os.path.join(locale_dir_path, filename)
                    
                    try:
                        with open(json_path, "r", encoding="utf-8") as f:
                            data = json.load(f)
                            
                        flattened = flatten_json(data)
                        for key, val in flattened.items():
                            # Standardize name format e.g. connect_identityLabel
                            android_key = sanitize_key(f"{file_base}_{key}")
                            formatted_val = convert_placeholders(val)
                            merged_translations[android_key] = formatted_val
                    except Exception as e:
                        print(f"[WARNING] Skipping {filename} in '{locale}' due to error: {e}")
        
            # B. Merge custom mobile strings for this locale
            if locale in custom_strings:
                for key, val in custom_strings[locale].items():
                    android_key = sanitize_key(key)
                    merged_translations[android_key] = val
                    
            # C. Generate native Android strings.xml
            if merged_translations:
                # Sort keys alphabetically for clean output
                sorted_keys = sorted(merged_translations.keys())
                
                # Build XML Tree
                root = ET.Element("resources")
                
                # Always put app_name at the top
                app_name_elem = ET.SubElement(root, "string", name="app_name")
                app_name_elem.text = "Sharkord"
                
                for key in sorted_keys:
                    if key == "app_name":
                        continue # Skip as we wrote it above
                    
                    elem = ET.SubElement(root, "string", name=key)
                    elem.text = escape_xml(merged_translations[key])
                    
                # Formatting XML to look clean and readable
                raw_xml = ET.tostring(root, encoding="utf-8")
                reparsed = minidom.parseString(raw_xml)
                pretty_xml = reparsed.toprettyxml(indent="    ")
                
                # Remove the default <?xml version="1.0" ?> header as it is optional in Android res files
                if pretty_xml.startswith("<?xml"):
                    pretty_xml = pretty_xml.split("\n", 1)[1]
                    
                # Target output folder
                dest_dir = get_android_locale_dir(locale)
                os.makedirs(dest_dir, exist_ok=True)
                dest_file = os.path.join(dest_dir, "strings.xml")
                
                try:
                    with open(dest_file, "w", encoding="utf-8") as f:
                        f.write(pretty_xml.strip() + "\n")
                    print(f"[SUCCESS] Generated: {os.path.relpath(dest_file, PROJECT_ROOT)} ({len(merged_translations)} strings)")
                except Exception as e:
                    print(f"[ERROR] Failed to write {dest_file}: {e}")

    finally:
        print("[INFO] Cleaning up temporary folders...")
        temp_dir_obj.cleanup()

    print("--------------------------------------------------")
    print("Sync complete!")
    print("--------------------------------------------------")

if __name__ == "__main__":
    main()
