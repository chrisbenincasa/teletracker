package com.teletracker.common.cache

import com.teletracker.common.util.IdOrSlug
import javax.inject.Inject

class ItemCache @Inject()() extends AsyncCache[IdOrSlug] {}
