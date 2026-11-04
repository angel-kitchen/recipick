import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location('collector', Path(__file__).parents[1] / 'collect-metrics.py')
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class MetricCollectorTest(unittest.TestCase):
    def test_only_expected_metric_families_are_saved(self):
        body = '# HELP text\nprocess_cpu_usage 0.1\nhikaricp_connections_pending 2\n'
        body += 'tomcat_threads_busy_threads 3\ncustom_customer_email{email="hidden"} 1\n'
        result = collector.select_metrics(body)
        self.assertIn('process_cpu_usage', result)
        self.assertIn('tomcat_threads_busy_threads', result)
        self.assertNotIn('hidden', result)
        self.assertNotIn('# HELP', result)

    def test_credentials_and_query_are_rejected(self):
        for url in ('http://user:secret@localhost/metrics', 'http://localhost/metrics?token=secret',
                    'file:///tmp/metrics', 'http://localhost/metrics#secret'):
            with self.subTest(url=url), self.assertRaises(argparse.ArgumentTypeError):
                collector.validate_url(url)
        self.assertEqual(collector.validate_url('http://localhost:28081/actuator/prometheus'),
                         'http://localhost:28081/actuator/prometheus')

    def test_failed_scrape_is_recorded_without_response_body(self):
        stop = threading.Event()
        def fail(*args, **kwargs):
            stop.set()
            raise HTTPError('http://localhost/metrics', 503, 'secret-body', {}, None)
        with tempfile.TemporaryDirectory() as directory, patch.object(collector, 'urlopen', side_effect=fail):
            collector.collect('http://localhost/metrics', Path(directory), 5, stop)
            raw = (Path(directory) / 'samples.jsonl').read_text()
            sample = json.loads(raw)
            self.assertFalse(sample['ok'])
            self.assertEqual(sample['status'], 503)
            self.assertNotIn('secret-body', raw)
            self.assertIn('endedAt', sample)

    def test_successful_scrape_saves_timestamp_and_filtered_data(self):
        stop = threading.Event()
        class Response:
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self):
                stop.set()
                return b'process_cpu_usage 0.5\nprivate_metric 9\n'
        with tempfile.TemporaryDirectory() as directory, patch.object(collector, 'urlopen', return_value=Response()):
            collector.collect('http://localhost/metrics', Path(directory), 5, stop)
            sample = json.loads((Path(directory) / 'samples.jsonl').read_text())
            self.assertTrue(sample['ok'])
            self.assertIn('startedAt', sample)
            self.assertNotIn('private_metric', (Path(directory) / sample['file']).read_text())


if __name__ == '__main__':
    unittest.main()
