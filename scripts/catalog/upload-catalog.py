"""Upload and verify a reviewed public catalog using server-local COS credentials."""
import hashlib
import json
import os
import re
import runpy
import subprocess
import sys
import urllib.request
from pathlib import Path


def main():
    directory = Path(sys.argv[1]).resolve(strict=True)
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    sample = '--sample' in sys.argv
    verify_only = '--verify-only' in sys.argv
    batches = {'common-20261030': 73, 'common-extra-20261030': 32}
    batch = manifest['batch']
    if batch not in batches or (sample and batch != 'common-20261030'):
        raise SystemExit('Unexpected catalog batch')
    if len(manifest['records']) != (2 if sample else batches[batch]):
        raise SystemExit('Unexpected catalog batch or record count')
    if sample and {record['objectKey'] for record in manifest['records']} != {
        'ingredient/common-20261030/eggplant.webp', 'ingredient/common-20261030/zucchini.webp'
    }:
        raise SystemExit('Unexpected sample assets')
    config = json.loads(subprocess.check_output(['docker', 'inspect', 'wgxhl-recipe-backend']))[0]
    env = dict(item.split('=', 1) for item in config['Config']['Env'] if '=' in item)
    for key in ['COS_SECRET_ID', 'COS_SECRET_KEY', 'COS_REGION', 'COS_BUCKET']:
        if not env.get(key):
            raise SystemExit('Missing server setting: ' + key)
        os.environ[key] = env[key]
    if env['COS_BUCKET'] != 'wg-recipe-1385696563':
        raise SystemExit('Refusing to upload catalog into an unexpected bucket')
    # Inherit the app bucket policy; never change the bucket ACL.
    os.environ['COS_OBJECT_ACL'] = ''
    upload = runpy.run_path(str(directory / 'cos-upload.py'))['main']
    for record in manifest['records']:
        key = record['objectKey']
        if not re.fullmatch(r'(ingredient|seasoning)/' + re.escape(batch) + r'/[a-z0-9-]+\.webp', key):
            raise SystemExit('Invalid catalog object key')
        asset = directory / 'assets' / key
        if not asset.is_file():
            raise SystemExit('Missing asset: ' + key)
        if not verify_only:
            sys.argv = ['cos-upload.py', str(asset), key]
            upload()
        url = 'https://{}.cos.{}.myqcloud.com/{}'.format(env['COS_BUCKET'], env['COS_REGION'], key)
        with urllib.request.urlopen(url, timeout=30) as response:
            data = response.read()
            if hashlib.sha256(data).digest() != hashlib.sha256(asset.read_bytes()).digest():
                raise SystemExit('Public asset checksum mismatch: ' + key)
            if response.headers.get_content_type() != 'image/webp':
                raise SystemExit('Unexpected asset content type: ' + key)
        print('Verified:', record['name'], key)
    print('All catalog assets are publicly readable and verified; no database changes made by this uploader.')


if __name__ == '__main__':
    main()
