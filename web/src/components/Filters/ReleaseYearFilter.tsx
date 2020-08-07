import React, { useCallback, useContext, useState } from 'react';
import { makeStyles, Slider, Theme, Typography } from '@material-ui/core';
import * as R from 'ramda';
import { SliderChange } from '../../utils/searchFilters';
import { useDebouncedCallback } from 'use-debounce';
import { FilterContext } from './FilterContext';
import FilterSectionTitle from './FilterSectionTitle';

const styles = makeStyles((theme: Theme) => ({
  sliderContainer: {
    width: '100%',
    padding: theme.spacing(0, 2),
  },
  filterLabel: {
    padding: theme.spacing(0.5),
  },
}));

const MIN_YEAR = 1900;

interface Props {
  readonly handleChange: (change: SliderChange) => void;
  readonly showTitle?: boolean;
}

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

export default function ReleaseYearFilter(props: Props) {
  const classes = styles();
  const {
    filters: { sliders },
  } = useContext(FilterContext);

  const nextYear = new Date().getFullYear() + 1;

  const [yearValue, setYearValue] = useState([
    ensureNumberInRange(
      R.or(sliders?.releaseYear?.min, MIN_YEAR) as number,
      MIN_YEAR,
      nextYear,
    ),
    ensureNumberInRange(
      R.or(sliders?.releaseYear?.max, nextYear) as number,
      MIN_YEAR,
      nextYear,
    ),
  ]);

  const [debouncePropUpdate] = useDebouncedCallback(
    (sliderChange: SliderChange) => {
      if (props.handleChange) {
        props.handleChange(sliderChange);
      }
    },
    250,
  );

  const extractValues = (
    newValue: number[],
    minValue: number,
    maxValue: number,
  ): [number?, number?] => {
    let [min, max] = newValue;
    return [
      min === minValue ? undefined : min,
      max === maxValue ? undefined : max,
    ];
  };

  const handleYearChange = useCallback((event, newValue) => {
    setYearValue(newValue);
  }, []);

  const handleYearCommitted = useCallback((event, newValue) => {
    let [min, max] = extractValues(newValue, MIN_YEAR, nextYear);
    debouncePropUpdate({
      releaseYear: {
        min,
        max,
      },
    });
  }, []);

  return (
    <div className={classes.sliderContainer}>
      {props.showTitle && <FilterSectionTitle title="Release Year" />}
      <Slider
        value={yearValue}
        min={MIN_YEAR}
        max={nextYear}
        marks={[
          { value: MIN_YEAR, label: MIN_YEAR },
          { value: nextYear, label: nextYear },
        ]}
        onChange={handleYearChange}
        onChangeCommitted={handleYearCommitted}
        valueLabelDisplay="auto"
        aria-labelledby="range-slider"
      />
    </div>
  );
}

ReleaseYearFilter.defaultProps = {
  showTitle: true,
};
