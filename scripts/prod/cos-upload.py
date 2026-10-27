#!/usr/bin/env python3
"""上传文件到腾讯云 COS（PUT Object，签名 v5，仅用标准库）。

用法：cos-upload.py <本地文件> <对象键>
环境变量：COS_SECRET_ID / COS_SECRET_KEY / COS_REGION / COS_BUCKET
可选：COS_OBJECT_ACL（默认 private）
"""
import hashlib
import hmac
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def require_env(name):
    value = os.environ.get(name, '').strip()
    if not value:
        raise SystemExit(f'缺少环境变量 {name}')
    return value


def hmac_sha1(key, message):
    return hmac.new(key, message.encode('utf-8'), hashlib.sha1).hexdigest()


def main():
    if len(sys.argv) < 3:
        raise SystemExit('用法: cos-upload.py <本地文件> <对象键>')
    local_path = sys.argv[1]
    object_key = sys.argv[2].lstrip('/')
    if not os.path.isfile(local_path):
        raise SystemExit(f'文件不存在: {local_path}')

    secret_id = require_env('COS_SECRET_ID')
    secret_key = require_env('COS_SECRET_KEY')
    region = require_env('COS_REGION')
    bucket = require_env('COS_BUCKET')
    acl = os.environ.get('COS_OBJECT_ACL', 'private').strip()

    host = f'{bucket}.cos.{region}.myqcloud.com'
    path = '/' + urllib.parse.quote(object_key, safe='/~')
    now = int(time.time())
    sign_time = f'{now};{now + 900}'
    key_time = sign_time

    headers = {'host': host}
    if acl:
        headers['x-cos-acl'] = acl
    header_keys = sorted(headers)
    header_list = ';'.join(header_keys)
    header_string = '&'.join(
        f'{key}={urllib.parse.quote(headers[key], safe="-_.~")}' for key in header_keys
    )

    http_string = f'put\n{path}\n\n{header_string}\n'
    string_to_sign = (
        'sha1\n' + sign_time + '\n'
        + hashlib.sha1(http_string.encode('utf-8')).hexdigest() + '\n'
    )
    sign_key = hmac_sha1(secret_key.encode('utf-8'), key_time)
    signature = hmac_sha1(sign_key.encode('utf-8'), string_to_sign)
    authorization = (
        'q-sign-algorithm=sha1'
        f'&q-ak={secret_id}'
        f'&q-sign-time={sign_time}'
        f'&q-key-time={key_time}'
        f'&q-header-list={header_list}'
        '&q-url-param-list='
        f'&q-signature={signature}'
    )

    with open(local_path, 'rb') as handle:
        payload = handle.read()

    request = urllib.request.Request(f'https://{host}{path}', data=payload, method='PUT')
    request.add_header('Authorization', authorization)
    if acl:
        request.add_header('x-cos-acl', acl)

    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            print(f'COS 上传成功: {object_key} ({len(payload)} bytes, status={response.status})')
    except urllib.error.HTTPError as error:
        body = error.read().decode('utf-8', 'replace')
        raise SystemExit(f'COS 上传失败: {error.code} {body[:500]}')
    except urllib.error.URLError as error:
        raise SystemExit(f'COS 连接失败: {error.reason}')


if __name__ == '__main__':
    main()
