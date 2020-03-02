import React, { useEffect } from 'react';
import { Slider, Theme, Typography } from '@material-ui/core';
// import { RouteComponentProps, withRouter } from 'react-router';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';
import makeStyles from '@material-ui/core/styles/makeStyles';
import _ from 'lodash';
import * as R from 'ramda';
import { OpenRange } from '../../types';
import { SlidersState } from '../../utils/searchFilters';

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

interface OwnProps {
  handleChange: (change: SliderChange) => void;
  sliders?: SlidersState;
  showTitle?: boolean;
}

type Props = OwnProps & WithRouterProps;

export interface SliderChange {
  releaseYear?: OpenRange;
}

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

const SliderFilters = (props: Props) => {
  const classes = styles();
  let nextYear = new Date().getFullYear() + 1;

  const [yearValue, setYearValue] = React.useState([
    ensureNumberInRange(
      R.or(
        props.sliders && props.sliders.releaseYear
          ? props.sliders.releaseYear.min
          : undefined,
        MIN_YEAR,
      ) as number,
      MIN_YEAR,
      nextYear,
    ),
    ensureNumberInRange(
      R.or(
        props.sliders && props.sliders.releaseYear
          ? props.sliders.releaseYear.max
          : undefined,
        nextYear,
      ) as number,
      MIN_YEAR,
      nextYear,
    ),
  ]);

  useEffect(() => {
    if (props.sliders && props.sliders.releaseYear) {
      let currMin = yearValue[0];
      let newMin: number | undefined = currMin;

      let currMax = yearValue[1];
      let newMax: number | undefined = currMax;

      if (props.sliders.releaseYear.min !== currMin) {
        newMin = props.sliders.releaseYear.min;
      }

      if (props.sliders.releaseYear.max !== currMax) {
        newMax = props.sliders.releaseYear.max;
      }

      if (newMin !== currMin || newMax !== currMax) {
        setYearValue([newMin || MIN_YEAR, newMax || nextYear]);
      }
    }
  }, [props.sliders, props.sliders ? props.sliders.releaseYear : undefined]);

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
      <div className={classes.sliderContainer} hidden={true}>
        {props.showTitle && <Typography>IMDb Score</Typography>}
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

SliderFilters.defaultProps = {
  showTitle: true,
};

export default withRouter(SliderFilters);
