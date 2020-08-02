def uri_params(params, spider):
    import datetime
    import time

    params['today'] = datetime.date.today().isoformat()
    params['canonical_name'] = spider.store_name if hasattr(spider, 'store_name') else spider.name
    params['spider_start'] = spider.start_time if hasattr(spider, 'start_time') else int(time.time())
