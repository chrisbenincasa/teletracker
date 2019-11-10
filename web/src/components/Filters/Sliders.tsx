import { Slider, Theme, Typography } from '@material-ui/core';
import React, { useEffect } from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import makeStyles from '@material-ui/core/styles/makeStyles';
import {
  parseFilterParamsFromQs,
  updateMultipleUrlParams,
} from '../../utils/urlHelper';
import _ from 'lodash';
import * as R from 'ramda';
import { NetworkType, OpenRange } from '../../types';

const styles = makeStyles((theme: Theme) => ({
  sliderContainer: {
    width: '100%',
    padding: theme.spacing(3),
  },
}));

const MIN_YEAR = 1900;
const MIN_RATING = 0;
const MAX_RATING = 10;
const RATING_STEP = 0.5;

interface OwnProps {
  handleChange: (change: SliderChange) => void;
}

type Props = OwnProps & RouteComponentProps<{}>;

export interface SliderChange {
  releaseYear?: OpenRange;
}

const debounceUrlUpdate = _.debounce(
  (props: Props, kvPairs: [string, any | undefined][]) => {
    updateMultipleUrlParams(props, kvPairs);
  },
  250,
);

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

const SliderFilters = (props: Props) => {
  const classes = styles();
  let nextYear = new Date().getFullYear() + 1;

  let filterParams = parseFilterParamsFromQs(props.history.location.search);

  const [yearValue, setYearValue] = React.useState([
    ensureNumberInRange(
      R.or(
        R.path(['sliders', 'releaseYear', 'min'], filterParams),
        MIN_YEAR,
      ) as number,
      MIN_YEAR,
      nextYear,
    ),
    ensureNumberInRange(
      R.or(
        R.path(['sliders', 'releaseYear', 'max'], filterParams),
        nextYear,
      ) as number,
      MIN_YEAR,
      nextYear,
    ),
  ]);

  useEffect(() => {
    let sliderState = parseFilterParamsFromQs(props.location.search).sliders;

    let newMin;
    if (
      sliderState &&
      sliderState.releaseYear &&
      sliderState.releaseYear.min !== yearValue[0]
    ) {
      newMin = sliderState.releaseYear.min || MIN_YEAR;
    }

    let newMax;
    if (
      sliderState &&
      sliderState.releaseYear &&
      sliderState.releaseYear.max !== yearValue[1]
    ) {
      newMax = sliderState.releaseYear.max || nextYear;
    }

    if (newMin || newMax) {
      setYearValue(([currMin, currMax]) => [
        newMin || currMin,
        newMax || currMax,
      ]);
    }
  }, [props.location.search]);

  const [imdbRatingValue, setImdbRatingValue] = React.useState([
    MIN_RATING,
    MAX_RATING,
  ]);

  const debouncePropUpdate = _.debounce((sliderChange: SliderChange) => {
    if (props.handleChange) {
      props.handleChange(sliderChange);
    }
  }, 250);

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
    let [min, max] = extractValues(newValue, MIN_YEAR, nextYear);
    debounceUrlUpdate(props, [['ry_min', min], ['ry_max', max]]);
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
    let [min, max] = extractValues(newValue, MIN_RATING, MAX_RATING);
    debounceUrlUpdate(props, [['imdb_min', min], ['imdb_max', max]]);
  };

  return (
    <React.Fragment>
      <div className={classes.sliderContainer}>
        <Typography>Release Year</Typography>
        <Slider
          value={yearValue}
          min={MIN_YEAR}
          max={nextYear}
          onChange={handleYearChange}
          onChangeCommitted={handleYearCommitted}
          valueLabelDisplay="auto"
          aria-labelledby="range-slider"
        />
      </div>
      <div className={classes.sliderContainer} hidden={true}>
        <Typography>IMDb Score</Typography>
        <Slider
          value={imdbRatingValue}
          min={MIN_RATING}
          max={MAX_RATING}
          step={RATING_STEP}
          onChange={handleImdbChange}
          valueLabelDisplay="auto"
          aria-labelledby="range-slider"
        />
      </div>
    </React.Fragment>
  );
};

export default withRouter(SliderFilters);
