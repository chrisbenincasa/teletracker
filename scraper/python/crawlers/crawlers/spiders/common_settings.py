DATA_BUCKET = 'DATA_BUCKET'

DEFAULT_DATA_BUCKET = 'teletracker-data-us-west-2'


def get_data_bucket(settings):
    return settings.get(DATA_BUCKET).value if DATA_BUCKET in settings else DEFAULT_DATA_BUCKET
