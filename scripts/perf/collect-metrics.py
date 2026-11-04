#!/usr/bin/env python3
"""성능 테스트 관리 포트에서 지표 시계열을 수집한다. 표준 라이브러리만 사용한다."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import signal
import threading
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import urlopen

PREFIXES = ('process_cpu_', 'system_cpu_', 'jvm_gc_', 'jvm_memory_',
            'jvm_threads_', 'hikaricp_connections_', 'tomcat_threads_',
            'tomcat_connections_', 'tomcat_global_', 'http_server_requests_seconds_')


def select_metrics(body):
    return '\n'.join(line for line in body.splitlines()
                     if not line.startswith('#') and line.startswith(PREFIXES)) + '\n'


def validate_url(value):
    parsed = urlsplit(value)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise argparse.ArgumentTypeError('인증 정보·쿼리 없는 HTTP(S) 관리 포트 URL을 지정하세요.')
    return value


def collect(url, directory, interval, stop):
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / 'samples.jsonl').open('a', encoding='utf-8') as index:
        while not stop.is_set():
            started = time.monotonic()
            sample = {'startedAt': datetime.now(timezone.utc).isoformat()}
            try:
                with urlopen(url, timeout=3) as response:
                    body = response.read().decode('utf-8')
                filename = f"metrics-{time.time_ns()}.prom"
                (directory / filename).write_text(select_metrics(body), encoding='utf-8')
                sample.update(ok=True, file=filename)
            except (OSError, UnicodeError, ValueError) as error:
                # URL과 응답 본문은 기록하지 않는다. 실패도 시간대별로 보존한다.
                sample.update(ok=False, errorType=type(error).__name__)
                if isinstance(error, HTTPError):
                    sample['status'] = error.code
            sample['endedAt'] = datetime.now(timezone.utc).isoformat()
            sample['elapsedMs'] = round((time.monotonic() - started) * 1000, 2)
            index.write(json.dumps(sample, ensure_ascii=False) + '\n')
            index.flush()
            stop.wait(max(0, interval - (time.monotonic() - started)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', type=validate_url, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--interval', type=float, default=5)
    args = parser.parse_args()
    if args.interval < 1:
        parser.error('수집 간격은 1초 이상이어야 합니다.')
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('기존 결과를 섞지 않도록 비어 있는 출력 폴더를 지정하세요.')
    stop = threading.Event()
    for name in (signal.SIGINT, signal.SIGTERM):
        signal.signal(name, lambda *_: stop.set())
    print('지표 수집 시작. SIGINT/SIGTERM으로 종료합니다.', flush=True)
    collect(args.url, args.output, args.interval, stop)


if __name__ == '__main__':
    main()
