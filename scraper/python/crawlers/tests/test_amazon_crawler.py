from crawlers.spiders.amazon.amazon import _process_detail_link
from scrapy.utils.request import request_fingerprint
from scrapy import Request

def test_process_detail_link_equal():
    link1 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_11'
    link2 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_12'

    assert _process_detail_link(link1) == _process_detail_link(link2)


def test_process_detail_link_no_ref_equal():
    link1 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_11'
    link2 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK'

    assert _process_detail_link(link1) == _process_detail_link(link2)


def test_process_detail_link_preserve_query():
    link1 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_11?q=1&t=1'
    link2 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK?q=1&t=1'

    assert _process_detail_link(link1) == _process_detail_link(link2)


def test_storefront_url():
    link1 = 'https://www.amazon.com/gp/video/storefront/ref=atv_hm_hom_1_c_7bCjc9_YPnx8G_9_1?contentType=merch&contentId=deals&merchId=deals'
    link2 = 'https://www.amazon.com/gp/video/storefront/ref=atv_hm_hom_1_c_7bCjc9_UefIyC_9_2?contentType=merch&contentId=deals&merchId=deals'

    assert _process_detail_link(link1) == _process_detail_link(link2)

def test_same_request_fingerprint():
    link1 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_11'
    link2 = 'https://www.amazon.com/gp/video/detail/B07SPFX1LK/ref=atv_dp_b00_det_c_UTPsmN_1_12'

    processed1 = _process_detail_link(link1)
    processed2 = _process_detail_link(link2)

    request1 = Request(url=processed1)
    request2 = Request(url=processed2)

    assert request_fingerprint(request1) == request_fingerprint(request2)