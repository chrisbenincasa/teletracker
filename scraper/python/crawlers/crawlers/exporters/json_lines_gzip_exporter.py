# coding: utf8

import gzip

from scrapy.exporters import JsonLinesItemExporter


class JsonLinesGzipItemExporter(JsonLinesItemExporter):
    """
    Sample exporter for .jl + .gz format.
    To use it, add
    ::

        FEED_EXPORTERS = {
            'jl.gz': 'myproject.exporters.JsonLinesGzipItemExporter',
        }
        FEED_FORMAT = 'jl.gz'

    to settings.py and then run scrapy crawl like this::

        scrapy crawl foo -o s3://path/to/items.jl.gz

    (if `FEED_FORMAT` is not explicitly specified, you'll need to add
    `-t jl.gz` to the command above)
    """

    def __init__(self, file, **kwargs):
        gzfile = gzip.GzipFile(fileobj=file)
        super(JsonLinesGzipItemExporter, self).__init__(gzfile, **kwargs)

    def finish_exporting(self):
        self.file.close()