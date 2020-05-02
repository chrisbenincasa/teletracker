import React, { useContext, useEffect } from 'react';
import { Slider, Theme, Typography } from '@material-ui/core';
import makeStyles from '@material-ui/core/styles/makeStyles';
import * as R from 'ramda';
import { SlidersState } from '../../utils/searchFilters';
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
const MIN_RATING = 0;
const MAX_RATING = 10;
const RATING_STEP = 0.5;

interface Props {
  handleChange: (change: SliderChange) => void;
  showTitle?: boolean;
}

export type SliderChange = Partial<SlidersState>;

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

export default function SliderFilters(props: Props) {
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

  const [imdbRatingValue, setImdbRatingValue] = React.useState([
    ensureNumberInRange(
      sliders?.imdbRating?.min || MIN_RATING,
      MIN_RATING,
      MAX_RATING,
    ),
    ensureNumberInRange(
      sliders?.imdbRating?.max || MAX_RATING,
      MIN_RATING,
      MAX_RATING,
    ),
  ]);

  useEffect(() => {
    if (sliders && sliders.imdbRating) {
      let currMin = _.head(imdbRatingValue);
      let newMin = currMin;

      let currMax = _.nth(imdbRatingValue, 1);
      let newMax = currMax;

      if (sliders.imdbRating.min !== currMin) {
        newMin = sliders.imdbRating.min;
      }

      if (sliders.imdbRating.max !== currMax) {
        newMax = sliders.imdbRating.max;
      }

      if (newMin !== currMin || newMax !== currMax) {
        setImdbRatingValue([newMin || MIN_RATING, newMax || MAX_RATING]);
      }
    }
  }, [sliders, imdbRatingValue]);

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

  const handleImdbChange = (event, newValue) => {
    setImdbRatingValue(newValue);
  };

  const handleImdbCommitted = (event, newValue) => {
    let [min, max] = extractValues(newValue, MIN_RATING, MAX_RATING);
    debouncePropUpdate({
      imdbRating: {
        min,
        max,
      },
    });
  };

  return (
    <React.Fragment>
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
      <div className={classes.sliderContainer}>
        {props.showTitle && <Typography>IMDb Rating</Typography>}
        <Slider
          value={imdbRatingValue}
          min={MIN_RATING}
          max={MAX_RATING}
          step={RATING_STEP}
          marks={[
            { value: MIN_RATING, label: MIN_RATING },
            { value: MAX_RATING, label: MAX_RATING },
          ]}
          onChange={handleImdbChange}
          onChangeCommitted={handleImdbCommitted}
          valueLabelDisplay="auto"
          aria-labelledby="range-slider"
        />
      </div>
    </React.Fragment>
  );
}

SliderFilters.defaultProps = {
  showTitle: true,
};
