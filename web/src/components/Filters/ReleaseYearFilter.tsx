import React, { useContext, useEffect } from 'react';
import { makeStyles, Slider, Theme, Typography } from '@material-ui/core';
import * as R from 'ramda';
import { SliderChange, SlidersState } from '../../utils/searchFilters';
import { useDebouncedCallback } from 'use-debounce';
import { FilterContext } from './FilterContext';
import _ from 'lodash';

const styles = makeStyles((theme: Theme) => ({
  sliderContainer: {
    width: '100%',
    padding: theme.spacing(0, 2),
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

  const [yearValue, setYearValue] = React.useState([
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

  useEffect(() => {
    if (sliders && sliders.releaseYear) {
      let currMin = _.head(yearValue);
      let newMin = currMin;

      let currMax = _.nth(yearValue, 1);
      let newMax = currMax;

      if (sliders.releaseYear.min !== currMin) {
        newMin = sliders.releaseYear.min;
      }

      if (sliders.releaseYear.max !== currMax) {
        newMax = sliders.releaseYear.max;
      }

      if (newMin !== currMin || newMax !== currMax) {
        setYearValue([newMin || MIN_YEAR, newMax || nextYear]);
      }
    }
  }, [sliders, yearValue]);

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

  const handleYearChange = (event, newValue) => {
    setYearValue(newValue);
  };

  const handleYearCommitted = (event, newValue) => {
    let [min, max] = extractValues(newValue, MIN_YEAR, nextYear);
    debouncePropUpdate({
      releaseYear: {
        min,
        max,
      },
    });
  };

  return (
    <div className={classes.sliderContainer}>
      {props.showTitle && <Typography>Release Year</Typography>}
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
