#!/usr/bin/env python3
"""Adapt upstream inline SRG mixin targets for the official-mapped local GameTest JVM.

Only writes a generated development dependency; never modifies or ships upstream jars.
"""
import re
import io
import struct
import sys
import zipfile
from pathlib import Path

source, mappings, output = map(Path, sys.argv[1:])
names = {}
for line in mappings.read_text().splitlines():
    parts = line.split()
    if parts and parts[0] in ('MD:', 'FD:'):
        old = parts[1].rsplit('/', 1)[-1]
        new = parts[3 if parts[0] == 'MD:' else 2].rsplit('/', 1)[-1]
        names[old.encode()] = new.encode()
pattern = re.compile(rb'\b(?:m|f)_\d+_\b')

def remap(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return data
    result = bytearray(data[:10])
    count = struct.unpack_from('>H', data, 8)[0]
    cursor, index = 10, 1
    lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
               11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[cursor]
        cursor += 1
        result.append(tag)
        if tag == 1:
            size = struct.unpack_from('>H', data, cursor)[0]
            cursor += 2
            value = pattern.sub(lambda match: names.get(match[0], match[0]), data[cursor:cursor + size])
            result.extend(struct.pack('>H', len(value)))
            result.extend(value)
            cursor += size
        else:
            size = lengths[tag]
            result.extend(data[cursor:cursor + size])
            cursor += size
            if tag in (5, 6):
                index += 1
        index += 1
    result.extend(data[cursor:])
    return result

def remap_nested_jar(data):
    result = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as incoming, zipfile.ZipFile(result, 'w', zipfile.ZIP_DEFLATED) as outgoing:
        for entry in incoming.infolist():
            nested = incoming.read(entry.filename)
            if entry.filename.endswith('.class'):
                nested = remap(nested)
            outgoing.writestr(entry, nested)
    return result.getvalue()

output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as incoming, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as outgoing:
    for entry in incoming.infolist():
        data = incoming.read(entry.filename)
        if entry.filename.endswith('.class'):
            data = remap(data)
        elif entry.filename.endswith('.jar'):
            data = remap_nested_jar(data)
        outgoing.writestr(entry, data)
