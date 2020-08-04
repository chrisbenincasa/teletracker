from crawlers.spiders.amazon.amazon import _process_detail_link


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
