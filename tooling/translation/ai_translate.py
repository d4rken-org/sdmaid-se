#!/usr/bin/env python3
"""
AI Translation Script for Android strings.xml files (Improved version)

This script facilitates AI-based translation of Android string resources by:
1. Cleaning up obsolete entries from target files
2. Extracting missing translations into batch files for AI processing
3. Applying translated batch files back to target files

Usage:
    python3 ai_translate_improved.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --cleanup
    python3 ai_translate_improved.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --extract
    python3 ai_translate_improved.py --source app/src/main/res/values/strings.xml --target app/src/main/res/values-lv/strings.xml --apply batch_lv_001.json
"""

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Set
import re
import shutil
from datetime import datetime


class AndroidStringTranslator:
    def __init__(self, source_file: str, target_file: str):
        self.source_file = Path(source_file)
        self.target_file = Path(target_file)
        self.batch_size = 50
        
        if not self.source_file.exists():
            raise FileNotFoundError(f"Source file not found: {source_file}")
        
        # Extract language code from target path (e.g., values-lv -> lv)
        self.language_code = self._extract_language_code(target_file)
    
    def _extract_language_code(self, target_file: str) -> str:
        """Extract language code from values-xx path"""
        path = Path(target_file)
        parent_dir = path.parent.name
        if parent_dir.startswith('values-'):
            return parent_dir[7:]  # Remove 'values-' prefix
        return 'unknown'
    
    def _backup_file(self, file_path: Path) -> None:
        """Create a backup of the file before modification"""
        if file_path.exists():
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_path = file_path.with_suffix(f'.backup_{timestamp}.xml')
            shutil.copy2(file_path, backup_path)
            print(f"Backup created: {backup_path}")
    
    def _parse_xml(self, file_path: Path) -> Tuple[ET.Element, ET.ElementTree]:
        """Parse XML file and return root element and tree"""
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            return root, tree
        except ET.ParseError as e:
            raise ValueError(f"Invalid XML in {file_path}: {e}")
    
    def _get_string_entries(self, root: ET.Element) -> Dict[str, ET.Element]:
        """Extract all string entries from XML root"""
        entries = {}
        for element in root.findall('.//string'):
            name = element.get('name')
            if name:
                entries[name] = element
        return entries
    
    def _get_plural_entries(self, root: ET.Element) -> Dict[str, ET.Element]:
        """Extract all plural entries from XML root"""
        entries = {}
        for element in root.findall('.//plurals'):
            name = element.get('name')
            if name:
                entries[name] = element
        return entries
    
    def _get_context_for_string(self, root: ET.Element, target_name: str) -> str:
        """Generate context information for a string to help with translation"""
        context_parts = []
        
        # Find the target element
        target_element = None
        for element in root.findall('.//string'):
            if element.get('name') == target_name:
                target_element = element
                break
        
        if target_element is None:
            return ""
        
        # Look for nearby strings that might provide context
        all_strings = list(root.findall('.//string'))
        try:
            target_index = all_strings.index(target_element)
            
            # Check previous and next strings for context clues
            context_window = 2
            start_idx = max(0, target_index - context_window)
            end_idx = min(len(all_strings), target_index + context_window + 1)
            
            related_strings = []
            for i in range(start_idx, end_idx):
                if i != target_index:
                    elem = all_strings[i]
                    name = elem.get('name', '')
                    text = elem.text or ''
                    if name and text:
                        related_strings.append(f"{name}: {text[:50]}...")
            
            if related_strings:
                context_parts.append("Related strings: " + "; ".join(related_strings))
        except ValueError:
            pass
        
        # Look for common prefixes/suffixes in the name for categorization
        name_parts = target_name.split('_')
        if len(name_parts) > 1:
            category = name_parts[0]
            context_parts.append(f"Category: {category}")
        
        return " | ".join(context_parts)
    
    def _write_formatted_xml(self, tree: ET.ElementTree, file_path: Path) -> None:
        """Write XML with proper formatting"""
        # Add proper indentation
        self._indent_xml(tree.getroot())
        
        # Write with declaration
        tree.write(file_path, encoding='utf-8', xml_declaration=True)
        
        # Fix the encoding declaration format
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Replace single quotes with double quotes in XML declaration
        content = content.replace("encoding='utf-8'", 'encoding="utf-8"')
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
    
    def _indent_xml(self, elem: ET.Element, level: int = 0) -> None:
        """Add proper indentation to XML elements"""
        indent = "  "  # 2 spaces
        i = "\n" + level * indent
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = i + indent
            if not elem.tail or not elem.tail.strip():
                elem.tail = i
            for child in elem:
                self._indent_xml(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = i
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = i
    
    def cleanup_obsolete_entries(self) -> None:
        """Remove entries from target that don't exist in source"""
        if not self.target_file.exists():
            print(f"Target file doesn't exist: {self.target_file}")
            return
        
        print(f"Cleaning up obsolete entries in {self.target_file}")
        
        # Parse both files
        source_root, _ = self._parse_xml(self.source_file)
        target_root, target_tree = self._parse_xml(self.target_file)
        
        # Get all entries from source
        source_strings = set(self._get_string_entries(source_root).keys())
        source_plurals = set(self._get_plural_entries(source_root).keys())
        
        # Find obsolete entries in target
        obsolete_count = 0
        
        # Remove obsolete strings
        for string_elem in target_root.findall('.//string'):
            name = string_elem.get('name')
            if name and name not in source_strings:
                print(f"Removing obsolete string: {name}")
                target_root.remove(string_elem)
                obsolete_count += 1
        
        # Remove obsolete plurals
        for plural_elem in target_root.findall('.//plurals'):
            name = plural_elem.get('name')
            if name and name not in source_plurals:
                print(f"Removing obsolete plural: {name}")
                target_root.remove(plural_elem)
                obsolete_count += 1
        
        if obsolete_count > 0:
            self._backup_file(self.target_file)
            self._write_formatted_xml(target_tree, self.target_file)
            print(f"Removed {obsolete_count} obsolete entries")
        else:
            print("No obsolete entries found")
    
    def extract_missing_translations(self) -> None:
        """Extract missing translations into batch files"""
        print(f"Extracting missing translations from {self.source_file}")
        
        # Parse source file
        source_root, _ = self._parse_xml(self.source_file)
        
        # Parse target file if it exists
        target_strings = {}
        target_plurals = {}
        if self.target_file.exists():
            target_root, _ = self._parse_xml(self.target_file)
            target_strings = self._get_string_entries(target_root)
            target_plurals = self._get_plural_entries(target_root)
        
        # Find missing strings
        source_strings = self._get_string_entries(source_root)
        missing_strings = []
        
        for name, element in source_strings.items():
            if name not in target_strings:
                text = element.text or ''
                context = self._get_context_for_string(source_root, name)
                missing_strings.append({
                    'type': 'string',
                    'name': name,
                    'source_text': text,
                    'context': context
                })
        
        # Find missing plurals
        source_plurals = self._get_plural_entries(source_root)
        missing_plurals = []
        
        for name, element in source_plurals.items():
            if name not in target_plurals:
                items = {}
                for item in element.findall('item'):
                    quantity = item.get('quantity')
                    text = item.text or ''
                    if quantity:
                        items[quantity] = text
                
                missing_plurals.append({
                    'type': 'plural',
                    'name': name,
                    'items': items,
                    'context': f"Plural forms for: {name}"
                })
        
        # Combine all missing entries
        all_missing = missing_strings + missing_plurals
        
        if not all_missing:
            print("No missing translations found")
            return
        
        print(f"Found {len(all_missing)} missing translations")
        
        # Create batch files
        batch_num = 1
        for i in range(0, len(all_missing), self.batch_size):
            batch_entries = all_missing[i:i + self.batch_size]
            
            batch_data = {
                'language': self.language_code,
                'batch_id': batch_num,
                'source_file': str(self.source_file),
                'target_file': str(self.target_file),
                'entries': batch_entries
            }
            
            batch_filename = f"batch_{self.language_code}_{batch_num:03d}.json"
            with open(batch_filename, 'w', encoding='utf-8') as f:
                json.dump(batch_data, f, indent=2, ensure_ascii=False)
            
            print(f"Created batch file: {batch_filename} ({len(batch_entries)} entries)")
            batch_num += 1
    
    def apply_translations(self, batch_file: str) -> None:
        """Apply translations from a batch file to the target"""
        print(f"Applying translations from {batch_file}")
        
        # Load batch file
        with open(batch_file, 'r', encoding='utf-8') as f:
            batch_data = json.load(f)
        
        # Create target file if it doesn't exist
        if not self.target_file.exists():
            self.target_file.parent.mkdir(parents=True, exist_ok=True)
            # Create minimal XML structure
            root = ET.Element('resources')
            tree = ET.ElementTree(root)
            self._write_formatted_xml(tree, self.target_file)
        
        # Parse target file
        target_root, target_tree = self._parse_xml(self.target_file)
        
        self._backup_file(self.target_file)
        
        applied_count = 0
        
        for entry in batch_data['entries']:
            if entry['type'] == 'string':
                if 'translated_text' in entry and entry['translated_text']:
                    # Create new string element
                    string_elem = ET.SubElement(target_root, 'string')
                    string_elem.set('name', entry['name'])
                    string_elem.text = entry['translated_text']
                    applied_count += 1
                    print(f"Applied string: {entry['name']}")
            
            elif entry['type'] == 'plural':
                if 'translated_items' in entry and entry['translated_items']:
                    # Create new plurals element
                    plural_elem = ET.SubElement(target_root, 'plurals')
                    plural_elem.set('name', entry['name'])
                    
                    for quantity, text in entry['translated_items'].items():
                        if text:
                            item_elem = ET.SubElement(plural_elem, 'item')
                            item_elem.set('quantity', quantity)
                            item_elem.text = text
                    
                    applied_count += 1
                    print(f"Applied plural: {entry['name']}")
        
        if applied_count > 0:
            self._write_formatted_xml(target_tree, self.target_file)
            print(f"Applied {applied_count} translations")
        else:
            print("No translations found in batch file")


def main():
    parser = argparse.ArgumentParser(description='AI Translation Script for Android strings.xml')
    parser.add_argument('--source', required=True, help='Source strings.xml file')
    parser.add_argument('--target', required=True, help='Target strings.xml file')
    
    # Modes
    parser.add_argument('--cleanup', action='store_true', help='Remove obsolete entries from target')
    parser.add_argument('--extract', action='store_true', help='Extract missing translations to batch files')
    parser.add_argument('--apply', help='Apply translations from batch file')
    
    args = parser.parse_args()
    
    # Validate arguments
    if not (args.cleanup or args.extract or args.apply):
        parser.error("Must specify one of: --cleanup, --extract, or --apply")
    
    try:
        translator = AndroidStringTranslator(args.source, args.target)
        
        if args.cleanup:
            translator.cleanup_obsolete_entries()
        
        elif args.extract:
            translator.extract_missing_translations()
        
        elif args.apply:
            translator.apply_translations(args.apply)
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()