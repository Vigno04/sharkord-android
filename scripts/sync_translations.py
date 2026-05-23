#!/usr/bin/env python3
import os
import json
import re
import xml.etree.ElementTree as ET
from xml.dom import minidom

# Path configurations
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPTS_DIR)
SHARKORD_ROOT = os.path.abspath(os.path.join(PROJECT_ROOT, "..", "sharkord"))
WEB_LOCALES_DIR = os.path.join(SHARKORD_ROOT, "apps", "client", "src", "i18n", "locales")
RES_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res")

CUSTOM_STRINGS_FILE = os.path.join(SCRIPTS_DIR, "custom_strings.json")

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
    
    # 1. Verify path to the official web client locales
    if not os.path.exists(WEB_LOCALES_DIR):
        print(f"[ERROR] Could not find official Sharkord locales directory at: {WEB_LOCALES_DIR}")
        print("Please verify that the 'sharkord' monorepo is in the parent folder of 'sharkord-android'.")
        return
        
    print(f"[INFO] Found official locales folder: {WEB_LOCALES_DIR}")
    
    # 2. Load custom mobile-only strings
    custom_strings = {}
    if os.path.exists(CUSTOM_STRINGS_FILE):
        try:
            with open(CUSTOM_STRINGS_FILE, "r", encoding="utf-8") as f:
                custom_strings = json.load(f)
            print(f"[INFO] Loaded custom strings file with {len(custom_strings)} locales.")
        except Exception as e:
            print(f"[WARNING] Could not parse custom_strings.json: {e}")
    else:
        print("[INFO] No custom_strings.json file found. Proceeding with Web translations only.")

    # 3. Detect locales in the web repository
    web_locales = [d for d in os.listdir(WEB_LOCALES_DIR) if os.path.isdir(os.path.join(WEB_LOCALES_DIR, d))]
    print(f"[INFO] Detected locales in web project: {', '.join(web_locales)}")

    # 4. Generate XML strings for each detected locale
    for locale in web_locales:
        locale_dir_path = os.path.join(WEB_LOCALES_DIR, locale)
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

    print("--------------------------------------------------")
    print("Sync complete!")
    print("--------------------------------------------------")

if __name__ == "__main__":
    main()
