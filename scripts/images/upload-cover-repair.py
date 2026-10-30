"""Validate originals, upload new cover keys, and verify bytes; never modify SQL data."""
import hashlib
import json
import os
import re
import runpy
import subprocess
import sys
import urllib.request
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    directory = Path(sys.argv[1]).resolve(strict=True)
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    if manifest['batch'] != 'cover-repair-20261030' or len(manifest['records']) != 8:
        raise SystemExit('Unexpected cover repair batch')
    config = json.loads(subprocess.check_output(['docker', 'inspect', 'wgxhl-recipe-backend']))[0]
    env = dict(item.split('=', 1) for item in config['Config']['Env'] if '=' in item)
    for key in ['COS_SECRET_ID', 'COS_SECRET_KEY', 'COS_REGION', 'COS_BUCKET']:
        if not env.get(key):
            raise SystemExit('Missing server setting: ' + key)
        os.environ[key] = env[key]
    if env['COS_BUCKET'] != 'wg-recipe-1385696563':
        raise SystemExit('Unexpected bucket')
    os.environ['COS_OBJECT_ACL'] = ''
    upload = runpy.run_path(str(directory / 'cos-upload.py'))['main']
    base = 'https://{}.cos.{}.myqcloud.com/'.format(env['COS_BUCKET'], env['COS_REGION'])
    for record in manifest['records']:
        old, new = record['oldKey'], record['objectKey']
        if not re.fullmatch(r'recipe/cover/[a-z0-9-]+\.webp', old) or new != 'recipe/cover/optimized-20261030/' + Path(old).name:
            raise SystemExit('Invalid cover paths')
        original = directory / 'originals' / Path(old).name
        asset = directory / 'assets' / new
        if sha(original.read_bytes()) != record['originalSha256'] or sha(asset.read_bytes()) != record['sha256']:
            raise SystemExit('Local cover checksum mismatch')
        with urllib.request.urlopen(base + old, timeout=45) as response:
            if sha(response.read()) != record['originalSha256']:
                raise SystemExit('Live original changed: ' + old)
        sys.argv = ['cos-upload.py', str(asset), new]
        upload()
        with urllib.request.urlopen(base + new, timeout=45) as response:
            data = response.read()
            if sha(data) != record['sha256'] or response.headers.get_content_type() != 'image/webp':
                raise SystemExit('Public cover verification failed: ' + new)
            if data[:4] != b'RIFF' or data[8:12] != b'WEBP':
                raise SystemExit('Not a real WebP')
        print('Verified new cover:', record['name'], len(data))
    print('All 8 new covers verified. Original objects preserved; no SQL changes made by uploader.')


if __name__ == '__main__':
    main()
