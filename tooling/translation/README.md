# AI Translation Tooling

AI-assisted translation workflow for SD Maid SE Android application with 64+ supported languages.

## Quick Start

The script supports these main operations:

- **Cleanup**: Remove obsolete entries from target files
- **Extract**: Generate translation batch files for missing strings
- **Apply**: Insert completed translations into target files
- **Cleanup-backups**: Remove old backup files from target directories

## Files

- `ai_translate.py` - Main translation script
- `batches/` - Directory containing translation batch files (`batch_*.json`)
- `README.md` - This documentation

## Workflow Steps

### 1. Cleanup Obsolete Entries

First, remove any strings/plurals from the target file that no longer exist in the source:

```bash
python3 tooling/translation/ai_translate.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --cleanup
```

This will:

- Create a backup of the target file
- Remove obsolete entries
- Report how many entries were removed

### 2. Extract Missing Translations

Generate batch files containing strings that need translation:

```bash
python3 tooling/translation/ai_translate.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --extract
```

This creates multiple `batch_lv_XXX.json` files with 50 entries each. Each batch file contains:

- Original source text
- Context information (nearby strings, category)
- Structure for both regular strings and plurals

### 3. Translate Batch Files

Open each batch file and add translations. For example, in `batch_lv_001.json`:

#### For regular strings:

```json
{
  "type": "string",
  "name": "onboarding_privacy_continue_action",
  "source_text": "Continue",
  "context": "...",
  "translated_text": "Turpināt"
}
```

#### For plurals:

```json
{
  "type": "plural",
  "name": "tasks_activity_active_notification_message",
  "items": {
    "one": "%d active task",
    "other": "%d active tasks"
  },
  "context": "...",
  "translated_items": {
    "zero": "%d aktīvu uzdevumu",
    "one": "%d aktīvs uzdevums", 
    "other": "%d aktīvi uzdevumi"
  }
}
```

### 4. Apply Translations

After translating a batch file, apply it to the target:

```bash
python3 tooling/translation/ai_translate.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --apply batch_lv_001.json
```

This will:

- Create a backup of the target file
- Insert the translations into the XML
- Report how many translations were applied

### 5. Repeat for All Batches

Continue translating and applying batch files until all are processed.

### 6. Clean Up Backup Files (Optional)

The script automatically removes backup files after successful operations. To manually clean up old backup files:

```bash
python3 tooling/translation/ai_translate.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --cleanup-backups
```

## Tips for AI Translation

When using AI tools (ChatGPT, Claude, etc.) to translate the batch files:

### Prompt Template:

```
Please translate the following Android app strings from English to [LANGUAGE]. 

Context: This is for "SD Maid SE", an Android storage cleaning application. 

For each entry, please:
1. Add a "translated_text" field for string entries
2. Add a "translated_items" field for plural entries (maintaining proper plural forms for the target language)
3. Preserve any formatting like %d, %s, %1$s placeholders
4. Keep the meaning consistent with the app's context

Here's the batch file to translate:
[PASTE BATCH FILE CONTENT]
```

### Important Translation Notes:

- Preserve all placeholder formats (%d, %s, %1$s, etc.)
- Maintain proper plural forms for the target language
- Consider the app context (storage cleaning, file management)
- Keep technical terms consistent
- Use the context provided to understand the string's purpose

## Batch File Structure

Each batch file contains:

- `language`: Target language code
- `batch_id`: Sequential batch number
- `source_file`: Path to source strings.xml
- `target_file`: Path to target strings.xml
- `entries`: Array of translation entries

## Error Recovery

If something goes wrong:

1. The script automatically creates backups before any changes
2. Backups are named with timestamps: `strings.backup_YYYYMMDD_HHMMSS.xml`
3. Simply restore from backup if needed

## Validation

After applying translations, verify the XML structure:

```bash
xmllint --noout app/src/main/res/values-lv/strings.xml
```

If the XML is malformed, restore from backup and try again.