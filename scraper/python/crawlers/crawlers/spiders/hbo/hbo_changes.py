import logging
import scrapy
import re

from crawlers.base_spider import BaseSpider
from calendar import monthrange, month_name
from datetime import datetime

year_regex = r'\(([0-9)]+)\)'
parens_regex = r'\(([^)]+)\)'


def _month_number(name):
    for (idx, month) in enumerate(month_name):
        if name.lower() == month.lower():
            return idx


def _month_name_match(name):
    for (idx, month) in enumerate(month_name):
        if name.lower() == month.lower():
            return month


class HboChangesSpider(BaseSpider):
    name = 'hbo_changes'
    allowed_domains = ['hbo.com']
    start_urls = [
        'https://www.hbo.com/whats-new-whats-leaving'
    ]

    def parse(self, response):
        for section in response.css('.components\\/Band--band[data-bi-context=\'{"band":"Text"}\'] > div > div'):
            title = section.xpath('.//h4//text()').get()
            if title:
                title = title.strip().lower()
                if title == 'premieres and finales':
                    self.log('handle premieres and finales')
                    # TODO: Handle new show premieres here
                elif title == 'theatrical premieres':
                    self.log('handle theatrical premieres')
                    for item in self.parse_theatrical_releases(section):
                        yield item
                elif 'starting' in title or 'ending' in title:
                    self.log('handle starting ending {}'.format(title))
                    for item in self.parse_new(section, title):
                        yield item
                else:
                    self.log('unrecognized title: {}'.format(title), logging.WARN)

    def parse_theatrical_releases(self, section):
        today = datetime.now()
        for line in section.xpath('./p/b'):
            date = line.xpath('./text()').get().strip().rstrip(':')
            title = line.xpath('./following-sibling::*[1]/text()').get().strip()
            full_date = None
            try:
                full_date = datetime.strptime(date, '%B %d at %I %p').replace(year=today.year)
            except ValueError:
                pass

            if not full_date:
                month_string = _month_name_match(date.split(' ')[0])
                date_num = re.search(r'{} (\d+)'.format(month_string), date, re.IGNORECASE)
                if date_num:
                    full_date = datetime.strptime(
                        '{}-{}-{}'.format(today.year, _month_number(month_string), date_num.group(1)),
                        '%Y-%m-%d')
                    self.log('{}, {}'.format(title, full_date))

            if full_date:
                yield HboChangeItem(
                    availableDate=full_date.isoformat(),
                    title=title,
                    releaseYear=None,
                    itemType='movie',
                    status='arriving'
                )

    def parse_new(self, section, section_title):
        today = datetime.today()
        status = 'arriving' if 'starting' in section_title else 'expiring'
        title_tokens = [x for x in section_title.split(' ') if len(x) > 0]
        [month, day] = title_tokens[max(len(title_tokens) - 2, 1):]
        day_number = int(day)
        (_, num_days_in_month) = monthrange(today.year, _month_number(month))

        if day_number > num_days_in_month:
            day_number = num_days_in_month

        full_date = datetime.strptime('{}-{}-{}'.format(today.year, _month_number(month), day_number),
                                      '%Y-%m-%d')

        self.log('Handle {} on {}'.format(status, full_date))

        titles_and_years = [x.strip() for x in ''.join(section.xpath('.//p//text()').getall()).split('\n')]
        for title_and_year in titles_and_years:
            if len(title_and_year) > 0:
                year_match = re.search(year_regex, title_and_year)
                if year_match:
                    release_year = year_match.group(1)
                    parsed_title = re.sub(parens_regex, '',
                                          title_and_year.replace(year_match.group(0), '')).strip()
                    self.log('{}'.format(full_date))
                    yield HboChangeItem(
                        availableDate=full_date.isoformat(),
                        title=parsed_title,
                        releaseYear=int(release_year),
                        itemType='movie',
                        status=status
                    )


class HboChangeItem(scrapy.Item):
    type = 'HboChangeItem'
    availableDate = scrapy.Field()
    title = scrapy.Field()
    releaseYear = scrapy.Field()
    status = scrapy.Field()
    itemType = scrapy.Field()
    network = 'hbo'
