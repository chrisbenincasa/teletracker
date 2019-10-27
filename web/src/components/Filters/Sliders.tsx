import { Slider, Theme, Typography } from '@material-ui/core';
import React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import makeStyles from '@material-ui/core/styles/makeStyles';
import {
  parseFilterParamsFromQs,
  updateMultipleUrlParams,
} from '../../utils/urlHelper';
import _ from 'lodash';
import * as R from 'ramda';

const styles = makeStyles((theme: Theme) => ({
  sliderContainer: {
    width: '100%',
    padding: theme.spacing(3),
  },
}));

const MIN_YEAR = 1900;
const MIN_RATING = 0;
const MAX_RATING = 0;
const RATING_STEP = 0.5;

interface OwnProps {}

type Props = OwnProps & RouteComponentProps<{}>;

const debounceUrlUpdate = _.debounce(
  (props: Props, kvPairs: [string, any | undefined][]) => {
    console.log('bounded');
    updateMultipleUrlParams(props, kvPairs);
  },
  250,
);

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

const SliderFilters = (props: Props) => {
  const classes = styles();
  let currentYear = new Date().getFullYear();

  let filterParams = parseFilterParamsFromQs(props.history.location.search);

  const [yearValue, setYearValue] = React.useState([
    ensureNumberInRange(
      R.or(
        R.path(['sliders', 'releaseYear', 'min'], filterParams),
        MIN_YEAR,
      ) as number,
      MIN_YEAR,
      currentYear,
    ),
    ensureNumberInRange(
      R.or(
        R.path(['sliders', 'releaseYear', 'max'], filterParams),
        currentYear,
      ) as number,
      MIN_YEAR,
      currentYear,
    ),
  ]);

  const [imdbRatingValue, setImdbRatingValue] = React.useState([
    MIN_RATING,
    MAX_RATING,
  ]);

  const handleYearChange = (event, newValue) => {
    setYearValue(newValue);
    let [min, max] = newValue;
    debounceUrlUpdate(props, [['ry_min', min], ['ry_max', max]]);
  };

  const handleImdbChange = (event, newValue) => {
    setImdbRatingValue(newValue);
    let [min, max] = newValue;
    debounceUrlUpdate(props, [['imdb_min', min], ['imdb_max', max]]);
  };

  return (
    <React.Fragment>
      <div className={classes.sliderContainer}>
        <Typography>Release Year:</Typography>
        <Slider
          value={yearValue}
          min={MIN_YEAR}
          max={currentYear}
          onChange={handleYearChange}
          valueLabelDisplay="auto"
          aria-labelledby="range-slider"
        />
      </div>
      <div className={classes.sliderContainer}>
        <Typography>IMDB Score:</Typography>
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
