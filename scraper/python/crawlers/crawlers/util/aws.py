import os
from typing import Optional


def get_boto3_endpoint_url() -> Optional[str]:
    return os.environ['AWS_ENDPOINT_URL'] if 'AWS_ENDPOINT_URL' in os.environ else None
