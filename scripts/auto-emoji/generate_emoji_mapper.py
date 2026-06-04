import json
import os
import urllib.request

def run():
    url = "https://raw.githubusercontent.com/omnidan/node-emoji/master/lib/emoji.json"
    print(f"Fetching emoji data from {url}...")
    
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"Error fetching emoji data: {e}")
        return

    # Add standard shortcodes if missing
    standard_emojis = {
        'thumbsup': '👍',
        'thumbsdown': '👎',
        'ok_hand': '👌',
        'heart': '❤️',
        'joy': '😂',
        'fire': '🔥',
        'open_mouth': '😮',
        'smile': '😊'
    }
    for key, val in standard_emojis.items():
        if key not in data:
            data[key] = val

    map_entries = []
    for key, val in data.items():
        # Escape backslashes and double quotes
        escaped_key = key.replace('\\', '\\\\').replace('"', '\\"')
        escaped_val = val.replace('\\', '\\\\').replace('"', '\\"')
        map_entries.append(f'        "{escaped_key}" to "{escaped_val}"')

    map_content = ",\n".join(map_entries)

    kotlin_code = f"""package com.sharkord.android.ui.home.components

object EmojiMapper {{
    private val emojiMap = mapOf(
{map_content}
    )

    /**
     * Resolves an emoji code (shortcode like "grinning" or unicode like "😀") to its display unicode character.
     * If it's already a unicode character or not found in the map, it returns the input code.
     */
    fun map(code: String): String {{
        val trimmed = code.trim().removeSurrounding(":")
        return emojiMap[trimmed] ?: emojiMap[code] ?: code
    }}

    /**
     * Checks if the given text consists entirely of emojis and whitespace.
     */
    fun isEmojiOnly(text: String): Boolean {{
        if (text.isBlank()) return false
        
        var emojiCount = 0
        var i = 0
        while (i < text.length) {{
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            
            if (Character.isWhitespace(codePoint)) {{
                i += charCount
                continue
            }}
            
            if (isEmojiCodePoint(codePoint) || isEmojiModifierOrSupport(codePoint)) {{
                emojiCount++
                i += charCount
                continue
            }}
            
            // If it's a digit or keycap base character, check if followed by variation/keycap modifier
            if (codePoint == 0x23 || codePoint == 0x2A || (codePoint in 0x30..0x39)) {{
                if (i + charCount < text.length) {{
                    val nextCp = text.codePointAt(i + charCount)
                    val nextCharCount = Character.charCount(nextCp)
                    if (nextCp == 0xFE0F) {{
                        if (i + charCount + nextCharCount < text.length) {{
                            val nextNextCp = text.codePointAt(i + charCount + nextCharCount)
                            if (nextNextCp == 0x20E3) {{
                                emojiCount++
                                i += charCount + nextCharCount + Character.charCount(nextNextCp)
                                continue
                            }}
                        }}
                    }} else if (nextCp == 0x20E3) {{
                        emojiCount++
                        i += charCount + nextCharCount
                        continue
                    }}
                }}
                return false
            }}
            
            return false
        }}
        
        return emojiCount > 0
    }}

    private fun isEmojiCodePoint(cp: Int): Boolean {{
        return (cp in 0x1F600..0x1F64F) || // Emoticons
               (cp in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs
               (cp in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
               (cp in 0x1FA70..0x1FAFF) || // Symbols and Pictographs Extended-A
               (cp in 0x2600..0x26FF)   || // Misc Symbols
               (cp in 0x2700..0x27BF)   || // Dingbats
               (cp in 0x1F680..0x1F6FF) || // Transport and Map
               (cp in 0x1F1E6..0x1F1FF) || // Regional Indicator Symbols
               (cp in 0x1F170..0x1F19A) || // Enclosed Alphanumeric Supplement
               (cp in 0x1F200..0x1F2FF) || // Enclosed Ideographic Supplement
               (cp in 0x2190..0x21FF)   || // Arrows
               (cp in 0x2300..0x23FF)   || // Misc Technical
               (cp in 0x2934..0x2B55)      // Misc Arrows and Shapes
    }}

    private fun isEmojiModifierOrSupport(cp: Int): Boolean {{
        return cp == 0x200D || // Zero Width Joiner
               (cp in 0xFE00..0xFE0F) || // Variation Selectors
               cp == 0x20E3 || // Combining Enclosing Keycap
               (cp in 0x1F3FB..0x1F3FF) // Emoji Modifier Fitzpatricks (skin tones)
    }}
}}
"""

    script_dir = os.path.dirname(os.path.abspath(__file__))
    target_dir = os.path.abspath(os.path.join(script_dir, "../../app/src/main/java/com/sharkord/android/ui/home/components"))
    
    if not os.path.exists(target_dir):
        os.makedirs(target_dir, exist_ok=True)

    target_path = os.path.join(target_dir, "EmojiMapper.kt")
    with open(target_path, "w", encoding="utf-8") as f:
        f.write(kotlin_code)
        
    print(f"Successfully generated EmojiMapper.kt at {target_path} with {len(data)} emojis.")

if __name__ == "__main__":
    run()
