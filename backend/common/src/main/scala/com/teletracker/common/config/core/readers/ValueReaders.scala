package com.teletracker.common.config.core.readers

import net.ceedubs.ficus.readers.{AllValueReaderInstances, EnumerationReader}

object ValueReaders extends ValueReaders

trait ValueReaders
    extends AllValueReaderInstances
    with EnumerationReader
    with StandardTypeReaders
    with JavaEnumReaders
